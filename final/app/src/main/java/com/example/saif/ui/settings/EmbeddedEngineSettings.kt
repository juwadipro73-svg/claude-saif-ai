package com.example.saif.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.saif.ui.chat.SaifSharedChatViewModel

@Composable
fun EmbeddedEngineSettings(viewModel: SaifSharedChatViewModel, isLightMode: Boolean,
    initialSection: String?, onOpenTerminal: () -> Unit, autoApprove: Boolean,
    onAutoApproveChange: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var providers by remember { mutableStateOf(false) }
    if (providers) {
        com.example.agyext.AntigravityCustomApiDialog(onDismiss = { providers = false })
    } else AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Native workspace settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tools run inside the app sandbox. Linux CLI providers need a separately installed runtime; unavailable integrations are not enabled automatically.")
                Row {
                    Text("Auto-approve commands", modifier = Modifier.weight(1f))
                    Switch(checked = autoApprove, onCheckedChange = onAutoApproveChange)
                }
                if (autoApprove) Text("Commands can edit and delete workspace files without confirmation.")
                TextButton(onClick = { providers = true }) { Text("API provider and model") }
                TextButton(onClick = { onDismiss(); onOpenTerminal() }) { Text("Open terminal") }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
