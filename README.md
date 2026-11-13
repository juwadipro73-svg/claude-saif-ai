# SAIF AI

A native Android AI workspace: chat with your own API key (or, optionally, Google Antigravity), live voice, code editing and project creation. APK/video build requests now go through a **separately deployable cloud template**; this repository does not contain a live production builder or a configured downloads domain.

**Licence:** SAIF AI is free software under the **GNU GPL v3 or later**. It is built on [AntiGem by Santosh Kurmi](https://github.com/Santoshkurmi/antigem) (GPLv3). Everyone whose code is used is credited in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), and in the app under Settings > Open-source licences & credits.

**Build status:** GitHub Actions produces a **debug preview APK**, not a production-signed release. Real-device sign-in, Play Integrity, Firebase RTDB, private Drive delivery and the separately deployed builder must be verified with the owner's accounts before cloud jobs can work.

## App behavior

- Startup uses the restored **email/password sign-in, sign-up, forgot-password and guest** screen. Firebase Email/Password accounts can request Firebase password-reset email. If Firebase is unavailable, a device-local account can be created; it is **not proof of email ownership** and cannot be remotely reset. Local passwords are PBKDF2-hashed with salt; older local SHA-256 rows are upgraded after successful sign-in. Firebase account passwords are not cached for offline fallback, so a Firebase password reset cannot be bypassed by an old device-local copy. Guest data is scoped to that guest session.
- **Your own API key is enough; Antigravity is optional.** After the app sign-in the chat opens directly (there is no "choose your provider" screen). Add an API key (OpenRouter, Gemini, OpenAI, Anthropic, Groq and more) and chat: no Antigravity sign-in is asked for, and no Antigravity account screen is shown while your key is in use. A key already saved in the AI provider settings is used automatically when Antigravity is not connected.
- **Storage clean-up.** Work in the Linux workspace downloads gigabytes of modules. When a project has been idle (30 minutes by default, changeable in Settings > Storage), re-downloadable folders are deleted: `node_modules`, Python virtual environments, Gradle build intermediates, and the npm, pip, Gradle, Maven and apt caches. Project files, `.git`, `.env` files and finished build outputs are never deleted, and projects with a running program are skipped. Returning to a project only needs its modules installed again (`npm install`, a new build); the agent is told to do that by itself. A log is kept at `/root/workspace/.config/saif-cleanup.md`.
- **Antigravity authentication is separate, inside SAIF AI.** The app login is not a Google OAuth identity and does not silently authorize Antigravity. To use Antigravity, connect its hub in the app. An old hub token is not silently reassigned to a different app account; reconnect Antigravity after switching SAIF AI accounts. Saved chats, project-chat histories, personal memory/PIN and imported GitHub skills are scoped to the active app owner. New memory PINs are salted PBKDF2 hashes (legacy PINs upgrade after a correct unlock), but this is a UI lock, **not encryption**. Unowned pre-migration data remains hidden rather than assigned to the next login. Project files and the shared local terminal environment are **not encrypted or securely isolated** across users of the same Android device.
- On app sign-in the local development environment and bridge start automatically if needed; progress/retry are in Settings. The standard package (`com.saifai.app`) uses ARM64 Ubuntu/PRoot; the classic terminal launcher style does not change the environment into a native Termux rootfs.
- The purple mic starts a **live voice conversation**. It is SAIF AI's own engine (`com.example.live`): it needs only the microphone permission and something that can answer (your provider API key, or the connected chat agent). It works with any provider: OpenAI, Groq and Gemini keys transcribe speech in the cloud, every other provider uses the on-device speech model (English, downloaded once on first use). Screen sharing is optional and is asked for inside the conversation. Notification permission and battery exemption are not startup requirements. Spoken coding tasks go to the same chat agent as typed ones. Tool approvals still require the user. A public GitHub repository/folder/SKILL.md import uses your chosen /command name and saves its skill text, references and scripts as one app-private Markdown document (up to 256 KB), scoped to the app account. Binary assets are listed by path, not embedded in the chat prompt; review untrusted skills before use.
- Chat keeps small text/photos under 3 MB inside the app. Attaching an APK, ZIP or video (or a supported >=3 MB photo/document) starts **one private website upload**, not an unsupported Antigravity binary attachment: stream to signed GCS, verify checksum, save to private Drive, then display a one-time download link in SAIF AI. Prompted project ZIPs can also be generated in the cloud. These features require the authenticated gateway, website routing, App Check and builder deployment in [cloud/README.md](cloud/README.md); the supplied demo URL is not verified as a live upload API. The app does not secretly send attached binary bytes to an AI model.
- Chat text, code snippets and **small images/text attachments (3 MB combined max)** stay in the app. Explicit APK/game compilation and MP4 renders are routed to `SaifAiCloudManager` and an authenticated backend. Project Studio and Video Studio build/export buttons no longer invoke the local APK compiler or full video renderer. A job card listens to Firebase RTDB and shows a private **SAIF AI DOWNLOADS** button when an artifact is ready. Cloud builds require separate service deployment; an unconfigured debug build fails closed and tells you what is missing.

## Cloud/Drive/Firebase setup

See **[cloud/README.md](cloud/README.md)** for the complete backend, builder workflow, Firebase RTDB rules, GCS input staging, private Drive delivery, sponsor-placeholder website, one-time download claim, deployment secrets and known template limitations. The separate public `SaifAI-Builder` repository is readable, but its draft PR #2 still uses placeholder artifacts and is **not** merged, modified from this checkout, or deployed; see the concrete contract gaps in [cloud/README.md](cloud/README.md).

The checked-in `app/google-services.json` currently has no OAuth clients. **Google startup sign-in is not required** for this restored login; enable Firebase **Email/Password** and **Anonymous** providers plus appropriate Firebase database/security rules if you want cloud jobs. App Check / Play Integrity for a sideloaded debug APK may require additional private test setup; never publish a debug App Check token. Configure a signed production build for deployment. Keep the Firebase project owner's Firestore rules for admin role, bans and quotas secure—client UI alone cannot establish authority.

## Build and download

A push starts `.github/workflows/build-apk.yml`. It first runs the cloud-template tests, then compiles the Android app and runs its unit tests. A successful build uploads an [Actions APK artifact](https://github.com/juwadipro73-svg/claude-saif-ai/actions/workflows/build-apk.yml) and updates the [rolling debug prerelease](https://github.com/juwadipro73-svg/claude-saif-ai/releases/tag/saif-ai-apk). **Check the run's commit SHA and conclusion before installing**; an earlier release or artifact is not proof that later changes passed. The cloud URL variables may be empty for a sign-in/UI preview, but cloud jobs then cannot run. If the three public origins are set as this repository's Actions variables (or existing secrets with the same names), CI safely probes the gateway and portal routes before publishing the APK; a demo website or Google-login redirect cannot pass that preflight. This does not replace a real upload/device test.

For a local build install JDK 17, Android SDK/Build Tools, Go 1.22 and Gradle 9.x:

```bash
# Only AFTER deploying the backend/portal/RTDB (public HTTPS addresses, not secrets):
export SAIF_CLOUD_API_BASE_URL='https://YOUR-GATEWAY'
export SAIF_DOWNLOAD_PORTAL_URL='https://YOUR-GATEWAY'
export SAIF_FIREBASE_DATABASE_URL='https://YOUR-RTDB.firebasedatabase.app'
./gradlew :app:assembleStandardDebug :app:testStandardDebugUnitTest
```

Output: `app/build/outputs/apk/standard/debug/app-standard-debug.apk`. A committed debug keystore supports consistent install-over-debug updates, but is **not** a production signing key. Use your own private release key, an appropriate target SDK and real-device QA before shipping.

## Licence, credits and source

SAIF AI is free software: you may use, change and share it under the **GNU General Public License, version 3 or (at your option) any later version** ([LICENSE](LICENSE)). It comes with no warranty.

- Copyright (C) 2026 SAIF AI and contributors.
- The AI-agent workspace is based on [AntiGem by Santosh Kurmi](https://github.com/Santoshkurmi/antigem), Copyright (C) 2026 Santosh Kurmi, GPL v3 or later.
- Termux terminal components, PRoot, talloc, the GNU C Library, sora-editor and every other library are listed with their licences in **[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)**; the licence texts are in [`licenses/`](licenses).

When you share the app: keep these files with it, make the source of that exact version available to everyone who gets the APK (publishing from this public repository does that), and do not put a closed-source or "all rights reserved" licence on it. The checklist and the points that are still open are in sections 7 and 8 of THIRD_PARTY_NOTICES.md.

SAIF AI is an independent project and is not affiliated with Google, Anthropic, OpenAI, OpenCode, Arena, Termux or AntiGem. Their names are used only to say which tools the app works with. The repository owner controls this fork's code, builds and release settings; that does not transfer third-party copyright or Firebase/Google/Antigravity account ownership.
