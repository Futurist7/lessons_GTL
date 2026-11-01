package com.linguascreen.domain

import android.content.Context
import android.graphics.Bitmap
import com.linguascreen.data.prefs.SettingsRepository
import com.linguascreen.data.repo.HistoryRepository
import com.linguascreen.data.translate.TranslationManager
import com.linguascreen.util.ScreenMetrics
import com.linguascreen.util.TextUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** screenshot -> OCR -> filter -> translate -> history. The bitmap is never stored or written out. */
@Singleton
class TranslationPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocr: OcrEngine,
    private val provider: TranslationProvider,
    private val translations: TranslationManager,
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
) {
    private var lastHash = 0L
    private var lastTarget = ""
    private var lastResult: ScreenTranslation? = null

    suspend fun run(bitmap: Bitmap, sourceApp: String?): ScreenTranslation {
        val s = settings.current()
        val target = s.targetLanguage
        if (!provider.isReady(target)) throw LinguaException.ModelNotDownloaded()

        // Same pixels as last time (e.g. user tapped twice): reuse, skip OCR + translation.
        val hash = withContext(Dispatchers.Default) { fingerprint(bitmap) }
        lastResult?.let { if (hash == lastHash && target == lastTarget) return it }

        val raw = ocr.recognize(bitmap)
        debugLog("OCR ${bitmap.width}x${bitmap.height}: " + raw.joinToString { "'${it.text}'@${it.bounds.flattenToString()}" })
        val blocks = filter(raw, bitmap.width, bitmap.height)
        if (blocks.isEmpty()) {
            if (isBlank(bitmap)) throw LinguaException.SecureContent()
            throw LinguaException.NoTextFound()
        }

        val translated = translations.translate(blocks, target)
        debugLog("TRANSLATED: " + translated.joinToString { "'${it.original}' -> '${it.translation}'" })
        val result = ScreenTranslation(translated, bitmap.width, bitmap.height)
        lastHash = hash; lastTarget = target; lastResult = result

        if (s.saveHistory) runCatching { history.save(translated, sourceApp) }
        return result
    }

    /** Logs recognised text, in debuggable builds only (never in release). */
    private fun debugLog(msg: String) {
        if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            android.util.Log.i("LinguaTest", msg)
        }
    }

    /** Frees cached results; called when the system is low on memory. */
    fun trimMemory() {
        lastResult = null
        translations.clearCache()
    }

    /** Keeps Chinese-containing blocks and drops those inside the status / navigation bars. */
    private fun filter(blocks: List<OcrBlock>, width: Int, height: Int): List<OcrBlock> {
        val top = ScreenMetrics.statusBarHeight(context)
        val bottom = height - ScreenMetrics.navigationBarHeight(context)
        return blocks.filter {
            TextUtils.containsChinese(it.text) &&
                it.bounds.centerY() in top..bottom &&
                it.bounds.width() > 4 && it.bounds.height() > 4
        }
    }

    /** Cheap 64-bit fingerprint from a sparse pixel grid. */
    private fun fingerprint(bmp: Bitmap): Long {
        var h = 1125899906842597L
        val stepX = maxOf(1, bmp.width / 48)
        val stepY = maxOf(1, bmp.height / 96)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                h = 31 * h + bmp.getPixel(x, y)
                x += stepX
            }
            y += stepY
        }
        return h
    }

    /** True if every sampled pixel is (near) black - what a FLAG_SECURE window looks like. */
    private fun isBlank(bmp: Bitmap): Boolean {
        val stepX = maxOf(1, bmp.width / 24)
        val stepY = maxOf(1, bmp.height / 48)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val p = bmp.getPixel(x, y)
                if ((p and 0xFF) > 8 || ((p shr 8) and 0xFF) > 8 || ((p shr 16) and 0xFF) > 8) return false
                x += stepX
            }
            y += stepY
        }
        return true
    }
}
