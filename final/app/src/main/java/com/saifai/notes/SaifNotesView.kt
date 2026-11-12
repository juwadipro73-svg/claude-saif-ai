/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.notes

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.UUID

@Composable
fun SaifNotesView(
    onClose: () -> Unit,
    modifier: Modifier =
    Modifier
) {
    val context =
    LocalContext.current
    val notesDir = remember(context) { File(context.filesDir, "saif_notes").apply { mkdirs() } }

    var notesList by remember {
        mutableStateOf(notesDir.listFiles { _, name -> name.endsWith(".md") }?.toList().orEmpty().sortedByDescending { it.lastModified() })
    }

    var selectedFile by remember { mutableStateOf<File?>(notesList.firstOrNull()) }
    var noteTitle by remember(selectedFile) { mutableStateOf(selectedFile?.nameWithoutExtension ?: "Quick Note") }
    var noteContent by remember(selectedFile) { mutableStateOf(selectedFile?.readText() ?: "# Quick Note\n\nWrite your thoughts or architecture ideas here.\n") }

    Surface(
        color = Color(0xFF141312),
        modifier =
    modifier.fillMaxSize()
    ) {
        Row(modifier =
    Modifier.fillMaxSize()) {
            // Sidebar: List of notes
            Column(
                modifier =
    Modifier
                    .width(180.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1B1A19))
                    .padding(8.dp)
            ) {
                Row(
                    modifier =
    Modifier.fillMaxWidth(),
                    horizontalArrangement =
    Arrangement.SpaceBetween,
                    verticalAlignment =
    Alignment.CenterVertically
                ) {
                    Text("My Notes", fontWeight =
    FontWeight.Bold, color =
    Color.White, fontSize =
    13.sp)
                    IconButton(
                        onClick = {
                            val newF = File(notesDir, "Note_${System.currentTimeMillis()}.md").apply {
                                writeText("# New Note\n")
                            }
                            notesList = listOf(newF) + notesList
                            selectedFile = newF
                        },
                        modifier =
    Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New", tint = Color(0xFF9B82EC), modifier =
    Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier =
    Modifier.weight(1f)) {
                    items(notesList) { f ->
                        val isSel = f == selectedFile
                        Surface(
                            shape =
    RoundedCornerShape(8.dp),
                            color = if (isSel) Color(0xFF2E2C2A) else Color.Transparent,
                            modifier =
    Modifier
                                .fillMaxWidth()
                                .clickable { selectedFile = f }
                        ) {
                            Text(
                                f.nameWithoutExtension,
                                color = if (isSel) Color(0xFFEDE9FE) else Color(0xFF9CA3AF),
                                fontSize =
    11.sp,
                                maxLines = 1,
                                modifier =
    Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Editor
            Column(
                modifier =
    Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(12.dp)
            ) {
                Row(
                    modifier =
    Modifier.fillMaxWidth(),
                    horizontalArrangement =
    Arrangement.SpaceBetween,
                    verticalAlignment =
    Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        textStyle = TextStyle(color =
    Color.White, fontSize =
    16.sp, fontWeight =
    FontWeight.Bold),
                        cursorBrush = SolidColor(Color(0xFF9B82EC)),
                        modifier =
    Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            val target = selectedFile ?: File(notesDir, "${noteTitle.ifBlank { "Note" }}.md")
                            target.writeText(noteContent)
                            selectedFile = target
                            notesList = notesDir.listFiles { _, name -> name.endsWith(".md") }?.toList().orEmpty().sortedByDescending { it.lastModified() }
                        },
                        modifier =
    Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = "Save", tint = Color(0xFF10B981), modifier =
    Modifier.size(18.dp))
                    }

                    IconButton(onClick = onClose, modifier =
    Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint =
    Color.White, modifier =
    Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                BasicTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    textStyle = TextStyle(color = Color(0xFFE5E7EB), fontSize =
    13.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(Color(0xFF9B82EC)),
                    modifier =
    Modifier.fillMaxSize()
                )
            }
        }
    }
}
