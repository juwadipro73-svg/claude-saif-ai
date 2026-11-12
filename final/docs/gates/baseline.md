# Phase 0 Baseline Audit: Legacy Dependencies & Missing Symbols

## 1. Audit Overview
- **Quarantined Legacy Code**: 220 files (~52,400 lines) moved to `/outside_repo/legacy/` completely outside repository boundaries and ignored by Git.
- **Affected Packages**: `com.example.saif.data`, `com.example.saif.domain`, `com.example.saif.theme`, `com.example.saif.ui` (all originating from Saif GPLv3).
- **Broken Imports Count**: 41 unresolved internal import declarations across 19 active workspace files.

## 2. Unresolved Import Inventory by Module

### A. Ports, Domain & Agent Models
- `com.example.saif.domain.model.AgentKind` (in `SaifAgentsManagerDialog.kt`, `TerminalProviderManager.kt`)
- `com.example.saif.domain.model.TokenUsage` (in `AntigravityCustomApiAgentEngine.kt`)
- `com.example.saif.data.agent.AgentPreferences` (in `SaifAgentsManagerDialog.kt`, `TerminalProviderManager.kt`)
- `com.example.saif.data.agent.ChatSessionStore` (in `AntigravityCustomApiAgentEngine.kt`, `AntigravityLlmBridge.kt`)
- `com.example.saif.data.agent.agy.AgyChatBackend` (in `AntigravityLlmBridge.kt`)

### B. Environment & Bridge Infrastructure
- `com.example.saif.data.local.LocalEnvironmentManager` (in `AntigravityCustomApiAgentEngine.kt`, `TerminalProviderManager.kt`)
- `com.example.saif.data.local.LocalInstallerState` (in `TerminalProviderManager.kt`)
- `com.example.saif.data.remote.AndroidLocalBridgeServer` (in `AntigravityCustomApiAgentEngine.kt`, `BuildProgressDialog.kt`, `LinuxApkBuildBridge.kt`, `OnDeviceApkBuildManager.kt`)
- `com.example.saif.data.remote.SystemConnectionState` (in `SaifEngineScreens.kt`)
- `com.example.saif.data.remote.SystemStatus` (in `SaifEngineScreens.kt`)
- `com.example.saif.data.remote.AgyHubClient` (in `SaifEngineUi.kt`)
- `com.example.saif.data.remote.GoogleAccountBinding` (in `AntigravityLlmBridge.kt`)

### C. Skills & Slash Command Engine
- `com.example.saif.data.remote.SavedAiSkills` (in `LiveCodingBridge.kt`, `SaifSlashCommandRegistry.kt`, `SaifSkillsManagerDialog.kt`)
- `com.example.saif.data.remote.SkillSlashParsing` (in `LiveCodingBridge.kt`)
- `com.example.saif.data.remote.CustomSlashCommands` (in `SaifSlashCommandRegistry.kt`)
- `com.example.saif.data.remote.PromptExpansion` (in `SaifSlashCommandRegistry.kt`)
- `com.example.saif.data.remote.SlashCommandItem` (in `SaifSlashCommandRegistry.kt`)
- `com.example.saif.data.remote.SlashCommandsCache` (in `SaifSlashCommandRegistry.kt`)

### D. Chat Engine & UI
- `com.example.saif.ui.chat.ChatFeedCache` (in `HomeAgentFeed.kt`)
- `com.example.saif.ui.chat.ChatFeedItem` (in `HomeAgentFeed.kt`)
- `com.example.saif.ui.chat.ChatViewModelHolder` (in `AntigravityLlmBridge.kt`)
- `com.example.saif.ui.settings.ENGINE_SETTINGS_SECTIONS` (in `SettingsSheet.kt`)
- `com.example.saif.ui.settings.SettingsSection` (in `SettingsSheet.kt`)
- `com.example.saif.data.daemon.GitApiClient` (in `GitHubIntegrationManager.kt`)

### E. Embedded Engine Facade Views
- `com.saifai.browser.SaifResearchBrowserView` (in `SaifEmbeddedEngineView.kt`)
- `com.saifai.ide.SaifCodeStudioView` (in `SaifEmbeddedEngineView.kt`)
- `com.saifai.notes.SaifNotesView` (in `SaifEmbeddedEngineView.kt`)
- `com.saifai.terminal.SaifTerminalView` (in `SaifEmbeddedEngineView.kt`)

---

## 3. Cleanroom Resolution Plan
Every one of these 41 missing symbols will be resolved by cleanroom implementations in:
1. `com.example.core.model` / `com.example.core.ports`
2. `com.example.core.adapters`
3. `com.example.feature.chat`
4. `com.example.feature.settings`
5. `com.example.feature.workspace`
6. `com.example.feature.editor`
7. `com.example.feature.terminal`
8. `com.example.feature.browser`
without copying any third-party code.
