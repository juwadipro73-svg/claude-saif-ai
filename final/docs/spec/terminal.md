# Specification: Terminal Execution & Process Virtualization

## 1. Scope & Objective
This specification defines the cleanroom terminal subsystem for running shell sessions, command execution pipelines, streaming process outputs, and ANSI escape code handling without any dependency on external daemon processes or copyleft binaries.

---

## 2. Architecture & Execution Engine

### 2.1 Pure Kotlin / Java Process Virtualization
- **Direct Process Management**:
  - Spawns subprocesses directly via standard Java `ProcessBuilder`.
  - Configures shell environment (`PATH`, `HOME`, `LANG=C.UTF-8`, `TMPDIR`, project working directory).
  - Binds standard input, standard output, and standard error streams asynchronously.
- **Process Lifecycle States**:
  - `IDLE`: Session initialized, waiting for command.
  - `STARTING`: Spawning shell executable (`/system/bin/sh` or `/data/data/.../files/usr/bin/bash`).
  - `RUNNING`: Active process running, capturing standard output lines in non-blocking background coroutine.
  - `EXITED`: Process terminated with recorded exit code.
  - `TERMINATED`: Process forcefully killed by user request or timeout.

### 2.2 Terminal User Interface
- **Screen Buffer**:
  - Fixed-width/height terminal grid rendering terminal characters using monospace font.
  - Supports ANSI color escape sequences (16 basic ANSI colors + 256 color palette).
  - Auto-scrolls to latest terminal output line on incoming data.
- **Virtual Control Keys**:
  - Horizontal keyboard accessory bar providing mobile keys: `Esc`, `Tab`, `Ctrl`, `Alt`, `Up`, `Down`, `Left`, `Right`, `|`, `-`, `/`.
  - Tapping `Ctrl` toggles sticky state so next keypress sends ASCII control code (e.g., `Ctrl+C` sends `\x03` to interrupt process).

### 2.3 Non-Interactive Command Execution (Agent Tool Engine)
- Background execution pipeline for agent `bash` or system commands:
  - Takes command string, working directory, and timeout duration.
  - Captures up to 500 KB of stdout and stderr into memory buffers without memory leaks.
  - Automatically terminates child process trees if execution exceeds specified timeout (default 30 seconds).
  - Returns structured outcome: `exitCode: Int`, `standardOutput: String`, `standardError: String`, `durationMs: Long`.

---

## 3. Security & Isolation Safeguards
- **Environment Isolation**: Subprocess working directory defaults strictly to active project root.
- **Command Sanitization**: Dangerous system binary operations attempting to access Android system private partitions are rejected before fork execution.

---

## 4. Acceptance Criteria
- Commands such as `ls`, `pwd`, `git`, `python3`, `node` execute reliably and stream output line by line without buffering stalls.
- Pressing `Ctrl+C` reliably kills the running foreground subprocess without terminating the parent application process.
- All process streams (stdin, stdout, stderr) are closed deterministically upon process exit to prevent file descriptor leaks.
