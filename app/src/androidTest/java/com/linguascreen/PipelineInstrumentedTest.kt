package com.linguascreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linguascreen.data.ocr.MlKitOcrEngine
import com.linguascreen.data.translate.MlKitTranslationProvider
import com.linguascreen.data.translate.TranslationManager
import com.linguascreen.util.TextUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real model download + real OCR + real translation on a rendered Chinese image. */
@RunWith(AndroidJUnit4::class)
class PipelineInstrumentedTest {
    private fun log(m: String) { Log.i("LinguaTest", m) }

    @Test
    fun downloadOcrTranslate() = runBlocking {
        withTimeout(12 * 60_000L) {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            val provider = MlKitTranslationProvider(ctx)

            val t0 = SystemClock.elapsedRealtime()
            if (!provider.isReady("en")) {
                provider.downloadModel("en", true) { d, t, n -> log("download progress $d/$t $n at ${SystemClock.elapsedRealtime() - t0}ms") }
            }
            log("MODEL READY after ${SystemClock.elapsedRealtime() - t0} ms")
            assertTrue("model should be ready", provider.isReady("en"))

            val bmp = Bitmap.createBitmap(1080, 700, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f }
            c.drawText("全新未拆封 苹果手机", 40f, 150f, paint)
            c.drawText("价格 1288 元", 40f, 330f, paint)
            c.drawText("包邮 当天发货", 40f, 510f, paint)

            val t1 = SystemClock.elapsedRealtime()
            val blocks = MlKitOcrEngine().recognize(bmp)
            log("OCR ${SystemClock.elapsedRealtime() - t1} ms: " + blocks.joinToString { "'${it.text}'@${it.bounds}" })
            val chinese = blocks.filter { TextUtils.containsChinese(it.text) }
            assertTrue("OCR must find Chinese text", chinese.isNotEmpty())

            val t2 = SystemClock.elapsedRealtime()
            val out = TranslationManager(provider).translate(chinese, "en")
            log("TRANSLATE ${SystemClock.elapsedRealtime() - t2} ms: " + out.joinToString { "'${it.original}' -> '${it.translation}'" })
            assertTrue(out.isNotEmpty())
            assertTrue("translations must be English", out.none { TextUtils.containsChinese(it.translation) })
            assertTrue("price must survive", out.any { it.translation.contains("1288") })
            bmp.recycle()
        }
    }
}
