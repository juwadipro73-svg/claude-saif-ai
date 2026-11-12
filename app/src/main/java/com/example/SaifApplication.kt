package com.example

import android.app.Application
import com.example.data.local.ProviderSettingsManager

class SaifApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProviderSettingsManager.init(this)

        // App-wide memory recall: every feature (chat, video director, builder, agent) learns into it and
        // recalls from it.
        com.example.util.SaifMemoryCore.init(this)
        com.example.utils.TTSManager.init(this)

        // SAIF ADVANCE EDITING ENGINE needs these at process start (the workspace has its own Application class that no
        // longer runs now that SaifApplication is the app class). The app self-updater and the original project's
        // analytics are deliberately NOT started here.
        try { com.example.gemini.data.adb.AdbConnectManager.init(this) } catch (_: Throwable) {}
        try { com.example.gemini.data.updater.PluginUpdateCheckWorker.schedule(this) } catch (_: Throwable) {}
    }
}
