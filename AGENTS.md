
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

### The `automation/` module was removed (2026-11-12)

The live voice and phone-automation module `automation/` (Java package `com.jarvis.assistant`) was the "JARVIS AI
Android assistant" by Tech GPTx (github.com/techgptx/Playlist) under its TAMCL v1.0 licence. The owner had it
removed completely, with its licence file and its credits, because that licence does not fit the GPL.

- Do not re-add the module and do not copy code from that project. `LegalNoticesBundleTest` fails when
  `com.jarvis.assistant` or `SaifAutomation` is used again.
- Removed with it: the purple live-voice button and the 3D orb, screen sharing inside a live session, voice
  enrolment, the overlay avatar, the hands-free settings and the on-device Vosk speech fallback (`SttRouter` now
  needs an OpenAI, Groq or Gemini key). Files removed from `:app`: `ui/components/SaifLiveOrb.kt`,
  `SaifLiveModeSheet.kt`, `SaifLiveSettingsSection.kt` and `live/LiveTurnLoop.kt`.
- Still there, and SAIF AI's own code: voice typing in the chat box, spoken replies (`TTSManager`, `SaifVoice`),
  the "Hey Saif" wake word, the phone agent (`agent/`), `live/LiveCodingBridge.kt`, and the unused older
  `LiveVoiceService` / `LiveVoiceSessionManager`. A new live-voice feature has to be written from scratch.
- Older text in this file and in `docs/ROUND_*_REPORT.md` that mentions `SaifAutomation`,
  `JarvisConversationService`, the orb or the purple mic describes code that no longer exists.
