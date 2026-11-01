package com.linguascreen.domain

import android.graphics.Bitmap

/** Finds text and its location in a bitmap. */
interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): List<OcrBlock>
}

/**
 * Pluggable translation backend (Chinese -> [target]). The default implementation is ML Kit
 * on-device; another provider can be added by implementing this interface and rebinding it in
 * [com.linguascreen.di.AppModule].
 */
interface TranslationProvider {
    /** True when every model needed for Chinese -> [target] is on the device. */
    suspend fun isReady(target: String): Boolean

    /** Downloads missing models. Throws [LinguaException.ModelDownloadFailed]. */
    suspend fun downloadModel(target: String, allowCellular: Boolean)

    /** Translates one piece of text. Throws on failure. */
    suspend fun translate(text: String, target: String): String
}
