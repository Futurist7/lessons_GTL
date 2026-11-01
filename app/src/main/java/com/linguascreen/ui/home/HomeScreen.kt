package com.linguascreen.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguascreen.R
import com.linguascreen.domain.Languages
import com.linguascreen.service.OverlayService
import com.linguascreen.ui.LabeledRow
import com.linguascreen.ui.LanguageDropdown
import com.linguascreen.ui.ModelCard
import com.linguascreen.ui.OnResume
import com.linguascreen.ui.SectionCard
import com.linguascreen.ui.history.HistoryItem

private enum class Pending { START, TOGGLE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    OnResume { overlayGranted = Settings.canDrawOverlays(context) }

    var showOverlayRationale by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }

    fun launch(p: Pending) {
        OverlayService.start(context, requestConsent = p == Pending.START)
        hint = if (p == Pending.START) {
            "Allow screen capture, then open a Chinese app and tap the floating 译 button."
        } else null
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Notifications are optional: the service runs either way.
        pending?.let { launch(it) }
        pending = null
    }

    fun begin(p: Pending) {
        if (!Settings.canDrawOverlays(context)) { pending = p; showOverlayRationale = true; return }
        val needsNotif = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotif) { pending = p; notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } else launch(p)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1B6EF3)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(40.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("LinguaScreen", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.SettingsIcon, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Translate Chinese apps on demand. Xianyu, Taobao, 1688, Pinduoduo, WeChat and more.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { begin(Pending.START) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Start Translation", style = MaterialTheme.typography.titleMedium) }
            hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }

            SectionCard {
                LabeledRow(
                    "Floating translation button",
                    if (overlayGranted) "Shows a button over other apps" else "Needs \"Display over other apps\" permission",
                ) {
                    Switch(checked = running, onCheckedChange = { on ->
                        if (on) begin(Pending.TOGGLE) else OverlayService.stop(context)
                    })
                }
                LabeledRow("Translate to", "Source language: Chinese (detected automatically)") {
                    LanguageDropdown(settings.targetLanguage, vm::setTarget)
                }
            }

            ModelCard(Languages.nameOf(settings.targetLanguage))

            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent translations", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenHistory) { Text("See all") }
                }
                if (recent.isEmpty()) {
                    Text(
                        "Nothing yet. Translations you make are saved on this device only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    recent.forEach { HistoryItem(it, onDelete = null) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showOverlayRationale) {
        AlertDialog(
            onDismissRequest = { showOverlayRationale = false; pending = null },
            title = { Text("Allow display over other apps") },
            text = {
                Text(
                    "LinguaScreen needs this permission to show the floating button and draw translations " +
                        "on top of other apps. After you allow it, come back and tap Start Translation again.\n\n" +
                        "Screens are only captured when you tap Translate, are processed on this device, " +
                        "and are never saved or uploaded.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayRationale = false
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { showOverlayRationale = false; pending = null }) { Text("Cancel") } },
        )
    }
}
