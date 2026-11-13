# Build status (honest)

## Successful native build — 2026-11-13

Branch: `arena/9a04a45b-claude-saif-ai`. APK source commit: `eae283b021b1ac082d4f9617a86a59980d5ffa39`.

GitHub Actions run #21 (`:app:assembleStandardDebug`) **succeeded**:
https://github.com/juwadipro73-svg/claude-saif-ai/actions/runs/43434716994

**Direct APK download (debug preview, GitHub pre-release):**
https://github.com/juwadipro73-svg/claude-saif-ai/releases/download/saif-ai-native-b21/SAIF-AI-native-debug-b21.apk

- File: `SAIF-AI-native-debug-b21.apk`, 48,869,910 bytes.
- SHA-256: `8ae24d7892416790e3d78f91399a9575f262eae8c8cf5c38774b5ed3fa397843`
- `apksigner verify` passed in CI before publishing. Signed with the repository's fixed debug key, so it
  can update earlier debug builds signed with the same key. This is not a production signing identity.
- Version: `1.0.21-b21`, versionCode 1021. Package `com.saifai.app`.
- Release page (also offers GitHub's exact source ZIP for this tag):
  https://github.com/juwadipro73-svg/claude-saif-ai/releases/tag/saif-ai-native-b21

Kotlin compiler diagnostics across builds: 604 → 469 → 57 → **0**.

What changed to reach a green build:
- Main chat and in-app helper requests now go through the native Custom API engine (user-configured
  provider profile). The removed third-party hub engine, generation polling, hub sign-in and fake quotas
  are no longer used; quotas are shown as unavailable rather than invented.
- Home feed, slash-command overlay, provider settings/configuration dialogs and tool approve/deny/stop/retry
  UI were rebuilt on native state.
- Skills: real per-account GitHub import. An import succeeds only after the actual `SKILL.md` (repository
  root) or `/blob/<ref>/<path>` file is fetched over HTTPS from the GitHub contents API (2 MB response /
  1 MB document limits). Skills are stored per signed-in account and update the UI reactively.
- Voice "normal" mode now starts the actual LiveVoiceManager instead of speaking a canned readiness message.
- Remaining compile errors fixed: malformed string literals, missing commas, Java named-argument calls,
  icon/receiver mismatches, Context/Application mismatches, nullable state, exception variable names,
  and stale data contracts. No compile error was silenced by adding fake success stubs.
- CI publishes a signature-verified APK release on every successful build of this branch, and publishes full
  compiler diagnostics as check runs when a build fails.

Not verified / remaining limitations:
- No unit, instrumentation, emulator or device tests were run. Launch, Room migrations from older installs,
  notifications, foreground-task survival and provider compatibility still need testing on a phone.
- This is a debug APK. R8 shrinking/obfuscation applies only to release builds, which require a private
  signing key (`SAIF_RELEASE_*` environment variables). R8 does not make an app impossible to reverse engineer.
- Configure a Custom API provider in AI Settings before chatting; hub accounts/quotas are not available.
- The local bridge only prepares its workspace. A Linux CLI toolchain installer is not implemented.
- Helper image requests are rejected with a clear message; main chat handles attachments directly.
- A separate universal backend fallback outside the main chat still returns fixed responses and is not
  used by main chat.
- Unit tests under `app/src/test` still reference removed classes; this build does not compile them.
- Still relevant from earlier audits: `assets/glibc-min-arm64.tar` (LGPL) needs its notice/source offer;
  `saif-debug.jks` and `google-services.json` are in this public repository; the `.proto` files describe
  a third-party language-server API and should be reviewed before distribution.

---

## Historical notes (superseded by the successful build above)

## Active branch compilation follow-up — 2026-11-13

Branch: `arena/a95718fa-claude-saif-ai`. Latest compiled source: `76b97c8`.

**Build still fails; there is no APK artifact.**
Latest run: https://github.com/juwadipro73-svg/claude-saif-ai/actions/runs/43430575836

Two new GitHub builds ran in this continuation. The first reported 604 Kotlin diagnostics;
the second reports 469 diagnostics across 24 files (including cascading errors). No tests ran.

Completed in this continuation:
- Repaired exception-variable references and malformed Material color references.
- Consolidated the duplicate native chat models through shared domain contracts; serializable
  messages/tools/attachments now match the custom API engine, and transcript state is mutable.
- Fixed speech-router scope, native markdown loop variables, icon references and edge-to-edge invocation.
- Added the attachment URI field with a preserving Room 6-to-7 migration.
- Added a real bounded, cancellable native shell command implementation with concurrent output draining.
- Removed false Linux-ready answers. This source does not implement a Linux installer, so CLI-provider
  setup reports that limitation rather than claiming an installation succeeded.
- Updated the CI trigger to support active Arena branches and report diagnostic counts by file.

Remaining diagnostics are concentrated in old hub-dependent consumers:
- ChatViewModel: 137; AntigravityLlmBridge: 37.
- HomeAgentFeed: 41; SaifAiApp: 46; SaifEngineScreens: 67.
- SaifSkillsEngine: 49; SaifLocalBridgeServer: 18.
- Other settings, provider, media and notification components: 74 combined.

Required next architectural step: replace stale hub-only chat/auth/quotas/feed contracts with the
native custom-API flow, preserving account isolation, tool approvals and stored history. Do not add
fake successful hub/network/installer stubs to silence the compiler. Then repair the remaining native
skills, bridge, settings and utility call sites and rerun compilation. APK publication is still blocked.


## GitHub Actions build attempt — 2026-11-13

An actual `:app:assembleStandardDebug` build was attempted on the current Arena branch, using GitHub's Ubuntu runner, JDK 21 and preinstalled Android SDK. It **failed compilation; no APK was generated**. No unit/device tests ran.

Latest compilation run: https://github.com/juwadipro73-svg/claude-saif-ai/actions/runs/43423607528
Source commit: `3abe3b4`.

- Initial SDK setup download failed; workflow now uses the runner's preinstalled SDK.
- Room/KSP found missing `ownerUid` and `isBookmarked` entity columns. Restored those fields, session mode and UUID defaults, with a version 5-to-6 preserving migration. The next run proceeded to Kotlin compilation.
- Kotlin compilation then failed on incompatible backend/domain adapters: missing `PendingToolApproval`, missing ChatMessage/ToolCall constructor fields, immutable state where callers require mutable state, and LocalEnvironmentManager command signature/result mismatches.
- Numerous pre-existing exception handlers refer to `e` despite declaring `err`. CloudBuildStatusCard also contains `errorMsgMsg`/`errorMsg` and invalid `colorScheme.errorMsg` references.
- The run includes build-log artifacts and compiler-error check annotations. Those are diagnostic artifacts, **not an APK download**.

Next required work: restore compatible real backend/domain implementations, fix compiler errors across the app, rerun the build, then validate installation and task execution. Do not disable account ownership checks or replace core behavior with no-op stubs just to get a green build.

The older static inventory below is retained as context; it is not a claim that the project builds.

## Fixed in this pass
- Removed the dead `:automation` module dependency from app/build.gradle.kts.
- Restored SAIF's own `builder` package (15 files) and the `AgentEnv` / `Skill` interface.
- Removed Termux components, icons and permissions from the manifest and resources.
- Removed calls to deleted classes in SaifApplication, MainActivity and SaifAiApp.
- Replaced LICENSE and THIRD_PARTY_NOTICES.md with an accurate SAIF AI proprietary licence and real dependency list.

## Still unresolved (these symbols are referenced but not defined)
  com.example.saif.data.remote.SkillBundle  (3 files: PostBuild98FixesVerificationTest.kt, SavedSkillBundleStorageTest.kt)
  com.example.saif.data.remote.SkillBundleFile  (3 files: PostBuild98FixesVerificationTest.kt, SavedSkillBundleStorageTest.kt)
  com.example.saif.data.remote.SavedAiSkill  (2 files: PostBuild98FixesVerificationTest.kt, SavedSkillBundleStorageTest.kt)
  com.example.saif.data.remote.GithubSkillLinks  (2 files: SavedAiSkillsTest.kt, SkillBundleTest.kt)
  com.example.saif.data.remote.GithubSkillSource  (2 files: SavedAiSkillsTest.kt, SkillBundleTest.kt)
  com.example.saif.ui.browser.BrowserExtensionManager  (2 files: ChatViewModel.kt, PromptExpansionTest.kt)
  com.example.saif.data.local.LocalServerManager  (2 files: SaifAiApp.kt, SaifEngineScreens.kt)
  com.example.saif.ui.chat.PendingToolApproval  (2 files: AntigravityCustomApiAgentEngine.kt, ChatViewModel.kt)
  com.example.saif.MainActivity  (1 files: PostBuild98FixesVerificationTest.kt)
  com.example.saif.data.remote.GithubSkillMetadata  (1 files: SavedAiSkillsTest.kt)
  com.example.saif.data.preferences.AuthPreferences  (1 files: BackgroundLinuxSetupTest.kt)
  com.example.saif.data.DataRepository  (1 files: MainScreenViewModelTest.kt)
  com.example.saif.ui.browser.BrowserExtension  (1 files: PromptExpansionTest.kt)
  com.example.saif.ui.browser.ExtensionSkill  (1 files: PromptExpansionTest.kt)
  com.example.saif.ui.components.MarkdownBlock  (1 files: TrajectoryErrorIngestionTest.kt)
  com.example.saif.ui.main.MainScreen  (1 files: MainScreenTest.kt)
  com.example.saif.ui.agents.AgentApprovalPanel  (1 files: SaifAiApp.kt)
  com.example.saif.ui.agents.AgentQuestionPanel  (1 files: SaifAiApp.kt)
  com.example.saif.ui.settings.EmbeddedEngineSettings  (1 files: SaifAiApp.kt)
  com.example.saif.domain.model.AiModel  (1 files: ChatViewModel.kt)
  com.example.saif.data.daemon.IdeApiClient  (1 files: ChatViewModel.kt)
  com.example.saif.ui.browser.BrowserSessionManager  (1 files: ChatViewModel.kt)
  com.example.saif.data.remote.dto.AskQuestionResponseItemDto  (1 files: ChatViewModel.kt)
  com.example.saif.ui.chat.SlashCommandOverlay  (1 files: SaifInputBar.kt)
  com.example.saif.ui.chat.ChatViewModel  (1 files: SaifEngineScreens.kt)
  com.saifai.sentinel.STATUS_CHANGED  (1 files: SaifSecuritySentinel.kt)

## Known functional gaps
- The Antigravity hub integration (`com.example.saif.*`) is a thin set of placeholder classes. Hub sign-in, model list, quotas and
  agent approval panels do not do real work. `ui/ChatViewModel.kt` expects a richer backend API than these placeholders provide.
- Several screens from the old workspace (full chat screen, git view, IDE-style editor tabs) are not present.
- `assets/glibc-min-arm64.tar` bundles GNU C Library files (LGPL). Keep the notice and provide the source offer, or remove the archive.
- `saif-debug.jks` and `google-services.json` are in the repository. Use a private release keystore and do not publish signing keys.
- Many unit tests under app/src/test reference removed classes and will not compile until they are updated or deleted.
- `app/src/main/proto/*.proto` and `proto_generator/protos/*.proto` describe the Antigravity language-server API (they contain Google model names).
  Confirm you have the right to ship them, or remove them together with the code generated from them.
- Two Google proto enum names contain the word "JARVIS". They are Google model identifiers, not the removed JARVIS assistant project.
