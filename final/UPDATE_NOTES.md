# SAIF AI — UI and reliability update

## Delivery status
Source update only. No tests, Android build, emulator run, or device validation was performed. This environment has no Java/Android SDK and cannot reach the required Google/Gradle repositories. **There is no generated APK or verified APK download link.** See BUILD_STATUS.md for pre-existing missing classes and backend placeholders; this update does not resolve those project-wide blockers.

## Implemented
- Native Compose Architect / Builder / Reviewer welcome cards with entrance animation and horizontal scrolling. Custom API users see them without needing hub authentication.
- Shared press/release shape and scale interpolation for 108 existing standard IconButton usages in the main UI package. Custom icon controls elsewhere retain their existing behavior. Material semantics and touch targets are preserved.
- Existing animated splash uses the shared time-based accent. In-app identity cycles every two hours with a 2.4-second interpolation while composed. Adaptive launcher artwork has a dark/cyan palette.
- Appearance remains above functional settings; duplicate engine Appearance entry removed. About > Credits opens one small-font horizontally scrollable notice line with prominent SAIF AI heading. Original bundled legal documents remain intact. Removed attribution from settings footer and duplicate drawer Admin Control Center. Other drawer choices retained because the referenced screenshot was not supplied.
- Custom API profiles migrated to Android Keystore-backed encrypted preferences; plaintext state removed only after encrypted persistence succeeds. Existing provider storage outside this manager is unchanged.
- HTTPS required for remote custom endpoints; exact loopback hosts allowed for native bridges. App network security blocks remote cleartext HTTP, including LAN HTTP APIs and plain-HTTP browser requests; use HTTPS instead.
- Longer bounded API read/call timeouts; disables transparent connection retry for agent requests to avoid duplicate billable execution. This does not implement a provider-wide resumable streaming protocol.
- Host terminal drains stdout concurrently, bounds retained output to 128 KiB, throttles live UI snapshots, enforces a deadline while reading, and destroys the direct process on cancellation. Command timeout default 15 minutes, maximum one hour. Ubuntu callback buffer also bounded. Process-tree termination and Ubuntu backend internals still need device validation.
- Chat foreground service plus bounded wake lock during generation. Successful completed chat tasks notify once per task only when no activity is visible. No completion notification for failures/cancellation or foreground activity. Foreground progress notification is required by Android. Notification permission/user settings are respected.
- Release R8/resource shrinking retained; unnecessary broad OkHttp keep rules removed. Release keystore passwords are environment supplied, never hardcoded. App backup disabled. No claim of decompile-proof security or comprehensive anti-tamper enforcement.
- Permission settings now list microphone, camera, and notifications only; removed misleading contacts/SMS/overlay/accessibility/notification-access requests for unavailable features. Other manifest media permissions are retained pending a full media workflow audit.

## Explicitly not completed / limitations
- Android launcher icons cannot smoothly morph. No launcher alias rotation or periodic launcher-icon worker was added. The 2-hour color animation applies only inside the app.
- Task execution is still owned by the existing ViewModel. Foreground protection helps minimization, but force-stop, OS process death, activity destruction and OEM task removal can terminate it. Durable service-owned jobs/checkpoint recovery are not implemented. Wake protection expires after two hours. Existing BuildService-based non-chat tasks are not integrated with the new completion tracker.
- Main backend adapters contain pre-existing stubs. Custom API tool execution and terminal APK compilation cannot be certified working until these are restored and the app is built on a configured machine.
- No exact case-insensitive `texhgptx` reference was found. No undocumented third-party code ownership claim or bulk backend deletion was made. Native replacement of unidentified legacy subsystems is not complete. APK size/performance improvements have not been measured.
- Credits reflect the existing THIRD_PARTY_NOTICES.md inventory, including VERIFY entries. GPL/LGPL attribution, corresponding-source and relinking obligations still need a release audit. Compact presentation does not replace those legal obligations.
- No repository-wide fix of existing syntax/type errors was attempted. R8 reflection compatibility, concurrent task lifecycle, Android notification policy, accessibility and reduced-motion behavior require user testing.

## Building after existing blockers are fixed
Use Android Studio with SDK 36, JDK/toolchain 21 and internet access to Google Maven, Maven Central and Gradle distributions. The current project defines only the `standard` flavor.

```sh
cd final
bash gradlew :app:assembleStandardDebug
# Output: app/build/outputs/apk/standard/debug/*.apk
```

Release builds require a private signing key, not the public debug keystore:

- SAIF_RELEASE_STORE_FILE
- SAIF_RELEASE_STORE_PASSWORD
- SAIF_RELEASE_KEY_ALIAS
- SAIF_RELEASE_KEY_PASSWORD

Without those values a release is unsigned. Never distribute the repository debug key as a trusted production identity. Keep R8 mapping files private for crash diagnostics. Remove embedded platform/bot secrets from deployment configuration before publishing; R8 does not hide APK constants.

The source ZIP includes the project's existing public debug keystore for reproducible debug installs, not a new production signing key. It excludes Git metadata, caches, build output, local properties and private environment files.
