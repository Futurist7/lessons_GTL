package com.linguascreen.data.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.linguascreen.domain.LinguaException
import com.linguascreen.domain.OcrBlock
import com.linguascreen.domain.OcrEngine
import com.linguascreen.util.TextUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device Chinese OCR (ML Kit, bundled model). The Chinese recognizer also reads Latin text,
 * so mixed Chinese/English screens work. Runs entirely offline.
 */
@Singleton
class MlKitOcrEngine @Inject constructor() : OcrEngine {
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    override suspend fun recognize(bitmap: Bitmap): List<OcrBlock> {
        val result = try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LinguaException.OcrFailed(e)
        }
        return result.textBlocks.mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            val text = TextUtils.joinLines(block.lines.map { it.text })
            if (text.isBlank()) null else OcrBlock(text, box)
        }
    }
}
