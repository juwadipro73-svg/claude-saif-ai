# Specification: Chat Engine, Feed & Interactive Panels

## 1. Scope & Objective
This specification defines the interactive conversation experience: user input handling, streaming message consumption, markdown rendering with code block interactions, tool execution feedback, approval dialogues, and questionnaire prompts.

---

## 2. Interactive Screens & User Flows

### 2.1 Main Chat Timeline
- **Layout**:
  - Top header displaying active model identity, project name, and drawer trigger.
  - Scrollable conversational timeline automatically snapping to bottom on incoming streamed tokens unless user has manually scrolled upwards.
  - Docked floating bottom area housing tool approval cards or questionnaire panels when actions are pending.
  - Multi-line input bar with attachment picker, voice input trigger, slash command autocomplete, and send action.

### 2.2 User Message Flow
- When user enters text and taps Send:
  1. Input is validated (non-empty after trim or at least one attachment present).
  2. Input field immediately clears and dismisses virtual keyboard if configured.
  3. User message appears right-aligned in primary accent color container with attachment pills.
  4. Assistant response placeholder appears left-aligned with subtle loading spinner.
  5. Tokens append incrementally as received over the active backend stream.

### 2.3 Assistant Reasoning / Thoughts
- If the model emits thought tokens:
  - An expandable "Thinking Process" card appears above the main response body.
  - Shows elapsed thinking time once finished.
  - Defaults to collapsed after completion to minimize visual clutter, but remains expandable on demand.

### 2.4 Markdown Rendering & Syntax Highlights
- Standard headings `#`, `##`, `###` formatted with distinct typographic scale and weights.
- Unordered lists (`-` or `*`) rendered with bullet points and nested indents.
- Quotes (`>`) rendered with left accent border and italicized text.
- Inline code formatted in monospace font with shaded background capsule.
- Fenced code blocks (```language ... ```):
  - Rendered inside dark syntax container.
  - Top action bar showing detected language tag on left and clipboard copy icon button on right.
  - Tapping copy icon copies raw code text to system clipboard and displays confirmation toast.

### 2.5 Tool Approval Docked Panel
- When an agent requests a system operation (command execution, file write, or sensitive search):
  - If auto-approve is disabled, turn pauses and docked approval card slides up above input bar.
  - Displays:
    - Operation category badge and target path / command preview.
    - Context explanation explaining why the action was requested.
    - Two or three actions:
      * `Allow Once`: Executes the action once and resumes streaming.
      * `Always Allow`: Grants execution and whitelists identical operations for current session.
      * `Deny`: Rejects execution, prompting optional text input to explain rejection reason to the agent.

### 2.6 Interactive Questionnaire Card
- When an agent poses multiple-choice questions to guide the conversation:
  - Questionnaire panel displays the prompt and selectable option cards.
  - Supports single-select (radio behavior) or multi-select (checkbox behavior).
  - Includes "Other / Custom" option opening inline text entry.
  - Tapping `Submit` returns selected option labels to the agent as a structured response message.

---

## 3. Persisted Keys & States
- `chat_auto_scroll`: Boolean flag for locking scroll position to bottom.
- `chat_code_theme`: Identifier for syntax highlighting theme (e.g., dark vs light).
- `auto_approve_tools`: Boolean flag to bypass manual approval for non-destructive operations.

---

## 4. Acceptance Criteria
- Streaming messages must render at least 60 frames per second without stutter or recomposition churn of the entire message list.
- Tapping copy on any code block must always copy the exact original characters without markdown delimiters or formatting artifacts.
- When an approval is rejected, the agent context must receive a clear rejection signal to gracefully adjust its plan without throwing unhandled exceptions.
