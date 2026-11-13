package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.example.live.LiveError
import com.example.live.LiveStartCheck
import com.example.live.SaifLive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU], application = Application::class)
class LiveConversationPermissionTest {
    @Test fun foregroundLiveVoiceNeedsMicButNotBatteryExemptionOrNotifications() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(power).setIgnoringBatteryOptimizations(app.packageName, false)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // The microphone is enough: no battery exemption and no notification permission is asked for.
        assertTrue(SaifLive.hasMicPermission(app))
        assertNull(LiveStartCheck.problem(SaifLive.hasMicPermission(app), hasProviderKey = true, agentConnected = false))

        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        assertFalse(SaifLive.hasMicPermission(app))
        val problem = LiveStartCheck.problem(SaifLive.hasMicPermission(app), hasProviderKey = true, agentConnected = false)
        assertEquals(LiveError.CODE_NO_MICROPHONE, problem?.errorCode)
    }
}
