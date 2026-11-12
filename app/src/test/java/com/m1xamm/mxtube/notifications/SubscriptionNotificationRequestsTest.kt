package com.m1xamm.mxtube.notifications

import com.m1xamm.mxtube.data.local.SubscriptionEntry
import com.m1xamm.mxtube.data.local.SubscriptionNotificationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionNotificationRequestsTest {
    private val channel = SubscriptionEntry("@creator", "https://www.youtube.com/@creator", "Creator", null, 1L, SubscriptionNotificationMode.All)

    @Test
    fun unchangedStartupAndProfileUpdatesPreserveTheRequestGeneration() {
        val requests = SubscriptionNotificationRequests()
        assertTrue(requests.update(listOf(channel)))
        val generation = requests.capture(channel)
        assertNotNull(generation)
        assertFalse(requests.update(listOf(channel.copy(name = "New display name"))))
        assertEquals(generation, requests.capture(channel))
    }

    @Test
    fun unsubscribeInvalidatesAnInFlightRequest() {
        val requests = SubscriptionNotificationRequests()
        requests.update(listOf(channel))
        val generation = requireNotNull(requests.capture(channel))
        requests.update(emptyList())
        assertFalse(requests.isCurrent(channel, generation, null))
        assertNull(requests.capture(channel))
    }

    @Test
    fun resubscribeCannotReviveAnOldRequestEvenWithIdenticalTimestamps() {
        val requests = SubscriptionNotificationRequests()
        requests.update(listOf(channel))
        val old = requireNotNull(requests.capture(channel))
        requests.update(emptyList())
        requests.update(listOf(channel))
        assertFalse(requests.isCurrent(channel, old, channel))
        assertTrue(requests.isCurrent(channel, requireNotNull(requests.capture(channel)), channel))
    }

    @Test
    fun noneThenReEnableCannotReviveAnOldRequest() {
        val requests = SubscriptionNotificationRequests()
        requests.update(listOf(channel))
        val generation = requireNotNull(requests.capture(channel))
        requests.update(listOf(channel.copy(notificationMode = SubscriptionNotificationMode.None)))
        requests.update(listOf(channel))
        assertFalse(requests.isCurrent(channel, generation, channel))
    }

    @Test
    fun aLateStoreSnapshotBlocksDeliveryBeforeTheSchedulerObserverRuns() {
        val requests = SubscriptionNotificationRequests()
        requests.update(listOf(channel))
        val generation = requireNotNull(requests.capture(channel))
        assertFalse(requests.isCurrent(channel, generation, channel.copy(notificationMode = SubscriptionNotificationMode.None)))
        assertFalse(requests.isCurrent(channel, generation, channel.copy(notificationsEnabledAtEpochMillis = 2L)))
        assertFalse(requests.isCurrent(channel, generation, channel.copy(addedAtEpochMillis = 2L)))
    }
}
