package com.example.live

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProviderSettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Starting and stopping SAIF AI's live voice: what happens before the microphone service is asked to run. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU], application = Application::class)
class SaifLiveStartTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun freshState() {
        // No saved provider key and no connected chat agent.
        ProviderSettingsManager.init(app)
        LiveCodingBridge.send = null
        LiveCodingBridge.isReady = null
        SaifLive.clearLiveError()
    }

    @Test fun withoutTheMicrophonePermissionNothingStarts() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)

        assertFalse(SaifLive.startLive(app, LiveMode.NORMAL))

        assertEquals(LiveError.CODE_NO_MICROPHONE, SaifLive.liveError.value?.errorCode)
        assertNull(shadowOf(app).peekNextStartedService())
        assertFalse(SaifLive.isLiveRunning.value)
    }

    @Test fun withoutAKeyOrAConnectedAgentTheUserIsSentToTheSettings() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        assertFalse(SaifLive.canAnswer())

        assertFalse(SaifLive.startLive(app, LiveMode.NORMAL))

        assertEquals(LiveError.CODE_NO_AI, SaifLive.liveError.value?.errorCode)
        assertNull(shadowOf(app).peekNextStartedService())
        assertFalse(SaifLive.isLiveRunning.value)
    }

    @Test fun stoppingWithoutASessionOnlyClearsTheProblem() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        SaifLive.startLive(app, LiveMode.NORMAL)

        SaifLive.stopLive(app)

        assertNull(SaifLive.liveError.value)
        assertFalse(SaifLive.isLiveRunning.value)
        assertEquals(SaifLive.STATUS_READY, SaifLive.connectionStatus.value)
        assertEquals(LiveMode.NORMAL, SaifLive.liveMode.value)
        // The screen can only be shared with a running session.
        assertFalse(SaifLive.enterScreenShareMode())
    }

    @Test fun theServiceShowsItsNotificationAtOnceAndStopsWhenAsked() {
        val stop = Intent(app, SaifLiveService::class.java).setAction(SaifLiveService.ACTION_STOP)
        val controller = Robolectric.buildService(SaifLiveService::class.java, stop).create()
        val service = controller.get()

        // Created: the screens can reach it, and the microphone notification with its Stop button is up.
        assertSame(service, SaifLiveService.current)
        val notification = shadowOf(service).lastForegroundNotification
        assertNotNull(notification)
        assertEquals(1, notification.actions.size)
        assertEquals("Stop", notification.actions[0].title.toString())

        // The Stop button (or a start without a known command) ends the service; nothing was ever listening.
        controller.startCommand(0, 7)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertFalse(SaifLive.isLiveRunning.value)
        assertFalse(SaifLive.isScreenSharing.value)

        controller.destroy()
        assertNull(SaifLiveService.current)
    }
}
