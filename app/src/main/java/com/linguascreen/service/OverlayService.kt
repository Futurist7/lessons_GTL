package com.linguascreen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.linguascreen.MainActivity
import com.linguascreen.R
import com.linguascreen.capture.CaptureConsentActivity
import com.linguascreen.capture.ScreenCapturer
import com.linguascreen.data.prefs.SettingsRepository
import com.linguascreen.domain.LinguaException
import com.linguascreen.domain.ScreenTranslation
import com.linguascreen.domain.TranslationPipeline
import com.linguascreen.overlay.FloatingButton
import com.linguascreen.overlay.TranslationOverlay
import com.linguascreen.util.ForegroundAppDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that owns the floating button, the screen capturer and the translation
 * overlay. Nothing is captured until the user taps "Translate Screen".
 */
@AndroidEntryPoint
class OverlayService : Service() {

    @Inject lateinit var pipeline: TranslationPipeline
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var state: OverlayState
    @Inject lateinit var appDetector: ForegroundAppDetector

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: android.view.WindowManager
    private lateinit var capturer: ScreenCapturer
    private var button: FloatingButton? = null
    private var overlay: TranslationOverlay? = null
    private var job: Job? = null
    private var paused = false
    private var pendingTranslate = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(android.view.WindowManager::class.java)
        capturer = ScreenCapturer(this).apply {
            // Fired when the user or system ends the capture session (e.g. status-bar "Stop").
            onStopped = { scope.launch { goForeground(withProjection = false) } }
        }
        overlay = TranslationOverlay(this, wm)
        createChannel()
        state.set(true)
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground(withProjection = capturer.isActive)
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_PROJECTION_RESULT -> handleProjection(
                intent.getIntExtra(EXTRA_RESULT_CODE, 0),
                intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA),
            )
            ACTION_PROJECTION_DENIED -> {
                pendingTranslate = false
                toast(LinguaException.CaptureDenied().message)
            }
            else -> {
                if (!ensureButton()) return START_NOT_STICKY
                if (intent?.getBooleanExtra(EXTRA_REQUEST_CONSENT, false) == true && !capturer.isActive) requestConsent()
            }
        }
        return START_NOT_STICKY
    }

    // ---- floating button -------------------------------------------------------------------

    /** Adds the floating button; returns false (and stops the service) if overlay permission is gone. */
    private fun ensureButton(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            overlayRevoked()
            return false
        }
        if (button != null) return true
        val b = FloatingButton(this, wm, object : FloatingButton.Callbacks {
            override fun onTranslate() = translateScreen()
            override fun onTogglePause() = togglePause()
            override fun onOpenSettings() = openApp("settings")
            override fun onClose() { stopSelf() }
            override fun onMoved(x: Int, y: Int) { scope.launch { settings.setButtonPosition(x, y) } }
        })
        button = b
        b.setPaused(paused)
        scope.launch {
            val s = settings.current()
            try {
                b.show(s.buttonX, s.buttonY)
            } catch (e: Exception) { // BadTokenException / SecurityException when permission vanished
                overlayRevoked()
            }
        }
        return true
    }

    private fun overlayRevoked() {
        toast(LinguaException.OverlayPermissionMissing().message)
        stopSelf()
    }

    private fun togglePause() {
        paused = !paused
        button?.setPaused(paused)
        if (paused) {
            // Privacy: drop the capture session while paused; consent is asked again on resume.
            job?.cancel()
            overlay?.dismiss()
            capturer.release()
            goForeground(withProjection = false)
            toast("Translation paused")
        } else {
            toast("Translation resumed")
        }
    }

    private fun openApp(route: String?) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_ROUTE, route),
        )
    }

    // ---- capture + translate ---------------------------------------------------------------

    private fun requestConsent() {
        startActivity(Intent(this, CaptureConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun handleProjection(resultCode: Int, data: Intent?) {
        if (data == null) { toast(LinguaException.CaptureDenied().message); return }
        try {
            // Android 14+: the mediaProjection service type must be active BEFORE getMediaProjection().
            goForeground(withProjection = true)
            val mgr = getSystemService(MediaProjectionManager::class.java)
            val projection = mgr.getMediaProjection(resultCode, data)
            if (projection == null) throw LinguaException.CaptureDenied()
            capturer.start(projection)
        } catch (e: Exception) {
            goForeground(withProjection = false)
            toast(LinguaException.CaptureDenied().message)
            pendingTranslate = false
            return
        }
        if (pendingTranslate) {
            pendingTranslate = false
            translateScreen()
        }
    }

    private fun translateScreen() {
        if (paused) { toast("Translation is paused. Choose Resume Translation first."); return }
        if (!Settings.canDrawOverlays(this)) { overlayRevoked(); return }
        if (job?.isActive == true) return
        if (!capturer.isActive) {
            pendingTranslate = true
            requestConsent()
            return
        }
        job = scope.launch { runTranslation() }
    }

    private suspend fun runTranslation() {
        var bitmap: Bitmap? = null
        try {
            overlay?.dismiss()
            button?.setVisible(false) // keep our own UI out of the screenshot
            delay(HIDE_DELAY_MS)
            val sourceApp = appDetector.currentApp()
            bitmap = capturer.capture()
            button?.setVisible(true)
            button?.setBusy(true)
            val result = pipeline.run(bitmap, sourceApp)
            bitmap.recycle(); bitmap = null // release pixels before drawing
            showOverlay(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LinguaException) {
            handleError(e)
        } catch (e: Exception) {
            toast("Something went wrong. Please try again.")
        } finally {
            bitmap?.recycle()
            button?.setVisible(true)
            button?.setBusy(false)
        }
    }

    private suspend fun showOverlay(result: ScreenTranslation) {
        val opacity = settings.current().overlayOpacity
        try {
            overlay?.show(result, opacity) { /* dismissed by the user */ }
        } catch (e: Exception) {
            overlayRevoked()
        }
    }

    private fun handleError(e: LinguaException) {
        toast(e.message)
        when (e) {
            is LinguaException.CaptureDenied -> { capturer.release(); goForeground(withProjection = false) }
            is LinguaException.ModelNotDownloaded -> openApp(null)
            else -> Unit
        }
    }

    // ---- lifecycle -------------------------------------------------------------------------

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation / fold: the translated layout no longer matches the screen, so drop it.
        overlay?.dismiss()
        button?.reclamp()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) pipeline.trimMemory()
    }

    override fun onDestroy() {
        scope.cancel()
        overlay?.dismiss()
        button?.destroy()
        capturer.destroy()
        state.set(false)
        super.onDestroy()
    }

    // ---- notification ----------------------------------------------------------------------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, getString(R.string.notif_action_stop), stop)
            .build()
    }

    private fun goForeground(withProjection: Boolean) {
        val n = buildNotification()
        try {
            when {
                Build.VERSION.SDK_INT >= 34 -> startForeground(
                    NOTIF_ID, n,
                    if (withProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
                Build.VERSION.SDK_INT >= 29 -> startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                else -> startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            if (withProjection) throw e
        }
    }

    private fun toast(message: String?) {
        if (message != null) Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val CHANNEL_ID = "floating_translator"
        private const val NOTIF_ID = 1001
        private const val HIDE_DELAY_MS = 220L

        private const val ACTION_STOP = "com.linguascreen.STOP"
        private const val ACTION_PROJECTION_RESULT = "com.linguascreen.PROJECTION_RESULT"
        private const val ACTION_PROJECTION_DENIED = "com.linguascreen.PROJECTION_DENIED"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val EXTRA_REQUEST_CONSENT = "request_consent"

        fun start(context: Context, requestConsent: Boolean) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverlayService::class.java).putExtra(EXTRA_REQUEST_CONSENT, requestConsent),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }

        fun deliverProjection(context: Context, resultCode: Int, data: Intent) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverlayService::class.java)
                    .setAction(ACTION_PROJECTION_RESULT)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_RESULT_DATA, data),
            )
        }

        fun deliverProjectionDenied(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverlayService::class.java).setAction(ACTION_PROJECTION_DENIED),
            )
        }
    }
}
