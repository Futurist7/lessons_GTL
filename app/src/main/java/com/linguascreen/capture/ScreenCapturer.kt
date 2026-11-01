package com.linguascreen.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import com.linguascreen.domain.LinguaException
import com.linguascreen.util.ScreenMetrics
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Captures single screenshots through MediaProjection, only when [capture] is called.
 *
 * Android 14+ allows a MediaProjection token to create just one VirtualDisplay, so we keep one
 * display for the lifetime of the projection but detach its output surface while idle. While
 * detached nothing is rendered or copied, so there is no continuous capture and no battery cost.
 */
class ScreenCapturer(private val context: Context) {
    private val thread = HandlerThread("lingua-capture").apply { start() }
    private val handler = Handler(thread.looper)

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var dpi = 0

    /** Called (on the capture thread) when the system or user stops the projection. */
    var onStopped: (() -> Unit)? = null

    val isActive: Boolean get() = projection != null

    fun start(mediaProjection: MediaProjection) {
        release()
        projection = mediaProjection
        // Android 14+ requires a callback to be registered before creating the VirtualDisplay.
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                releaseResources()
                projection = null
                onStopped?.invoke()
            }
        }, handler)
    }

    suspend fun capture(): Bitmap {
        val proj = projection ?: throw LinguaException.CaptureDenied()
        try {
            prepare(proj)
            return withTimeout(CAPTURE_TIMEOUT_MS) { grabFrame() }
        } catch (e: TimeoutCancellationException) {
            throw LinguaException.CaptureFailed(e)
        } catch (e: LinguaException) {
            throw e
        } catch (e: SecurityException) {
            release()
            throw LinguaException.CaptureDenied()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LinguaException.CaptureFailed(e)
        }
    }

    /** Creates the display/reader on first use, and resizes them after rotation or a fold. */
    private fun prepare(proj: MediaProjection) {
        val m = ScreenMetrics.real(context)
        if (display != null && reader != null && m.width == width && m.height == height) return
        reader?.close()
        val newReader = ImageReader.newInstance(m.width, m.height, PixelFormat.RGBA_8888, 2)
        reader = newReader
        val existing = display
        if (existing == null) {
            display = proj.createVirtualDisplay(
                "LinguaScreen", m.width, m.height, m.dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, handler,
            )
        } else {
            existing.resize(m.width, m.height, m.dpi)
        }
        width = m.width; height = m.height; dpi = m.dpi
    }

    private suspend fun grabFrame(): Bitmap = suspendCancellableCoroutine { cont ->
        val r = reader!!
        val d = display!!
        // Drop any stale frame left from a previous capture.
        runCatching { r.acquireLatestImage()?.close() }
        r.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            source.setOnImageAvailableListener(null, null)
            d.surface = null // stop rendering immediately
            try {
                val plane = image.planes[0]
                val padded = plane.rowStride / plane.pixelStride
                val full = Bitmap.createBitmap(padded, image.height, Bitmap.Config.ARGB_8888)
                full.copyPixelsFromBuffer(plane.buffer)
                val bmp = if (padded != image.width) {
                    Bitmap.createBitmap(full, 0, 0, image.width, image.height).also { full.recycle() }
                } else full
                if (cont.isActive) cont.resume(bmp) else bmp.recycle()
            } catch (e: Throwable) {
                if (cont.isActive) cont.resumeWithException(e)
            } finally {
                image.close()
            }
        }, handler)
        cont.invokeOnCancellation {
            r.setOnImageAvailableListener(null, null)
            runCatching { d.surface = null }
        }
        d.surface = r.surface
    }

    private fun releaseResources() {
        runCatching { display?.release() }
        display = null
        runCatching { reader?.close() }
        reader = null
        width = 0; height = 0
    }

    /** Stops the projection and frees every buffer. A new consent is needed to capture again. */
    fun release() {
        val p = projection
        projection = null
        releaseResources()
        runCatching { p?.stop() }
    }

    fun destroy() {
        release()
        thread.quitSafely()
    }

    private companion object {
        const val CAPTURE_TIMEOUT_MS = 3000L
    }
}
