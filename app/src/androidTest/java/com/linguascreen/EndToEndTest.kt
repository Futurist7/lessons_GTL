package com.linguascreen

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.linguascreen.data.db.AppDatabase
import com.linguascreen.data.translate.MlKitTranslationProvider
import com.linguascreen.service.OverlayService
import com.linguascreen.util.TextUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/**
 * Drives the real app: floating button -> consent dialog -> Translate Screen -> overlay appears
 * with English over a screen showing Chinese text -> history row saved.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {
    private fun log(m: String) { Log.i("LinguaTest", m) }

    @Test
    fun translateScreenEndToEnd() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val ctx: Context = inst.targetContext
        val pkg = ctx.packageName
        fun shell(c: String) = device.executeShellCommand(c).also { log("shell $c -> $it") }

        shell("appops set $pkg SYSTEM_ALERT_WINDOW allow")
        shell("pm grant $pkg android.permission.POST_NOTIFICATIONS")

        runBlocking {
            val p = MlKitTranslationProvider(ctx)
            if (!p.isReady("en")) p.downloadModel("en", true)
            assertTrue(p.isReady("en"))
        }

        device.pressHome()
        inst.context.startActivity(
            Intent(inst.context, TestChineseActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Thread.sleep(2000)

        OverlayService.start(ctx, requestConsent = true)
        val ok = device.wait(Until.findObject(By.res("android:id/button1")), 30_000)
        log("consent dialog button: ${ok?.text}")
        assertNotNull("system screen-capture dialog should appear", ok)
        ok.click()
        Thread.sleep(2500)

        val button = device.wait(Until.findObject(By.desc("LinguaScreen translate button")), 15_000)
        assertNotNull("floating button should be visible over the other app", button)
        button.click()

        val translate = device.wait(Until.findObject(By.text("Translate Screen")), 8_000)
        assertNotNull("control panel should open", translate)
        translate.click()

        // The overlay exposes the translations as its content description.
        val overlay = device.wait(
            Until.findObject(By.desc(Pattern.compile("^(?!LinguaScreen).*[A-Za-z]{3,}.*", Pattern.DOTALL)).pkg(pkg)),
            60_000,
        )
        device.takeScreenshot(File("/sdcard/Download/lingua_e2e.png"))
        assertNotNull("translation overlay should appear", overlay)
        val shown = overlay.contentDescription.toString()
        log("OVERLAY SHOWS: $shown")
        assertFalse("overlay must be English, not Chinese", TextUtils.containsChinese(shown))
        assertTrue("price must be kept", shown.contains("1288"))

        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, "linguascreen.db").build()
        val rows = runBlocking { db.historyDao().observeAll().first() }
        log("HISTORY ROWS: " + rows.joinToString { "'${it.original}' -> '${it.translation}'" })
        assertTrue("history should have entries", rows.isNotEmpty())
        db.close()

        // Dismiss by tapping, then stop the service.
        device.click(device.displayWidth / 2, device.displayHeight / 4)
        OverlayService.stop(ctx)
    }
}
