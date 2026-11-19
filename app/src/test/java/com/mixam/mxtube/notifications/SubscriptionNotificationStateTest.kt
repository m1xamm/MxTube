package com.mixam.mxtube.notifications

import com.mixam.mxtube.data.local.SubscriptionEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SubscriptionNotificationStateTest {
    private lateinit var directory: File
    private val channel = SubscriptionEntry("@creator", "https://www.youtube.com/@creator", "Creator", null, 1L)
    private val state = ChannelUploadNotificationState(
        1L, channel.url, true, listOf("newvideo001"), 123L, 456L,
        "https://www.youtube.com/channel/UC1111111111111111111111",
    )

    @Before
    fun setUp() {
        directory = File(System.getProperty("java.io.tmpdir"), "mx-upload-test-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun stateSurvivesReopenAndAtomicReplacement() {
        SubscriptionNotificationState.write(directory, mapOf(channel.key to state))
        assertEquals(state, SubscriptionNotificationState.read(directory)[channel.key])
        val next = state.copy(seenVideoIds = listOf("newvideo001", "newvideo002"))
        SubscriptionNotificationState.write(directory, mapOf(channel.key to next))
        assertEquals(next, SubscriptionNotificationState.read(directory)[channel.key])
        assertFalse(File(directory, "${SubscriptionNotificationState.FILE_NAME}.tmp").exists())
    }

    @Test
    fun retryProgressPersistsWithoutTurningAFailedInitialFetchIntoASeed() {
        val failed = state.copy(seeded = false, seenVideoIds = emptyList(), retryPending = true)
        SubscriptionNotificationState.write(directory, mapOf(channel.key to failed))
        assertEquals(failed, SubscriptionNotificationState.read(directory)[channel.key])
    }

    @Test
    fun unsubscribeAndChangedSubscriptionIncarnationsArePruned() {
        val states = mutableMapOf(channel.key to state)
        SubscriptionNotificationState.prune(states, listOf(channel.copy(addedAtEpochMillis = 2L)))
        assertTrue(states.isEmpty())
        states[channel.key] = state
        SubscriptionNotificationState.prune(states, emptyList())
        assertTrue(states.isEmpty())
    }

    @Test
    fun profileModeAndReEnableChangesRetainSeenIds() {
        val states = mutableMapOf(channel.key to state)
        SubscriptionNotificationState.prune(states, listOf(channel.copy(name = "Renamed", notificationsEnabledAtEpochMillis = 10L)))
        assertEquals(state, states[channel.key])
    }

    @Test
    fun corruptAndUnknownSchemasReseedSafely() {
        val file = File(directory, SubscriptionNotificationState.FILE_NAME)
        file.writeText("not json")
        assertTrue(SubscriptionNotificationState.read(directory).isEmpty())
        file.writeText("{\"v\":99,\"channels\":[]}")
        assertTrue(SubscriptionNotificationState.read(directory).isEmpty())
    }

    @Test
    fun seenAndChannelCountsStayBounded() {
        val seen = (0..200).map { "v" + it.toString().padStart(10, '0') }
        val states = (0..350).associate { "@creator$it" to state.copy(seenVideoIds = seen) }
        SubscriptionNotificationState.write(directory, states)
        val restored = SubscriptionNotificationState.read(directory)
        assertEquals(SubscriptionNotificationState.MAX_CHANNELS, restored.size)
        assertTrue(restored.values.all { it.seenVideoIds.size == SubscriptionNotificationPolicy.MAX_SEEN_IDS })
    }
}
