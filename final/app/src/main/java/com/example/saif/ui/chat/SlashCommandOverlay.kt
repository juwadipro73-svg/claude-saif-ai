package com.example.saif.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.saif.data.remote.SlashCommandItem

@Composable
fun SlashCommandOverlay(items: List<SlashCommandItem>, query: String,
    runsOnTap: (SlashCommandItem) -> Boolean, onPick: (SlashCommandItem) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, tonalElevation = 4.dp) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
            items(items, key = { it.command }) { item ->
                TextButton(onClick = { onPick(item) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(item.displayCommand + if (runsOnTap(item)) " · Open" else "")
                        Text(item.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
