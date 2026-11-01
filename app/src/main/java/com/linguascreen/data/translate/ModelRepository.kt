package com.linguascreen.data.translate

import com.linguascreen.domain.LinguaException
import com.linguascreen.domain.TranslationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ModelState {
    data object Checking : ModelState
    data object Missing : ModelState
    data object Downloading : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/** Observable state of the offline translation model for the currently selected language. */
@Singleton
class ModelRepository @Inject constructor(private val provider: TranslationProvider) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<ModelState>(ModelState.Checking)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    suspend fun refresh(target: String) {
        if (_state.value == ModelState.Downloading) return
        _state.value = ModelState.Checking
        _state.value = if (provider.isReady(target)) ModelState.Ready else ModelState.Missing
    }

    /** Starts a download that survives screen changes (it runs in the repository's own scope). */
    fun startDownload(target: String, allowCellular: Boolean) {
        scope.launch { download(target, allowCellular) }
    }

    private suspend fun download(target: String, allowCellular: Boolean) {
        if (_state.value == ModelState.Downloading) return
        _state.value = ModelState.Downloading
        try {
            provider.downloadModel(target, allowCellular)
            _state.value = if (provider.isReady(target)) ModelState.Ready else ModelState.Missing
        } catch (e: CancellationException) {
            _state.value = ModelState.Missing
            throw e
        } catch (e: LinguaException) {
            _state.value = ModelState.Failed(e.message ?: "Download failed")
        }
    }
}
