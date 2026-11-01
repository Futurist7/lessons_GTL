package com.linguascreen.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguascreen.data.db.HistoryEntity
import com.linguascreen.data.prefs.AppSettings
import com.linguascreen.data.prefs.SettingsRepository
import com.linguascreen.data.repo.HistoryRepository
import com.linguascreen.service.OverlayState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    history: HistoryRepository,
    overlayState: OverlayState,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val recent: StateFlow<List<HistoryEntity>> =
        history.recent(5).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val running: StateFlow<Boolean> = overlayState.running

    fun setTarget(tag: String) {
        viewModelScope.launch { settingsRepo.setTarget(tag) }
    }
}
