package com.linguascreen.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.linguascreen.domain.Languages
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val targetLanguage: String = Languages.DEFAULT_TARGET,
    val saveHistory: Boolean = true,
    val allowCellularDownload: Boolean = true,
    val overlayOpacity: Float = 0.92f,
    /** Last floating-button position in px; -1 means "never moved" (use default). */
    val buttonX: Int = -1,
    val buttonY: Int = -1,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val target = stringPreferencesKey("target")
        val saveHistory = booleanPreferencesKey("save_history")
        val cellular = booleanPreferencesKey("cellular")
        val opacity = floatPreferencesKey("opacity")
        val buttonX = intPreferencesKey("button_x")
        val buttonY = intPreferencesKey("button_y")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            targetLanguage = p[Keys.target] ?: Languages.DEFAULT_TARGET,
            saveHistory = p[Keys.saveHistory] ?: true,
            allowCellularDownload = p[Keys.cellular] ?: true,
            overlayOpacity = p[Keys.opacity] ?: 0.92f,
            buttonX = p[Keys.buttonX] ?: -1,
            buttonY = p[Keys.buttonY] ?: -1,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(v: ThemeMode) = edit { it[Keys.theme] = v.name }
    suspend fun setTarget(v: String) = edit { it[Keys.target] = v }
    suspend fun setSaveHistory(v: Boolean) = edit { it[Keys.saveHistory] = v }
    suspend fun setAllowCellular(v: Boolean) = edit { it[Keys.cellular] = v }
    suspend fun setOverlayOpacity(v: Float) = edit { it[Keys.opacity] = v.coerceIn(0.5f, 1f) }
    suspend fun setButtonPosition(x: Int, y: Int) = edit { it[Keys.buttonX] = x; it[Keys.buttonY] = y }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}

