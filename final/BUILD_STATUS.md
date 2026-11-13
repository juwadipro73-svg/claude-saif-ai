# Build status (honest)

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
