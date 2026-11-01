package com.linguascreen.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguascreen.data.prefs.AppSettings
import com.linguascreen.data.prefs.SettingsRepository
import com.linguascreen.data.prefs.ThemeMode
import com.linguascreen.util.ForegroundAppDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val detector: ForegroundAppDetector,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _usageAccess = MutableStateFlow(detector.hasAccess())
    val usageAccess: StateFlow<Boolean> = _usageAccess.asStateFlow()

    fun refreshUsageAccess() { _usageAccess.value = detector.hasAccess() }

    fun setTheme(v: ThemeMode) { viewModelScope.launch { repo.setTheme(v) } }
    fun setTarget(v: String) { viewModelScope.launch { repo.setTarget(v) } }
    fun setSaveHistory(v: Boolean) { viewModelScope.launch { repo.setSaveHistory(v) } }
    fun setAllowCellular(v: Boolean) { viewModelScope.launch { repo.setAllowCellular(v) } }
    fun setOpacity(v: Float) { viewModelScope.launch { repo.setOverlayOpacity(v) } }
}
