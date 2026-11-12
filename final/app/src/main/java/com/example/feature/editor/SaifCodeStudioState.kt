/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.feature.editor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 100% Original SAIF AI Code Studio State.
 * Implements docs/spec/editor.md.
 */
data class SaifEditorTab(
    val file: File,
    val initialContent: String,
    val currentContent: String = initialContent,
    val isDirty: Boolean = false
)

class SaifCodeStudioState {

    private val _tabs = MutableStateFlow<List<SaifEditorTab>>(emptyList())
    val tabs: StateFlow<List<SaifEditorTab>> = _tabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    suspend fun openFile(file: File) = withContext(Dispatchers.IO) {
        val existingIndex = _tabs.value.indexOfFirst { it.file.canonicalPath == file.canonicalPath }
        if (existingIndex >= 0) {
            _activeTabIndex.value = existingIndex
            return@withContext
        }
        val content = if (file.exists() && file.isFile) file.readText() else ""
        val newTab = SaifEditorTab(file = file, initialContent = content)
        _tabs.value = _tabs.value + newTab
        _activeTabIndex.value = _tabs.value.lastIndex
    }

    fun updateBuffer(index: Int, newText: String) {
        val currentList = _tabs.value.toMutableList()
        if (index in currentList.indices) {
            val tab = currentList[index]
            currentList[index] = tab.copy(currentContent = newText, isDirty = newText != tab.initialContent)
            _tabs.value = currentList
        }
    }

    suspend fun saveActiveTab() = withContext(Dispatchers.IO) {
        val index = _activeTabIndex.value
        val list = _tabs.value.toMutableList()
        if (index in list.indices) {
            val tab = list[index]
            // Atomic file write via temp file
            val tempFile = File(tab.file.parentFile, "${tab.file.name}.tmp")
            tempFile.writeText(tab.currentContent)
            if (tab.file.exists()) tab.file.delete()
            tempFile.renameTo(tab.file)
            list[index] = tab.copy(initialContent = tab.currentContent, isDirty = false)
            _tabs.value = list
        }
    }

    fun closeTab(index: Int) {
        val list = _tabs.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _tabs.value = list
            _activeTabIndex.value = (_activeTabIndex.value - 1).coerceAtLeast(0)
        }
    }
}
