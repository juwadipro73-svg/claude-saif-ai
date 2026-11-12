/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.saif.ui.settings

enum class SettingsSection(val title: String) {
    GENERAL("General"),
    APPEARANCE("Appearance"),
    VOICE("Voice & Kabir Authority"),
    WORKSPACE("Workspace Shell"),
    WEBVIEW_EXTENSIONS("Web Extensions"),
    SLASH_COMMANDS("Slash Commands"),
    AI_SKILLS("AI Skills"),
    MCP("Model Context Protocol (MCP)")
}

val ENGINE_SETTINGS_SECTIONS: List<SettingsSection> = SettingsSection.entries
