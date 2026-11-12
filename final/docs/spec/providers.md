# Specification: AI Provider Backends & Network Transport

## 1. Scope & Objective
This specification defines the communication protocols, authentication handshakes, payload formatting, streaming token processing, and error handling for all supported AI backends behind the unified `AgentBackend` port.

---

## 2. Provider Protocols & Behaviors

### 2.1 Antigravity Hub Provider
- **Transport**:
  - Local HTTP server negotiation over `127.0.0.1`.
  - Bidirectional WebSocket connection for real-time turn events and tool dispatching.
- **Handshake Sequence**:
  1. Verifies local hub daemon is responding on designated port.
  2. Transmits authentication bearer token stored in secure preferences.
  3. Receives session authorization and model list.
- **Payload Streaming**:
  - Handles incoming frames: `content_chunk`, `thought_chunk`, `tool_call_request`, `tool_call_result`, `turn_complete`, `error`.
  - When `tool_call_request` arrives, maps to domain `ToolCall` and evaluates auto-approve policy.
- **Resilience**:
  - Automatically reconnects WebSocket on unexpected termination with exponential backoff (1s, 2s, 4s, up to 15s).

### 2.2 Claude Provider
- **Transport**:
  - Process execution via local CLI bridge or direct Anthropic Messages API over HTTPS.
- **Authentication**:
  - API Key stored securely in encrypted app preferences or OAuth token exchange.
- **Message Protocol**:
  - Outgoing JSON payload conforming to Anthropic Messages endpoint specification (`model`, `messages`, `system`, `tools`, `stream: true`).
  - Server-Sent Events (SSE) streaming parser:
    * `message_start`: Initializes turn state.
    * `content_block_delta`: Emits incremental text or thinking tokens.
    * `tool_use`: Captures requested tool invocation and argument dictionary.
    * `message_stop`: Finalizes turn and records token usage statistics.

### 2.3 Gemini Direct API Provider
- **Transport**:
  - HTTPS POST to Google Generative Language endpoints (`generateContent?alt=sse`).
- **Authentication**:
  - API Key queried from encrypted settings or rotation pool.
- **Message Protocol**:
  - Request payload structure containing `contents` (role and parts), `systemInstruction`, and `tools` (function declarations).
  - SSE stream emits chunks containing candidate parts: text deltas and function calls.
  - Automatically executes approved function calls and posts `functionResponse` back in a follow-up request.

### 2.4 Custom API Provider (OpenAI Compatible)
- **Transport**:
  - User-configurable endpoint URL (`https://.../v1/chat/completions`).
- **Authentication**:
  - User-supplied API key transmitted in `Authorization: Bearer <key>` header.
- **Protocol**:
  - Standard JSON chat completions payload with `stream: true`.
  - SSE streaming parsing `choices[0].delta.content` and `choices[0].delta.tool_calls`.

---

## 3. Error Handling & Edge Cases
- **HTTP 401 / 403 (Authentication Error)**: Emits user-visible authorization notification and flags provider credentials as expired.
- **HTTP 429 (Rate Limit / Quota Exceeded)**: Parses `Retry-After` header if present; alerts user and offers automatic retry after backoff.
- **Network Disconnection**: Detects lack of internet connectivity, pauses transmission, and queues unsent prompts for retry upon network restoration.
- **Payload Malformation**: Emits sanitized diagnostic message to conversational stream without crashing UI thread.

---

## 4. Acceptance Criteria
- Switching between providers from settings must not drop existing conversation messages.
- Streaming latency from first network byte to UI display must remain below 50 milliseconds.
- Stored credentials must never be written to application logs, crash reports, or unencrypted shared preferences.
