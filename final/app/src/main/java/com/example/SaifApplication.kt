/* Copyright (c) SAIF AI. All rights reserved. */

package com.example

import android.app.Application
import com.example.data.local.ProviderSettingsManager
import com.saifai.engine.SaifEngineBootstrap

class SaifApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProviderSettingsManager.init(this)

        // Initialize SAIF AI Sovereign Engine & Memory
        SaifEngineBootstrap.initialize(this)
        com.example.util.SaifMemoryCore.init(this)
        com.example.utils.TTSManager.init(this)
    }
}
