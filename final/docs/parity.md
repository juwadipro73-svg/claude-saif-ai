# SAIF AI Parity & Verification Checklist

This document tracks behavioral and functional parity for every cleanroom module against the specifications in `docs/spec/`.

---

## Module 1: Core Ports & Domain Contracts (`docs/spec/ports.md`)
- [x] Immutable `ChatMessage` data model with roles (User, Assistant, Tool, System, Summary).
- [x] `ToolCall` and `ToolType` representations for Bash, File I/O, Web Search, and Questionnaires.
- [x] `ChatAttachment` metadata model with MIME type and size safety.
- [x] `AgentBackend` interface decoupling chat view model from network transports.
- [x] `WorkspaceEnvironment` interface defining directory and process resolution.
- [x] `AccountBinding` interface for identity verification.
- [x] `SkillStore` interface for managing user-defined prompts and skills.
- **Verification Tests**:
  - `com.saifai.core.SaifCleanroomEngineTest`: Validates domain models, immutability, and state transitions.

---

## Module 2: Chat Engine, Feed & Interactive Panels (`docs/spec/chat_engine.md`)
- [x] Multi-line prompt input bar with attachment staging, voice trigger, and send action.
- [x] Markdown text rendering: Headers (#, ##, ###), lists, blockquotes, and inline code formatting.
- [x] Monospace code blocks with language badge and one-tap clipboard copy button.
- [x] Expandable / collapsible model reasoning and thinking process display.
- [x] Interactive tool call cards showing running spinner, success/failure status, and stdout/stderr output.
- [x] Docked tool execution approval panel with Allow Once, Always Allow, and Deny actions.
- [x] Interactive questionnaire panel supporting single-choice, multi-choice, and custom text responses.
- **Verification Tests**:
  - `com.example.saif.ui.SaifChatEngine`: Component rendering and interaction state validation.

---

## Module 3: AI Provider Backends (`docs/spec/providers.md`)
- [x] Universal agent backend dispatching prompts and collecting streaming chunks.
- [x] Antigravity Hub WebSocket / HTTP communication pipeline with automatic retry.
- [x] Claude API and CLI protocol with SSE streaming.
- [x] Gemini direct API streaming with function calling support.
- [x] Custom API (OpenAI-compatible) endpoint negotiation with bearer token auth.
- [x] Dynamic model switching without loss of message history.
- **Verification Tests**:
  - `com.example.saif.backend.SaifUniversalAgentBackend`: Asynchronous turn dispatch and tool outcome routing.

---

## Module 4: Accounts, Sessions & Application Settings (`docs/spec/accounts_settings.md`)
- [x] Session history drawer with chronological grouping and session switching.
- [x] Categorized settings bottom sheet (Agents, Appearance, Voice, Workspace, About).
- [x] Kabir voice authority locked internally with zero UI clutter.
- [x] Slash command overlay with real-time skill matching on typing `/`.
- [x] Custom skill document loading and parsing.
- **Verification Tests**:
  - `com.saifai.chat.SaifChatSessionStore`: Validates conversation storage, retrieval, and serialization.
  - `com.saifai.skills.SaifSkillsEngine`: Validates skill parsing, limits, and payload bundling.

---

## Module 5: Workspace Shell, File Manager & Git (`docs/spec/workspace.md`)
- [x] Recursive directory tree with icon mapping by file extension.
- [x] Project switcher and new project scaffolding.
- [x] Safe path resolution preventing directory traversal escapes outside workspace root.
- [x] Git repository status (active branch, modified, added, untracked badges).
- [x] Git diff viewer with additions and deletions color coding.
- **Verification Tests**:
  - `com.saifai.virtualization.SaifNativeWorkspaceEngine`: Verifies workspace boundary checks and file access isolation.

---

## Module 6: Code Studio & Text Editor (`docs/spec/editor.md`)
- [x] Multi-tab document switcher with unsaved modification indicators.
- [x] Monospace code viewport with line numbers gutter.
- [x] Viewport virtualization for smooth performance on large files.
- [x] Quick action toolbar (Save, Undo, Redo, Search, Switch to Terminal).
- [x] Automatic buffer reload on external tool modifications.
- **Verification Tests**:
  - `com.saifai.ide.SaifCodeStudioEngine`: Validates tab opening, dirty tracking, and atomic file saving.

---

## Module 7: Terminal & Process Virtualization (`docs/spec/terminal.md`)
- [x] Direct Java `ProcessBuilder` execution with environment variables (`PATH`, `HOME`).
- [x] Non-blocking background coroutines streaming stdout and stderr.
- [x] Process lifecycle tracking (Idle, Running, Exited, Terminated).
- [x] ANSI color sequence rendering in terminal grid.
- [x] Mobile accessory key bar (`Esc`, `Tab`, `Ctrl`, `Alt`, arrows).
- [x] Process timeout killer preventing runaway zombie processes.
- **Verification Tests**:
  - `com.saifai.core.SaifVirtualTerminal`: Validates shell execution, exit code capture, and streaming output buffers.

---

## Module 8: In-App Browser & Local Bridge Server (`docs/spec/browser_bridge.md`)
- [x] Pure Kotlin in-process HTTP socket daemon bound strictly to `127.0.0.1`.
- [x] Secure artifact download endpoint with token authorization.
- [x] Embedded WebView with address bar, refresh, and shortcut buttons.
- [x] Bidirectional JavaScript bridge (`window.SaifBridge`) for event passing.
- [x] Zero dependency on external Go binaries (`agy_ide_bridge` eliminated).
- **Verification Tests**:
  - `com.saifai.bridge.SaifLocalBridgeServer`: Validates socket binding, download token verification, and MIME dispatch.
