package com.linguascreen.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguascreen.data.prefs.SettingsRepository
import com.linguascreen.data.translate.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val models: ModelRepository,
) : ViewModel() {
    val state = models.state

    init {
        viewModelScope.launch {
            settings.settings.map { it.targetLanguage }.distinctUntilChanged().collect { models.refresh(it) }
        }
    }

    fun refresh() {
        viewModelScope.launch { models.refresh(settings.current().targetLanguage) }
    }

    fun download() {
        viewModelScope.launch {
            val s = settings.current()
            models.startDownload(s.targetLanguage, s.allowCellularDownload)
        }
    }
}
