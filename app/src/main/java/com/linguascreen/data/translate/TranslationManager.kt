package com.linguascreen.data.translate

import android.util.LruCache
import com.linguascreen.domain.LinguaException
import com.linguascreen.domain.OcrBlock
import com.linguascreen.domain.TranslatedBlock
import com.linguascreen.domain.TranslationProvider
import com.linguascreen.util.TextUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/** Adds an in-memory cache and number protection on top of any [TranslationProvider]. */
@Singleton
class TranslationManager @Inject constructor(private val provider: TranslationProvider) {
    private val cache = LruCache<String, String>(MAX_CACHE)
    private val gate = Semaphore(4)

    suspend fun translate(blocks: List<OcrBlock>, target: String): List<TranslatedBlock> {
        if (blocks.isEmpty()) return emptyList()
        var firstError: Throwable? = null
        val results = coroutineScope {
            blocks.map { block ->
                async {
                    try {
                        TranslatedBlock(block.text, translateText(block.text, target), block.bounds)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (firstError == null) firstError = e
                        null // one failing block must not discard the rest
                    }
                }
            }.map { it.await() }
        }
        val ok = results.filterNotNull()
        if (ok.isEmpty()) throw (firstError as? LinguaException ?: LinguaException.TranslationFailed(firstError))
        return ok
    }

    private suspend fun translateText(text: String, target: String): String {
        val key = "$target|$text"
        synchronized(cache) { cache.get(key) }?.let { return it }
        val raw = gate.withPermit { provider.translate(text, target) }
        val out = TextUtils.preserveNumbers(text, raw.trim())
        synchronized(cache) { cache.put(key, out) }
        return out
    }

    fun clearCache() = synchronized(cache) { cache.evictAll() }

    private companion object {
        const val MAX_CACHE = 800
    }
}
