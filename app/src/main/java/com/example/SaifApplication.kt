package com.example

import android.app.Application
import com.example.data.local.ProviderSettingsManager

class SaifApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProviderSettingsManager.init(this)

        // Pre-load the on-device speech model at app start, so voice typing and the live conversation work with
        // ANY provider key. Cloud speech-to-text exists only for Groq, OpenAI and Gemini keys; for every other
        // provider (OpenRouter, Anthropic, custom...) the words are recognised on the phone.
        try { com.example.stt.OfflineSpeech.prepare(this) } catch (_: Throwable) {}

        // App-wide memory recall: every feature (chat, live voice, video director, builder, agent) learns into it
        // and recalls from it.
        com.example.util.SaifMemoryCore.init(this)
        com.example.utils.TTSManager.init(this)

        // SAIF ADVANCE EDITING ENGINE needs these at process start (the workspace has its own Application class that no
        // longer runs now that SaifApplication is the app class). The app self-updater and the original project's
        // analytics are deliberately NOT started here.
        try { com.example.gemini.data.adb.AdbConnectManager.init(this) } catch (_: Throwable) {}
        try { com.example.gemini.data.updater.PluginUpdateCheckWorker.schedule(this) } catch (_: Throwable) {}

        // The live voice conversation (purple microphone) is SAIF AI's own engine, see com.example.live.SaifLive.
        // It needs no set-up here: it reads the provider key, the voice, the memory and the language setting from
        // the app when a session starts.
    }
}
