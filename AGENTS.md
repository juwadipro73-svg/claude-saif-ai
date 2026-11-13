
-- This whole below prompt only applies to gemini flash models(for any non gemini model you do not have to follow this rule and follow what user said)
## 3. Communication & User Interaction Rules

- **Answer Questions First**: When the user asks a question, points out an issue, or argues a design point, ALWAYS answer and explain directly to the user in text first.
- **Do Not Stealth-Edit Files**: Never start modifying code in silence instead of answering the user's question or addressing their direct feedback.
- **Format Parity**: Both JSON and binary gRPC return the exact same protobuf schema and fields from the daemon; never assume or claim they return different keys or formats.
- **Only do what user has told you to do**: Never touch a single line that user has not told to do, if user said to do Task X then do that nothing else not even add a signle extra space somewhere, if need such things to chagne mroe than user has asked for, alwasy explain user that you want to do this too.

-- "Dont try building release build ever"
-- "Dont try git push pull commit, stash etc ever"

-- While running new command for build/etc always check for previous if ther is runnign smae thigns then either kill it then start new or wait for old (Dont run both);

-- Always check running process before exectuing new task run(so that you dont execute same task twice or more times)

-- Always explain user every single details that you are going to change to fix or add something(Dont need line by line but still enoough) and dont start editing, alwasy explain to user, and stop there, user will respond back with continue,do it or whatever or ask you more question,arguement

- "Dont do gralew install or any install relelated command(Only applies to gemini models)

- Never ever try to compile or assemble for Standard Build, alwasy for termuxBuild only

- If you are not 100% sure for someting how to do, never try to do, always ask user for direction this case without understanding what user want.

## Credits and licences: never remove them

SAIF AI is GPL-3.0-or-later and is built on other people's code (AntiGem by Santosh Kurmi, Termux, PRoot and more).
Rules for every agent and every change:

- Do not delete or reword away any name, copyright line or licence in `LICENSE`, `THIRD_PARTY_NOTICES.md`,
  `licenses/` or the settings footers. Do not claim that any third-party code is "100% owned" by SAIF AI.
- Do not change the project to a proprietary or "all rights reserved" licence. It contains GPL code.
- When you add a library or copy code, add it to `THIRD_PARTY_NOTICES.md` with its licence, then copy the file to
  `app/src/main/assets/legal/` (the unit test `LegalNoticesBundleTest` fails if the copies differ or a credit is gone).
- Only add code whose licence fits the GPL (GPL, LGPL, Apache-2.0, MIT, BSD and similar). Code under a licence
  with extra conditions (for example a compulsory credit in videos) must not be added.
- The CI build prints the libraries that are really packaged in the APK as check annotations named
  "APK libraries i/n" (last step of `.github/workflows/build-apk.yml`). When dependencies change, compare
  section 3 of `THIRD_PARTY_NOTICES.md` with that list.
- The assistant's spoken or typed answer to "who made you?" is the product persona (SAIF AI, see
  `SaifIdentity.RULE`). It is separate from these legal credits and must not be used as a reason to remove them.

### The live voice is SAIF AI's own code; the third-party `automation/` module is gone (2026-11-13)

Until build #10 the live voice conversation (purple mic) ran on a third-party module, `automation/` (Java package
`com.jarvis.assistant`, the "JARVIS AI Android assistant" by Tech GPTx, TAMCL v1.0). The owner had all of its code,
its licence and its credits removed and replaced by SAIF AI's own code, because that licence does not fit the GPL.

- Do not re-add the module and do not copy or adapt code from that project (github.com/techgptx/Playlist).
  `LegalNoticesBundleTest` fails when `com.jarvis.assistant`, `SaifAutomation` or `JarvisApp` is used again.
  Do not add credits or a licence file for it either: nothing of it is in the app.
- The own engine, all in `app/src/main/java/com/example/`:
  - `live/SaifLive.kt` - the state and the functions the screens use (start, stop, mute, interrupt, screen share).
  - `live/SaifLiveService.kt` - foreground service (microphone; also mediaProjection while the screen is shared),
    notification, wake lock, 30-minute idle end, memory learning after each turn.
  - `live/LiveTurnLoop.kt` - one conversation: listen, speech-to-text, answer (phone agent, coding bridge or the
    provider), text-to-speech, barge-in. It existed before and was only freed from the module's types.
  - `live/LiveScreenCapture.kt` - shared-screen pictures (reduced size, one per second, JPEG, never stored).
  - `live/LivePrompt.kt`, `live/LiveModels.kt` - the spoken-answer prompt and the shared types and rules.
  - `stt/OfflineSpeech.kt`, `stt/OfflineSpeechFiles.kt` - on-device speech recognition with the Vosk library
    (Apache-2.0); the model is downloaded once into `files/vosk-model`.
  - `ui/components/SaifLiveOrb.kt` - the orb, drawn with Compose Canvas, and the live conversation screen.
- Things the old module had that SAIF AI never showed, and that therefore were not rebuilt: its own settings
  screens, voice enrolment (owner voice lock), overlay avatars, its hands-free standby, its persona modes and its
  direct Gemini-audio connection. `SaifLiveSettingsSection.kt` was never called from any screen and was deleted.
- Not verified on a phone: the new code is compiled and unit-tested by CI only. Check on a device after changes:
  start and stop from the purple mic, barge-in, mute, screen share on Android 14+, a provider without
  speech-to-text (model download), and that the session ends when the app account signs out.
- Older text in this file and in `docs/ROUND_*_REPORT.md` that mentions `SaifAutomation`,
  `JarvisConversationService` or `orb.html` describes code that no longer exists.
