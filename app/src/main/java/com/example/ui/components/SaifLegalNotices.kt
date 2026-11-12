/*
 * SAIF AI - licence and credits screen.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.ui.components

/** The licence and credit texts bundled in `assets/legal`, and where the source code lives. */
object SaifLegalNotices {
    const val SOURCE_URL = "https://github.com/juwadipro73-svg/claude-saif-ai"
    const val CREDITS_PAGE = "THIRD_PARTY_NOTICES.md"
    const val LICENSE_PAGE = "LICENSE"

    /** Tab label to file below `assets/legal/`. The credits come first: they name everyone whose code is used. */
    val PAGES: List<Pair<String, String>> = listOf(
        "Credits" to CREDITS_PAGE,
        "GPL-3.0 (SAIF AI)" to LICENSE_PAGE,
        "Apache-2.0" to "licenses/Apache-2.0.txt",
        "LGPL-2.1" to "licenses/LGPL-2.1.txt",
        "LGPL-3.0" to "licenses/LGPL-3.0.txt",
        "GPL-2.0" to "licenses/GPL-2.0.txt",
        "EPL-2.0" to "licenses/EPL-2.0.txt",
        "MIT" to "licenses/MIT.txt",
        "BSD-3-Clause" to "licenses/BSD-3-Clause.txt",
        "BSD-2-Clause" to "licenses/BSD-2-Clause.txt"
    )

    /** Shown at the bottom of Settings. The GPL asks for a visible copyright, no-warranty and licence notice. */
    const val SHORT_NOTICE =
        "Copyright (C) 2026 SAIF AI and contributors. Built on AntiGem by Santosh Kurmi (GPLv3). Free software " +
            "under the GNU GPL v3 or later, with ABSOLUTELY NO WARRANTY. Tap to read the licences, the credits " +
            "and where to get the source code."
}
