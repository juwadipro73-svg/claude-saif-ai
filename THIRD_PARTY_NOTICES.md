# SAIF AI - licence, credits and third-party notices

SAIF AI is free software. It is licensed under the **GNU General Public License, version 3 or (at your option) any
later version** (`GPL-3.0-or-later`), the same licence as the project it is built on (AntiGem). The full text is in
[`LICENSE`](LICENSE).

    SAIF AI
    Copyright (C) 2026 SAIF AI and contributors
    Copyright (C) 2026 Santosh Kurmi (AntiGem, on which SAIF AI is based)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU General
    Public License as published by the Free Software Foundation, either version 3 of the License, or (at your
    option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
    implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
    for more details.

**Source code.** The complete source of SAIF AI, including every change made to the projects below, is published at
<https://github.com/juwadipro73-svg/claude-saif-ai>. Each APK shows its build number and commit in
Settings (bottom of the screen); the source of that exact build is the repository at that commit.

The same notices are inside the app: **Settings > Open-source licences & credits**.

---

## 1. Projects SAIF AI is built on (their source code is part of this repository)

### AntiGem - by Santosh Kurmi
- Project: <https://github.com/Santoshkurmi/antigem>
- Licence: GNU General Public License v3.0 or any later version. Copyright (C) 2026 Santosh Kurmi.
- Used for: the AI-agent workspace of SAIF AI - agent chat, Linux terminal, code editor (IDE), in-app browser,
  plugins and settings. In this repository that is, among others, `app/src/main/java/com/example/gemini/`,
  `app/src/main/java/com/termux/`, `app/src/main/proto/`, `app/src/main/assets/browser-runtime/`,
  `app/src/standard/assets/`, `agy_ide_bridge/`, `proto_generator/`, `extensions/`, `extension_docs/`, `scripts/`
  and parts of `docs/` (all present in the AntiGem repository), and `termux-daemon/` (named as AntiGem code by
  the earlier notice of this repository).
- Changes: SAIF AI renamed and restyled the interface, added its own sign-in, chat home screen, live voice
  conversation, video studio, API-key provider, cloud build templates and storage clean-up, and changed many
  files. The modified files are distributed under the same GPL.

### Termux terminal view and terminal emulator
- Project: <https://github.com/termux/termux-app> (libraries `terminal-view` and `terminal-emulator`), based on
  Terminal Emulator for Android by Jack Palevich.
- Licence: Apache License 2.0 ([`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt)).
- Used for: drawing the terminal. `app/src/main/java/com/termux/view/` holds the terminal view sources; the
  emulator is the library `com.github.termux.termux-app:terminal-emulator:v0.118.1`.

### Chrome extension API type definitions (documentation only, not in the APK)
- `docs/chrome_extension_api/chrome/` from DefinitelyTyped (`@types/chrome`). MIT licence,
  Copyright (c) Microsoft Corporation and the contributors named in the file. Licence file kept next to it.

---

## 2. Programs shipped inside the APK as ready-made binaries

| Component | Files | Licence | Source |
|---|---|---|---|
| PRoot (Termux build) | `app/src/standard/assets/bin/proot`, `loader` | GNU GPL v2 or later. Copyright (C) 2015 STMicroelectronics. | <https://github.com/termux/proot> |
| talloc | `app/src/standard/assets/bin/libtalloc.so.2`, `.../lib/libtalloc.so.2` | GNU LGPL v3 or later. Copyright (C) Andrew Tridgell 2004, Stefan Metzmacher 2006, and the Samba team. | <https://talloc.samba.org/> |
| GNU C Library 2.44 (Termux glibc build) | `app/src/main/assets/glibc-min-arm64.tar` | GNU LGPL v2.1 or later. Copyright (C) Free Software Foundation, Inc. | <https://sourceware.org/glibc/>, build recipes: <https://github.com/termux-pacman/glibc-packages> |

These binaries are used unchanged, as they came with AntiGem. Their licence texts are in `licenses/GPL-2.0.txt`,
`licenses/LGPL-3.0.txt` and `licenses/LGPL-2.1.txt`. Anyone who receives the APK may ask the distributor for the
source code of these programs; it is available from the addresses above.

---

## 3. Libraries built into the app

This table was compared with the list of libraries that the build really packages into the APK (the build
prints that list; see "List the libraries packaged in the APK" in `.github/workflows/build-apk.yml`).

| Library | Licence | Copyright / project |
|---|---|---|
| AndroidX / Jetpack (core, activity, lifecycle, Compose, Material 3, navigation3, Room, DataStore, WorkManager, WebKit, Media3, security-crypto, swiperefreshlayout, and the AndroidX libraries these pull in) | Apache-2.0 | The Android Open Source Project |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization, JetBrains annotations | Apache-2.0 | JetBrains s.r.o. and contributors |
| OkHttp, Okio, Wire | Apache-2.0 | Square, Inc. |
| Coil (coil-compose, coil-svg) | Apache-2.0 | Coil Contributors |
| AndroidSVG (used by Coil) | Apache-2.0 | Paul LeBeau |
| Accompanist Drawable Painter (used by Coil) | Apache-2.0 | Google LLC |
| Firebase Android SDK, open-source parts: Firestore, Realtime Database, App Check, common components | Apache-2.0 | Google LLC |
| gRPC-Java (used by Firestore) | Apache-2.0 | The gRPC Authors |
| Protocol Buffers, Java lite runtime (used by Firestore) | BSD-3-Clause | Google LLC |
| PerfMark (used by gRPC) | Apache-2.0 | Google LLC |
| Tink cryptography library (used by AndroidX security-crypto) | Apache-2.0 | Google LLC |
| Gson, Guava, apksig | Apache-2.0 | Google LLC / The Android Open Source Project |
| Apache Commons Compress, Commons IO, Commons Codec, Commons Lang | Apache-2.0 | The Apache Software Foundation |
| Markwon (core, inline-parser, ext-latex) | Apache-2.0 | Dimitry Ivanov |
| Vosk Android (speech recognition on the phone, with Kaldi; used by SAIF AI's voice typing and live voice) | Apache-2.0 | Alpha Cephei Inc. |
| SnakeYAML Engine (used by sora-editor) | Apache-2.0 | SnakeYAML contributors |
| JNA (Java Native Access; used by Vosk) | Apache-2.0 **or** LGPL-2.1-or-later (used here under Apache-2.0) | Timothy Wall and contributors |
| Annotation-only libraries: JSR-305, Error Prone annotations, J2ObjC annotations, JSpecify, javax.inject, Android annotations | Apache-2.0 | their respective authors (Google LLC and others) |
| Annotation-only libraries: Checker Framework qualifiers, Animal Sniffer annotations | MIT | the Checker Framework developers; MojoHaus |
| sora-editor (editor, language-textmate, language-java) | LGPL-2.1 | Copyright (C) 2020-2024 Rosemoe - <https://github.com/Rosemoe/sora-editor> |
| Eclipse TM4E code inside sora-editor's language-textmate, Eclipse JDT annotations | EPL-2.0 | Eclipse Foundation and contributors |
| JLaTeXMath (via `ru.noties:jlatexmath-android`) | GPL-2.0-or-later with a linking exception | Copyright (C) 2009-2011 Calixte Denizet, Kris Coolsaet, Nico Van Cleemput, Kurt Vermeulen; Copyright 2004-2007 Universiteit Gent |
| JSch (fork `com.github.mwiede:jsch`) | BSD-3-Clause | Copyright (c) 2002-2015 Atsuhiko Yamanaka, JCraft, Inc. |
| jsoup | MIT | Copyright (c) 2009-2026 Jonathan Hedley |
| Joni, JCodings (used by sora-editor) | MIT | Copyright (c) JRuby Team |
| commonmark-java (used by Markwon) | BSD-2-Clause | Copyright (c) 2015, Robin Stocker |
| XZ for Java | Public domain / 0BSD | Lasse Collin, Igor Pavlov |

**Not open source.** For sign-in and app protection the app also contains these Google libraries. They are not
free software; Google lets apps include them under the Android Software Development Kit Licence, the Google Play
Core SDK terms and the Google APIs terms: Firebase Authentication, Google Play services (auth, base, basement,
tasks, fido), Google Identity (`googleid`), Play Integrity and reCAPTCHA. See section 8.

Licence texts: `licenses/Apache-2.0.txt`, `licenses/LGPL-2.1.txt`, `licenses/EPL-2.0.txt`, `licenses/GPL-2.0.txt`,
`licenses/MIT.txt`, `licenses/BSD-2-Clause.txt`, `licenses/BSD-3-Clause.txt`.

The exact versions are in `gradle/libs.versions.toml` and `app/build.gradle.kts`.
Libraries that those libraries pull in themselves keep their own licences.

---

## 4. Go modules compiled into the workspace bridge (`agy_ide_bridge`)

| Module | Licence | Copyright |
|---|---|---|
| Go standard library and runtime, `golang.org/x/sys` | BSD-3-Clause | Copyright 2009 The Go Authors |
| `github.com/gorilla/websocket` | BSD-2-Clause | Copyright (c) 2013 The Gorilla WebSocket Authors |
| `github.com/google/uuid` | BSD-3-Clause | Copyright (c) 2009, 2014 Google Inc. |
| `github.com/remyoudompheng/bigfft` | BSD-3-Clause | Copyright (c) 2012 The Go Authors |
| `github.com/dustin/go-humanize` | MIT | Copyright (c) 2005-2008 Dustin Sallings |
| `github.com/mattn/go-isatty` | MIT | Copyright (c) Yasuhiro MATSUMOTO |
| `github.com/ncruces/go-strftime` | MIT | Copyright (c) 2022 Nuno Cruces |
| `modernc.org/sqlite`, `modernc.org/libc`, `modernc.org/mathutil`, `modernc.org/memory` | BSD-3-Clause | The respective modernc.org authors. SQLite itself is in the public domain. `modernc.org/libc` also carries the notices of the C library code it is translated from; see the LICENSE files inside each module. |

---

## 5. Downloaded on the phone at run time (not part of the APK)

The app downloads these when the Linux workspace is set up or a feature is first used. They are not distributed
with SAIF AI and stay under their own licences and terms:

- Ubuntu root file system for PRoot, from <https://github.com/termux/proot-distro> releases; the packages inside
  are under their own free-software licences (see `/usr/share/doc/*/copyright` inside the environment).
- Termux packages and bootstrap, from <https://github.com/termux/termux-packages>.
- The AntiGem bootstrap archive, from <https://github.com/Santoshkurmi/antigem> releases.
- Google Antigravity CLI, Claude Code, OpenCode and other AI tools that the user chooses to install: each is the
  product of its owner and is used under that owner's terms with the user's own account or API key.
- The English speech model `vosk-model-small-en-us` (Apache-2.0, Alpha Cephei Inc.), downloaded once from
  <https://alphacephei.com/vosk/models> when on-device speech recognition is first needed.
- Anything the user or the AI agent installs with `apt`, `npm`, `pip` or similar tools.

The `cloud/` folder holds separate server templates. Their Node.js dependencies and licences are listed in the
`package-lock.json` file of each template; they are not part of the APK.

---

## 6. Trademarks and affiliation

SAIF AI is an independent project. It is **not affiliated with, endorsed by or sponsored by** Google, Anthropic,
OpenAI, OpenCode, Arena, Termux or Santosh Kurmi (AntiGem). Antigravity, Gemini, Claude, Claude Code,
ChatGPT, OpenCode and the other product names are trademarks of their owners and are used only to say which tools
and services the app can work with. The files in `app/src/main/proto/` and `proto_generator/` describe the
interface of the Antigravity language server so that the app can talk to a copy the user installs and signs in to;
they are not a Google product and are not licensed by Google.

---

## 7. What to do when you share SAIF AI (checklist)

1. **Keep these files with the app and the source:** `LICENSE`, `THIRD_PARTY_NOTICES.md` and the `licenses/`
   folder. Do not remove a name from this file. The copies inside the APK
   (`app/src/main/assets/legal/`) are checked by a unit test, so a build cannot silently drop them.
2. **Give the source with every APK.** Because of the GPL, everyone who gets the APK must be able to get the
   complete source of that exact version, including your own changes. Publishing the APK from this public
   repository does that. If the repository is made private, the source must be offered in another way.
3. **Do not put another licence on top.** SAIF AI as a whole cannot be sold or published as closed-source or
   "all rights reserved" software while it contains AntiGem code. You may charge money for it, but the buyer still
   gets the GPL rights and the source.
4. **Do not use other companies' names or logos** as if the app were theirs (section 6).

---

## 8. Open points that this file cannot solve (read before publishing widely)

This file states facts that were checked against the upstream repositories on 2026-11-12. It is not legal advice.
One group could not be re-checked from the build environment, because its Git host was not reachable: the
licence files of `modernc.org/libc`, `modernc.org/mathutil` and `modernc.org/memory` (section 4). They are
listed as their authors publish them; `modernc.org/sqlite` was checked.

1. **Older builds.** Builds up to #10 (made before 2026-11-13) either contained a third-party voice-automation
   module under a different licence, or had no live voice at all. That module is gone: the live voice conversation
   is now SAIF AI's own code under the GPL. Share only builds newer than #10, and replace older APKs wherever you
   published them.
2. **Google's closed libraries and the GPL.** The sign-in and the app-protection checks use Google libraries
   that are not open source (listed at the end of section 3). The GPL has no exception for such libraries, and
   AntiGem itself does not use them. Many GPL apps are published this way, but strictly it is not covered. To be
   fully safe: ask the author of AntiGem for a short written permission to combine his code with these libraries,
   or replace the Google sign-in with open-source code.
3. **Antigravity.** Using Google Antigravity through this app is subject to Google's terms for the user's own
   account. Nothing here grants rights to Google's software or names.
4. **Keys in the repository.** `app/saif-debug.jks` (debug signing key) and `app/google-services.json` are public
   in this repository. They are for test builds only; use a private key for a real release.
