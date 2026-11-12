/*
 * SAIF AI - a user's own API key never needs an Antigravity sign-in.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.agyext

/**
 * Decides when the Antigravity account has to be checked.
 *
 * The bug this prevents: with a saved API key the chat showed itself as "connected", the Antigravity account check
 * then ran against that pretend connection, found no Antigravity account bound to the app user, and covered the
 * whole app with "Verify your Antigravity account". An API key is not an Antigravity session, so there is nothing
 * to verify.
 */
object OwnApiKeyPolicy {
    /**
     * @param usingOwnApiKey the user's own API profile is switched on and complete
     * @param hubReady the real Antigravity hub is connected and signed in
     * @param accountDenial the account-binding check, evaluated only when Antigravity is the engine in use
     * @return a message to show, or null when the app may be used
     */
    fun antigravityAccountIssue(usingOwnApiKey: Boolean, hubReady: Boolean, accountDenial: () -> String?): String? =
        if (usingOwnApiKey || !hubReady) null else accountDenial()

    /** Voice coding tasks may be sent when either engine can take them. */
    fun canRunCodingTasks(usingOwnApiKey: Boolean, hubReady: Boolean, accountDenial: () -> String?): Boolean =
        usingOwnApiKey || (hubReady && accountDenial() == null)

    /**
     * Picks a saved key when Antigravity is not connected: the active profile first, then any other complete one.
     * Returns null when no usable key is saved.
     */
    fun savedProfileToUse(state: AgyCustomApiState): AgyCustomApiProfile? =
        state.activeProfile?.takeIf { it.isReady() } ?: state.profiles.firstOrNull { it.isReady() }
}
