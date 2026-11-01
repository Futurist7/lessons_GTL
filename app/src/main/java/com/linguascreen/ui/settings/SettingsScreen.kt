package com.linguascreen.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguascreen.data.prefs.ThemeMode
import com.linguascreen.domain.Languages
import com.linguascreen.ui.LabeledRow
import com.linguascreen.ui.LanguageDropdown
import com.linguascreen.ui.ModelCard
import com.linguascreen.ui.OnResume
import com.linguascreen.ui.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val usage by vm.usageAccess.collectAsStateWithLifecycle()
    val context = LocalContext.current
    OnResume { vm.refreshUsageAccess() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Text("Translation", style = MaterialTheme.typography.titleMedium)
                LabeledRow("Translate to", "Source: Chinese (auto-detected)") {
                    LanguageDropdown(s.targetLanguage, vm::setTarget)
                }
                LabeledRow("Download models on mobile data", "Off = Wi-Fi only") {
                    Switch(checked = s.allowCellularDownload, onCheckedChange = vm::setAllowCellular)
                }
            }
            ModelCard(Languages.nameOf(s.targetLanguage))

            SectionCard {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = s.theme == mode,
                            onClick = { vm.setTheme(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Text(
                    "Translation overlay opacity",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Slider(value = s.overlayOpacity, onValueChange = vm::setOpacity, valueRange = 0.5f..1f)
            }

            SectionCard {
                Text("Privacy", style = MaterialTheme.typography.titleMedium)
                LabeledRow("Save translation history", "Stored only on this device") {
                    Switch(checked = s.saveHistory, onCheckedChange = vm::setSaveHistory)
                }
                LabeledRow(
                    "Record source app",
                    if (usage) "Usage access granted" else "Optional. Needs Usage access to label history with the app name.",
                ) {
                    if (!usage) {
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }) { Text("Grant") }
                    }
                }
                Text(
                    "Screenshots are taken only when you tap Translate Screen, are read on this device, " +
                        "and are discarded immediately. Nothing is uploaded; the internet is used only to " +
                        "download language models.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            SectionCard {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text("LinguaScreen 1.0.0", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
