# SAIF AI Feature Specification

This document provides the user-facing functional specification for SAIF AI, serving as the cleanroom contract for all architectural replacements. New cleanroom code is written strictly to fulfill this specification without copying legacy or copyleft code.

---

## 1. Top Bar & Navigation (SaifHeader)
- Brand Title: Displays SAIF AI with active model indicator badge.
- Session Navigation: Left menu hamburger toggles the conversation session history drawer.
- Horizontal Quick Actions Strip:
  - Smooth horizontal scrolling with gradient indicators and touch chevrons.
  - Admin Panel: Quick access to admin controls and quota inspection.
  - Upgrade Plan: Subscription and tier status.
  - GitHub Connect: Modal dialog for connecting GitHub accounts and cloning repositories directly into /root/workspace/.
  - Theme Toggle: Instant light/dark mode switching with smooth animated icon transitions.
  - Settings: Opens the SAIF AI comprehensive settings bottom sheet.
  - In-App Research Browser: Opens full-screen Web browser for previewing local development servers or web research.
  - Interactive Linux Terminal: Opens the multi-session interactive Linux terminal.
  - Code Studio IDE: Opens the multi-tab code editor with line numbers and save capability.

---

## 2. Conversation & Chat Experience (SaifAiApp & HomeAgentFeed)
- Message Feed:
  - Displays user prompts, assistant thoughts (collapsible reasoning blocks), markdown text, code blocks with syntax highlighting and copy buttons.
  - Interactive Tool Call Cards: Shows tool name, command string, running/success/error status indicators, stdout/stderr output, and duration.
  - Interactive Tool Approval Cards: Prompts user to approve or reject pending system commands with scope selection (Once, Always, Reject).
  - Clarification & Choice Questionnaire: Interactive single/multi-select option cards for clarifying agent queries.
- Composer & Input Bar (SaifInputBar):
  - Multi-line text composer with "REPLY TO SAIF AI" placeholder.
  - Attachment button: Staging images, documents, ZIP archives, and MP4 videos directly into /root/uploads/.
  - Voice button: Speech input with audio visualization.
  - Slash command overlay: Interactive popup on typing "/" with categorized suggestions (App actions, Specialist agents, Engineering workflows, Custom plugins).
  - Send button: Immediately dispatches prompt, collapses slash tokens, and streams response.

---

## 3. Specialist AI Agents & Personas
- Specialist Personas:
  - Coding Architect (Engineering, refactoring, building)
  - Research Specialist (Web, codebase survey, docs)
  - Automation Specialist (Shell scripts, workflow automations)
  - Analysis Specialist (Architecture, security, performance audits)
  - Content Specialist (Documentation, release notes, copywriting)
- Intent Auto-Routing:
  - Automatic detection of prompt complexity to route tasks to the optimal specialist agent.

---

## 4. Native Linux Terminal & Process Layer
- Multi-session terminal tabs with individual process lifecycles.
- Shell prompt environment anchored in /root/workspace.
- Quick action keys: CTRL+C, TAB, ESC, CLEAR, LS, APK.
- Built-in CLI utilities: saif-build-apk (compiles and signs Android APKs) and saif-apk-link (serves files over local HTTP download links).

---

## 5. Code Studio & Workspace File IDE
- Multi-tab file management anchored in the user project workspace.
- Line numbering, monospaced typography, and syntax highlighting.
- Fast file saving with disk persistence and toast notifications.
- Quick navigation button to jump directly into the terminal for the open project.

---

## 6. Research & Preview Browser
- Full-featured WebView component with address bar, back/forward navigation, reload, and progress bar.
- Direct support for viewing http://127.0.0.1:<port> local web applications and public research URLs.

---

## 7. Knowledge Notes Workspace
- Dedicated Markdown notes sidebar and editing view.
- Instant note creation, listing, persistence, and markdown authoring.

---

## 8. Voice & Speech Synthesis (Kabir Authority)
- Permanent internal voice binding to Kabir ("Kabir").
- Clean background TTS synthesis for all chat responses and video studio narrations without exposing confusing voice-picker settings.

---

## 9. Settings Sheet (SettingsSheet)
- Account & Profile row (User email, tier, sign out).
- AI Agents & Skills management (Toggle specialist agents, import skills from GitHub).
- SAIF AI Advanced Editing Engine settings (Resolution, FPS, Bitrate, Audio ducking, Hardware acceleration, LUTs).
- About SAIF AI (Build stamp, legal license, third-party notices).
