package com.example.agents

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaifAgentRegistryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun explicitAgentSlashTriggerSelectsAgentAndRemovesTriggerFromPrompt() {
        val key = "saif_specialist_agents_v1_guest"
        context.getSharedPreferences(key, Context.MODE_PRIVATE).edit().clear().commit()
        SaifAgentRegistry.reload(context, "guest")

        val (agent, prompt) = SaifAgentRegistry.resolveAgentForTurn(context, "/agent-coding Fix the Kotlin crash")

        assertNotNull(agent)
        assertEquals("coding", agent?.slug)
        assertEquals("Fix the Kotlin crash", prompt)
        assertEquals(agent?.id, SaifAgentRegistry.state.value.activeAgentId)
        assertTrue(SaifAgentRegistry.buildAgentDirective(agent!!).contains("Coding Architect"))
    }
}
