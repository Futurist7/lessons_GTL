package com.linguascreen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.linguascreen.data.prefs.AppSettings
import com.linguascreen.data.prefs.SettingsRepository
import com.linguascreen.ui.AppNav
import com.linguascreen.ui.theme.LinguaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startRoute = intent?.getStringExtra(EXTRA_ROUTE) ?: "home"
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            LinguaTheme(settings.theme) { AppNav(startRoute) }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
