/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.engine

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class SaifSecuritySentinel : DeviceAdminReceiver() {
    companion object /* SAIF Security Sentinel */ {
        const val SENTINEL_ACTION_STATUS_CHANGED = "com.saifai.sentinel.STATUS_CHANGED"
    }

    override fun onEnabled(adminContext: Context, adminIntent: Intent) {
        super.onEnabled(adminContext, adminIntent)
    }

    override fun onDisabled(adminContext: Context, adminIntent: Intent) {
        super.onDisabled(adminContext, adminIntent)
    }
}
