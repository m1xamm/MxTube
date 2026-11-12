package com.m1xamm.mxtube.data.local

import com.m1xamm.mxtube.data.youtube.ChannelRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SubscriptionsStoreTest {
    private lateinit var dir: File
    private lateinit var file: File

    private val eram = ChannelRef("@eram", "https://www.youtube.com/@eram")
    private val one = ChannelRef("UC1111111111111111111111", "https://www.youtube.com/channel/UC1111111111111111111111")

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "mxsubs-${System.nanoTime()}").apply { mkdirs() }
        file = File(dir, "mx_subs.json")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun survivesReopen() = runBlocking {
        val store = SubscriptionsStore(file)
        store.subscribe(eram, "ERAM", "https://img/avatar.jpg", now = 1_000L)
        store.subscribe(one, "One", null, now = 2_000L)
        store.markFeedSeen(4_321L)

        val state = SubscriptionsStore(file).snapshot()

        assertEquals(listOf(one.key, eram.key), state.channels.map { it.key })
        assertEquals("https://img/avatar.jpg", state.channels.last().avatarUrl)
        assertEquals(4_321L, state.lastSeenFeedAtEpochMillis)
    }

    @Test
    fun resubscribingKeepsTheOriginalEntryAndMovesItToFront() = runBlocking {
        val store = SubscriptionsStore(file)
        store.subscribe(eram, "ERAM", null, now = 1_000L)
        store.subscribe(one, "One", null, now = 2_000L)
        store.subscribe(eram, "ERAM new", "https://img/avatar.jpg", now = 3_000L)

        val state = store.snapshot()

        assertEquals(listOf(eram.key, one.key), state.channels.map { it.key })
        val reused = state.channels.first()
        assertEquals(1_000L, reused.addedAtEpochMillis)
        assertEquals("ERAM new", reused.name)
        assertEquals("https://img/avatar.jpg", reused.avatarUrl)
    }

    @Test
    fun emptyNameKeepsWhatWasStored() = runBlocking {
        val store = SubscriptionsStore(file)
        store.subscribe(eram, "ERAM", "https://img/avatar.jpg", now = 1_000L)
        store.subscribe(eram, "", null, now = 5_000L)

        val entry = store.snapshot().channels.single()
        assertEquals("ERAM", entry.name)
        assertEquals("https://img/avatar.jpg", entry.avatarUrl)
    }

    @Test
    fun unsubscribeAndClearAll() = runBlocking {
        val store = SubscriptionsStore(file)
        store.subscribe(eram, "ERAM", null, now = 1_000L)
        store.subscribe(one, "One", null, now = 2_000L)

        store.unsubscribe(eram.key)
        assertEquals(listOf(one.key), store.snapshot().channels.map { it.key })
        assertFalse(store.isSubscribed(eram.key))
        assertTrue(store.isSubscribed(one.key))

        store.clearAll()
        assertTrue(SubscriptionsStore(file).snapshot().channels.isEmpty())
    }

    @Test
    fun updateProfileKeepsOrderAndTimestamp() = runBlocking {
        val store = SubscriptionsStore(file)
        store.subscribe(eram, "ERAM", null, now = 1_000L)
        store.subscribe(one, "One", null, now = 2_000L)

        store.updateProfile(eram.key, "ERAM channel", "https://img/avatar.jpg")

        val state = store.snapshot()
        assertEquals(listOf(one.key, eram.key), state.channels.map { it.key })
        val updated = state.channels.last()
        assertEquals("ERAM channel", updated.name)
        assertEquals("https://img/avatar.jpg", updated.avatarUrl)
        assertEquals(1_000L, updated.addedAtEpochMillis)
    }

    @Test
    fun notificationModeSurvivesReopenAndProfileUpdates() = runBlocking {
        val store = SubscriptionsStore(file)
        store.subscribe(one, "One", null, now = 1_000L)
        store.setNotificationMode(one.key, SubscriptionNotificationMode.All, now = 2_000L)
        store.updateProfile(one.key, "One updated", null)
        store.subscribe(one, "One updated", null, now = 3_000L)

        val entry = SubscriptionsStore(file).snapshot().channels.single()
        assertEquals(SubscriptionNotificationMode.All, entry.notificationMode)
        assertEquals(1_000L, entry.notificationsEnabledAtEpochMillis)
        assertEquals(1_000L, entry.addedAtEpochMillis)
    }

    @Test
    fun reEnablingNotificationsDoesNotReplayMutedUploads() = runBlocking {
        val store = SubscriptionsStore(file)
        store.subscribe(one, "One", null, now = 1_000L)
        store.setNotificationMode(one.key, SubscriptionNotificationMode.None, now = 2_000L)
        store.setNotificationMode(one.key, SubscriptionNotificationMode.All, now = 5_000L)
        store.setNotificationMode(one.key, SubscriptionNotificationMode.All, now = 6_000L)

        val entry = store.snapshot().channels.single()
        assertEquals(5_000L, entry.notificationsEnabledAtEpochMillis)
        assertEquals(SubscriptionNotificationMode.All, entry.notificationMode)
    }

    @Test
    fun changingNotificationsDoesNotRestoreAnUnsubscribedChannel() = runBlocking {
        val store = SubscriptionsStore(file)
        store.subscribe(one, "One", null, now = 1_000L)
        store.unsubscribe(one.key)
        store.setNotificationMode(one.key, SubscriptionNotificationMode.All)
        assertTrue(store.snapshot().channels.isEmpty())
    }

    @Test
    fun existingSubscriptionsDefaultToPersonalized() = runBlocking {
        file.writeText("""{"v":1,"channels":[{"key":"@eram","url":"https://www.youtube.com/@eram","name":"ERAM","addedAt":1000}]}""")
        val entry = SubscriptionsStore(file).snapshot().channels.single()
        assertEquals(SubscriptionNotificationMode.Personalized, entry.notificationMode)
        assertEquals(1_000L, entry.notificationsEnabledAtEpochMillis)
    }

    @Test
    fun ignoresAFileFromAnUnknownSchema() = runBlocking {
        file.writeText("""{"v":99,"channels":[{"key":"@x","url":"https://www.youtube.com/@x","name":"X"}]}""")
        assertTrue(SubscriptionsStore(file).snapshot().channels.isEmpty())
    }
}
