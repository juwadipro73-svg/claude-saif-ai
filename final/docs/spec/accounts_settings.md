# Specification: Accounts, Sessions, Skills & Application Settings

## 1. Scope & Objective
This specification defines identity handling, session history management, custom skill definitions, slash command extensions, and user-configurable application preferences.

---

## 2. Interactive Screens & Behaviors

### 2.1 Navigation & History Drawer
- **Drawer Trigger**: Tapping menu hamburger icon on top bar slides in session drawer from the start edge.
- **Drawer Contents**:
  - User identity banner (avatar, display name, account status badge).
  - "New Chat" primary action button: Clears active feed, initializes fresh session ID, and resets conversation context.
  - Session List: Chronologically grouped conversation sessions (Today, Yesterday, Previous 7 Days, Older).
  - Tapping a session item loads its messages into the active timeline and closes drawer.
  - Long press on session item exposes options: `Rename`, `Export as Markdown`, `Delete`.
  - Bottom actions: Quick link to Settings sheet and system diagnostics.

### 2.2 Application Settings Sheet
- **Presentation**: Modal bottom sheet with scrollable categorized preference cards.
- **Sections**:
  1. **AI Agents & Providers**:
     - Active Provider selection (Antigravity Hub, Claude, Gemini, Custom API).
     - Model selection dropdown with capability indicators (Code, Vision, High-Context).
     - API Key management dialogs with obscured input and copy/paste validation.
  2. **Appearance & Interface**:
     - Theme mode toggle (Dark, Light, System Default).
     - Code block syntax highlighting style picker.
     - Font size adjustments (Small, Standard, Large).
  3. **Voice & Speech**:
     - Status indicator confirming active Kabir Voice authority (locked internally without user picker clutter).
     - Hands-free wake word toggle.
  4. **Editing Engine & Workspace**:
     - Default project directory selector.
     - Auto-save frequency interval slider.
     - Terminal shell binary override path.
  5. **About & Version**:
     - SAIF AI proprietary licensing notices, build stamp, and release notes link.

### 2.3 Custom Skills & Slash Command Engine
- **Skill Format**: Markdown documents with YAML frontmatter specifying:
  - `name`: Command slug identifier (e.g., `audit`, `refactor`, `explain`).
  - `description`: Short user-facing summary shown in autocomplete list.
  - `instructions`: System prompt augmentations injected into context on invocation.
- **Slash Overlay Flow**:
  - User typing `/` triggers floating popup above input bar.
  - Real-time search filters available skills and built-in actions.
  - Selecting a suggestion inserts `/command` token and formats prompt accordingly.

---

## 3. Storage & Persisted Keys
- `active_session_id`: UUID string of the ongoing chat session.
- `app_theme_mode`: `SYSTEM`, `LIGHT`, or `DARK`.
- `provider_active`: String identifier of the chosen provider.
- `skills_registry`: Persistent directory containing user-defined `.md` skill files.

---

## 4. Acceptance Criteria
- Creating a new session must instantly clear the view without freezing or database locks.
- Storing or updating an API key takes effect immediately without requiring application restart.
- Invalid or corrupt skill files must be reported with line error diagnostics rather than failing silent or crashing skill loader.
