package com.linguascreen.domain

import android.graphics.Rect

/** A block of text found by OCR, with its position in screenshot pixel coordinates. */
data class OcrBlock(val text: String, val bounds: Rect)

/** An OCR block together with its translation. */
data class TranslatedBlock(val original: String, val translation: String, val bounds: Rect)

/** Result of translating one screenshot. [screenWidth]/[screenHeight] are the screenshot dimensions. */
data class ScreenTranslation(
    val blocks: List<TranslatedBlock>,
    val screenWidth: Int,
    val screenHeight: Int,
)

/** Pure geometry type (no Android dependency) used by the overlay layout algorithm. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun offset(dx: Float, dy: Float) = Box(left + dx, top + dy, right + dx, bottom + dy)
    fun intersects(o: Box, tolerance: Float = 1f) =
        left < o.right - tolerance && o.left < right - tolerance &&
            top < o.bottom - tolerance && o.top < bottom - tolerance
}

/** A selectable translation target language. [tag] is an ML Kit language code. */
data class Language(val tag: String, val displayName: String)

/** User-facing failures. Every message is safe to show directly in a toast or dialog. */
sealed class LinguaException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class OverlayPermissionMissing :
        LinguaException("Overlay permission is missing. Open LinguaScreen and grant \"Display over other apps\".")

    class CaptureDenied :
        LinguaException("Screen capture permission was not granted. Tap Translate again and allow screen capture.")

    class CaptureFailed(cause: Throwable? = null) :
        LinguaException("Could not capture the screen. Please try again.", cause)

    class SecureContent :
        LinguaException("This screen is protected by the app and cannot be captured.")

    class NoTextFound : LinguaException("No Chinese text was found on this screen.")

    class OcrFailed(cause: Throwable? = null) :
        LinguaException("Text recognition failed. Please try again.", cause)

    class ModelNotDownloaded :
        LinguaException("The offline translation model is not downloaded yet. Open LinguaScreen and tap Download.")

    class ModelDownloadFailed(val offline: Boolean, cause: Throwable? = null) :
        LinguaException(
            if (offline) "No internet connection. Connect to Wi-Fi to download the language model."
            else "The language model download failed. Check your connection and try again.",
            cause,
        )

    class TranslationFailed(cause: Throwable? = null) :
        LinguaException("Translation failed. Please try again.", cause)
}
