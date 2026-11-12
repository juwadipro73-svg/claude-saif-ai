# Specification: In-App Research Browser & Local Bridge Server

## 1. Scope & Objective
This specification defines the integrated research and preview browser tab and the pure in-process local HTTP daemon providing asset delivery, file downloads, and localhost inspection without any external Go binary dependencies.

---

## 2. In-Process Local Bridge Server Architecture

### 2.1 Pure Kotlin HTTP Socket Daemon
- **Network Binding**:
  - Bound strictly to loopback interface `127.0.0.1` (never exposed to external WiFi or mobile network interfaces).
  - Port assignment: dynamically allocated or configured to default port (e.g. 8767).
- **Core Endpoints**:
  1. `GET /status`: Returns JSON health report confirming server readiness and active capabilities.
  2. `GET /downloads/{token}/{filename}`: Securely serves registered artifacts (APKs, ZIPs, logs) with proper `Content-Type` headers and octet-stream MIME handling.
  3. `POST /api/exec`: Accepts structured command execution requests for local agent bridge.
- **Security Token Validation**:
  - All modifying endpoints require an authentication token (`X-Saif-Token` header or query parameter) generated securely per app session using `SecureRandom`.
  - Rejects any requests from origins other than `localhost` or `127.0.0.1`.

---

## 3. In-App Research Browser View

### 3.1 WebView Architecture
- Embedded native Android `WebView` configured with modern web standards:
  - JavaScript enabled with secure DOM storage.
  - Custom `WebChromeClient` providing progress bar and title updates.
  - Custom `WebViewClient` intercepting navigation, error codes, and SSL policies.
- **Navigation Controls**:
  - Address bar displaying current URL with reload and clear actions.
  - Back, Forward, and Home buttons.
  - Direct shortcut buttons: `Localhost:3000`, `Localhost:8080`, `Dev Preview`.
  - "Inspect Console" sheet showing captured JavaScript `console.log`, `console.warn`, and `console.error` entries.

### 3.2 Bidirectional JavaScript Bridge
- Exposes safe `@JavascriptInterface` object `window.SaifBridge`:
  - `postMessage(jsonStr: String)`: Allows previewed web apps to send events back to SAIF AI.
  - `copyText(text: String)`: Allows web views to trigger native clipboard copies safely.

---

## 4. Acceptance Criteria
- Bridge server starts in under 100ms and utilizes zero background CPU while idle.
- Serving a 50MB file over `127.0.0.1` achieves transfer speeds exceeding 50 MB/s without high heap garbage collection pauses.
- Navigating to unresolvable URLs displays a clean, user-friendly error card with "Retry" button rather than a blank screen.
