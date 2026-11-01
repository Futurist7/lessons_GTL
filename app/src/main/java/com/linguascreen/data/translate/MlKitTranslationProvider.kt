package com.linguascreen.data.translate

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.linguascreen.domain.LinguaException
import com.linguascreen.domain.TranslationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit on-device translation, Chinese -> [target]. Models (~30 MB each) are downloaded once and
 * then work offline. Non-English targets pivot through English, so English is checked too.
 */
@Singleton
class MlKitTranslationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : TranslationProvider {

    private val translators = ConcurrentHashMap<String, Translator>()
    private val modelManager = RemoteModelManager.getInstance()

    private fun translator(target: String): Translator = translators.getOrPut(target) {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.CHINESE)
                .setTargetLanguage(target)
                .build(),
        )
    }

    override suspend fun isReady(target: String): Boolean {
        val needed = setOf(TranslateLanguage.CHINESE, TranslateLanguage.ENGLISH, target)
        return try {
            needed.all { lang ->
                modelManager.isModelDownloaded(TranslateRemoteModel.Builder(lang).build()).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun downloadModel(
        target: String,
        allowCellular: Boolean,
        onProgress: (done: Int, total: Int, name: String) -> Unit,
    ) {
        if (!isOnline()) throw LinguaException.ModelDownloadFailed(offline = true)
        // ML Kit's own requireWifi() waits silently forever on mobile data, so check it ourselves
        // and fail fast with a clear message instead.
        if (!allowCellular && isMetered()) throw LinguaException.WifiRequired()
        val missing = linkedSetOf(TranslateLanguage.CHINESE, TranslateLanguage.ENGLISH, target).filter {
            !isDownloaded(it)
        }
        val conditions = DownloadConditions.Builder().build()
        missing.forEachIndexed { i, lang ->
            onProgress(i, missing.size, Locale.forLanguageTag(lang).getDisplayLanguage(Locale.ENGLISH))
            try {
                withTimeout(DOWNLOAD_TIMEOUT_MS) {
                    modelManager.download(TranslateRemoteModel.Builder(lang).build(), conditions).await()
                }
            } catch (e: TimeoutCancellationException) {
                throw LinguaException.ModelDownloadTimeout()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw LinguaException.ModelDownloadFailed(offline = !isOnline(), cause = e)
            }
        }
        onProgress(missing.size, missing.size, "")
    }

    private suspend fun isDownloaded(lang: String): Boolean =
        modelManager.isModelDownloaded(TranslateRemoteModel.Builder(lang).build()).await()

    private fun isMetered(): Boolean =
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true

    override suspend fun translate(text: String, target: String): String = try {
        translator(target).translate(text).await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw LinguaException.TranslationFailed(e)
    }

    private companion object {
        const val DOWNLOAD_TIMEOUT_MS = 5 * 60_000L
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
