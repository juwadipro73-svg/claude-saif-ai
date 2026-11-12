# Specification: Code Studio & Text Editor

## 1. Scope & Objective
This specification defines the multi-tab code editor, syntax tokenization, line numbering, file dirty-state tracking, and seamless switching between source code, terminal, and live previews.

---

## 2. Interactive Screens & Behaviors

### 2.1 Tab Management Bar
- Horizontally scrollable row displaying all currently opened files.
- Each tab shows:
  - File name icon based on extension.
  - File display name.
  - Unsaved modifications indicator (subtle dot or color tint).
  - Close button (`x`) with save prompt if unsaved changes exist.
- Plus button on tab strip to quickly create a new untitled buffer.

### 2.2 Editor Body & Typography
- **Monospace Font**: Clean, modern monospace typography optimized for mobile code readability.
- **Line Numbers Gutter**:
  - Left-aligned gutter displaying 1-indexed line numbers.
  - Highlights current active line number with accent color.
- **Syntax Tokenization**:
  - Highlights keywords, strings, comments, numbers, and identifiers for standard formats: Kotlin, Java, XML, JSON, HTML, CSS, JavaScript, Bash, Python, Markdown.
  - Theme colors adapt smoothly to light and dark modes.

### 2.3 Editor Actions Bar
- Floating or docked toolbar offering essential coding shortcuts:
  - `Save` (`Ctrl+S` equivalent): Writes buffer contents atomically to storage and clears dirty indicator.
  - `Undo` / `Redo`: Standard stack-based editing history.
  - `Search & Replace`: Floating search bar with case-sensitivity and regex options.
  - `Switch to Terminal`: Instantly transitions to terminal view keeping current directory synchronized.
  - `Run / Live Preview`: Dispatches build or opens research browser pointing to local server output.

### 2.4 External Change Detection
- If an agent tool modifies a file currently loaded in the editor:
  - If editor has no unsaved changes, automatically reloads the buffer to show latest edits.
  - If editor has unsaved changes, displays conflict banner: "File modified externally. [Reload from disk] or [Keep my changes]".

---

## 3. Performance & Memory Requirements
- Large files (up to 20,000 lines) must render using viewport virtualization (only composing visible lines).
- Keystroke latency must remain under 16 milliseconds to guarantee responsive 60fps typing.

---

## 4. Acceptance Criteria
- Saving a file never results in partial or truncated disk writes; always write to temporary file before atomic rename.
- Virtual keyboard appearance must adjust editor viewport height so active editing cursor remains clearly visible.
