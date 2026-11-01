package com.linguascreen.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguascreen.data.translate.ModelState
import com.linguascreen.domain.Language
import com.linguascreen.domain.Languages

/** Runs [block] every time the screen returns to the foreground (e.g. back from system settings). */
@Composable
fun OnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) block() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
fun LanguageDropdown(selected: String, onSelected: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text(Languages.nameOf(selected)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        Languages.targets.forEach { lang: Language ->
            DropdownMenuItem(text = { Text(lang.displayName) }, onClick = { open = false; onSelected(lang.tag) })
        }
    }
}

/** Shows the offline model status and the Download button. */
@Composable
fun ModelCard(targetName: String, vm: ModelViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    OnResume { vm.refresh() }
    SectionCard {
        Text("Offline language model", style = MaterialTheme.typography.titleMedium)
        Text(
            "Chinese → $targetName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        when (val s = state) {
            ModelState.Checking -> Text("Checking…", style = MaterialTheme.typography.bodyMedium)
            ModelState.Ready -> Text("Ready. Translation works offline.", style = MaterialTheme.typography.bodyMedium)
            is ModelState.Downloading -> {
                val step = if (s.total > 0) " (${minOf(s.done + 1, s.total)} of ${s.total})" else ""
                val name = if (s.current.isNotBlank()) "${s.current} model" else "models"
                Text("Downloading $name$step…", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            ModelState.Missing -> {
                Text(
                    "Not downloaded yet. Download once (about 30 MB per model, Chinese + English) to translate offline.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::download) { Text("Download model") }
            }
            is ModelState.Failed -> {
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::download) { Text("Retry download") }
            }
        }
    }
}

@Composable
fun LabeledRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

