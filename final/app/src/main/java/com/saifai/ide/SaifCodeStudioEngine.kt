/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.ide

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.saifai.virtualization.SaifNativeWorkspaceEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 100% Original Cleanroom Code Studio & IDE Engine for SAIF AI.
 * Completely replaces legacy IdeScreen & CodeEditorView.
 */
class SaifCodeStudioEngine private constructor() {

    companion object {
        val instance: SaifCodeStudioEngine by lazy { SaifCodeStudioEngine() }
    }

    data class OpenFileTab(
        val relativePath: String,
        val displayName: String,
        val content: String,
        val isModified: Boolean = false
    )

    private val openTabs = ConcurrentHashMap<String, OpenFileTab>()
    private val _tabsFlow = MutableStateFlow<List<OpenFileTab>>(emptyList())
    val tabsFlow: StateFlow<List<OpenFileTab>> = _tabsFlow.asStateFlow()

    private val _activeFilePath = MutableStateFlow<String?>(null)
    val activeFilePath: StateFlow<String?> = _activeFilePath.asStateFlow()

    fun openFile(context: Context, relativePath: String): OpenFileTab? {
        val hostFile = SaifNativeWorkspaceEngine.instance.resolveHostFile(context, relativePath)
            ?: File(SaifNativeWorkspaceEngine.instance.ensureWorkspaceInitialized(context), relativePath)

        val content = if (hostFile.isFile && hostFile.canRead()) {
            hostFile.readText()
        } else {
            ""
        }
        val tab = OpenFileTab(
            relativePath = relativePath,
            displayName = hostFile.name.ifBlank { relativePath },
            content = content
        )
        openTabs[relativePath] = tab
        _tabsFlow.value = openTabs.values.toList()
        _activeFilePath.value = relativePath
        return tab
    }

    fun updateContent(relativePath: String, newContent: String) {
        val existing = openTabs[relativePath] ?: return
        openTabs[relativePath] = existing.copy(content = newContent, isModified = true)
        _tabsFlow.value = openTabs.values.toList()
    }

    fun saveFile(context: Context, relativePath: String): Boolean {
        val tab = openTabs[relativePath] ?: return false
        val hostFile = SaifNativeWorkspaceEngine.instance.resolveHostFile(context, relativePath)
            ?: File(SaifNativeWorkspaceEngine.instance.ensureWorkspaceInitialized(context), relativePath)

        return try {
            hostFile.parentFile?.mkdirs()
            hostFile.writeText(tab.content)
            openTabs[relativePath] = tab.copy(isModified = false)
            _tabsFlow.value = openTabs.values.toList()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun closeTab(relativePath: String) {
        openTabs.remove(relativePath)
        val remaining = openTabs.values.toList()
        _tabsFlow.value = remaining
        if (_activeFilePath.value == relativePath) {
            _activeFilePath.value = remaining.firstOrNull()?.relativePath
        }
    }

    fun highlightCode(code: String, fileExtension: String): AnnotatedString {
        val builder = buildAnnotatedString {
            append(code)
            val keywords = when (fileExtension.lowercase()) {
                "kt", "java" -> setOf("fun", "val", "var", "class", "interface", "import", "package", "override", "return", "if", "else", "when", "for", "while")
                "py" -> setOf("def", "class", "import", "from", "return", "if", "elif", "else", "for", "while", "try", "except", "with", "as")
                "js", "ts" -> setOf("const", "let", "var", "function", "return", "import", "export", "from", "default", "class", "if", "else", "for")
                else -> emptySet()
            }

            for (kw in keywords) {
                val regex = Regex("\\b$kw\\b")
                for (match in regex.findAll(code)) {
                    addStyle(
                        SpanStyle(color = Color(0xFFC084FC), fontWeight =
    FontWeight.Bold),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // String literals
            val stringRegex = Regex("\"[^\"]*\"|'[^']*'")
            for (match in stringRegex.findAll(code)) {
                addStyle(
                    SpanStyle(color = Color(0xFF34D399)),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // Comments
            val commentRegex = Regex("//.*|#.*")
            for (match in commentRegex.findAll(code)) {
                addStyle(
                    SpanStyle(color = Color(0xFF9CA3AF)),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }
        return builder
    }
}
