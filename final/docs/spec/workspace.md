# Specification: Workspace Shell, File Manager & Git Integration

## 1. Scope & Objective
This specification describes the workspace file system management, project navigation sidebar, file operations, and integrated Git version control views.

---

## 2. Interactive Screens & User Flows

### 2.1 Workspace Hierarchy & Project Sidebar
- **Project Structure**:
  - Root directory located at `/root/workspace/` (or app sandboxed files dir on unrooted setups).
  - Each app or project resides in its own subfolder slug (e.g., `/root/workspace/my-app/`).
- **Sidebar Tree View**:
  - Expandable / collapsible directory nodes.
  - Distinct file icons based on extension (`.kt`, `.java`, `.xml`, `.html`, `.js`, `.css`, `.json`, `.md`).
  - Single tap opens file in Code Studio editor tab.
  - Context menu on long tap or chevron:
    * `New File`, `New Directory`, `Rename`, `Delete`, `Copy Path`.

### 2.2 Project Switcher & Creator
- **Switching Projects**:
  - Dropdown on header allows switching active workspace root between projects.
  - Changing project notifies IDE, terminal, and agent context to refresh their working directories.
- **New Project Sheet**:
  - Prompts for project name, package name, and initial template (Android Native Kotlin, Multi-file Web HTML5/JS, Python CLI).
  - Scaffolds initial file tree upon confirmation.

### 2.3 Integrated Git View
- **Repository Status Panel**:
  - Current active branch name display with branch switching dropdown.
  - Staged and unstaged file lists with modification badges (`M` modified, `A` added, `D` deleted, `U` untracked).
- **Diff Viewer**:
  - Tapping a modified file opens split or unified diff viewer.
  - Additions highlighted in green (`+`), deletions in red (`-`).
- **Commit & Sync Workflow**:
  - Commit message text box with character counter.
  - `Commit`: Stages changes and creates Git commit using permissive Git client (e.g., JGit / libgit2 wrapper).
  - `Pull` and `Push`: Synchronizes branch with configured remote URL using user credentials.

---

## 3. Persistence & Safety Constraints
- **File Access Boundary**:
  - All file manager operations are strictly confined within the designated workspace root.
  - Any path traversal containing `..` that resolves outside workspace root must immediately throw a security denial exception.
- **Concurrent Write Guard**:
  - File saving acquires an atomic file lock to prevent concurrent overwrites by agent tools while user is typing.

---

## 4. Acceptance Criteria
- File tree loads recursively with sub-second latency for directories with up to 5,000 files.
- Git diff viewer correctly handles UTF-8 files with CRLF or LF line endings.
- Deleting a non-empty directory requires explicit user confirmation dialog.
