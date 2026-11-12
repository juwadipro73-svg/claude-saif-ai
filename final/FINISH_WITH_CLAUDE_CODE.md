# Finish the SAIF AI build (paste this into Claude Code on a machine with the Android SDK)

You are an autonomous senior Android/Kotlin engineer. Goal: make this repository compile, run and keep the features of the original
SAIF AI app, using only original SAIF AI code plus permissively licensed libraries. Never copy third-party code.

## Facts (from static analysis, nothing here has been compiled yet)
- The old AntiGem and JARVIS code is gone. `com.example.saif.*` holds only small placeholder classes, so ~23 files that call them do not compile.
- `ui/ChatViewModel.kt` was written for an Antigravity "hub" backend (conversation/cascade API, quotas, model list). That hub client does not exist.
  Do NOT recreate it. Route chat through `AntigravityCustomApiAgentEngine`, `LlmGateway` and `SaifUniversalAgentBackend` (user API keys) and remove hub-only branches.
- Chat feed UI (`ui/HomeAgentFeed.kt`, `ui/HomeAgentTimeline.kt`) needs composables that no longer exist (for example `MarkdownBlockView`, user and assistant bubbles,
  tool cards, question panel, approval panel, `SlashCommandOverlay`). Build them in `com.example.feature.chat` or reuse `saif/ui/SaifChatEngine.kt` (`SaifMessageFeed`, `SaifAgentApprovalPanel`).
- Symbols referenced but not defined (non-test):
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

## Tasks, in order
1. Run `./gradlew :app:assembleDebug` and fix errors until it is green. After each batch of fixes, rerun.
2. Replace every placeholder in `com.example.saif.*` with real SAIF-owned code or delete the feature that needs it.
3. Rewrite `ui/ChatViewModel.kt` so it depends only on SAIF-owned interfaces (`AgentBackend`, `SaifCorePorts`), with no hub types.
4. Update or delete unit tests that reference removed classes, then run `./gradlew test`.
5. Proto/Wire: delete `app/src/main/proto`, `proto_generator` and the Wire dependency if nothing real uses them.
6. Optional replacements: swap Rosemoe sora-editor (LGPL) for a permissively licensed editor if desired.
7. Leave in place with notices: Apache-2.0 / MIT / BSD libraries and the glibc archive (LGPL: keep the notice and a source offer, or remove the archive).
8. Remove `saif-debug.jks` and `google-services.json` from version control; document how to supply them locally.
9. Keep LICENSE and THIRD_PARTY_NOTICES.md accurate; do not claim anything that is not true.

## Gates before any release
- Build and unit tests green; app launches and a chat with a user API key works end to end.
- scripts/similarity_check.py (or an equivalent line-hash check) against the old code stays below 8% overall and 15% per file.
- A person independent of the AI reviews old vs new code for similarity.
