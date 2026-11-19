package com.mixam.mxtube.notifications

import com.mixam.mxtube.data.local.SubscriptionEntry
import com.mixam.mxtube.data.local.SubscriptionNotificationMode
import com.mixam.mxtube.data.local.WatchHistoryEntry
import com.mixam.mxtube.data.youtube.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionNotificationPolicyTest {
    private val now = 100 * SubscriptionNotificationPolicy.DAY_MILLIS
    private val channel = SubscriptionEntry(
        "UC1111111111111111111111", "https://www.youtube.com/channel/UC1111111111111111111111",
        "Creator", null, now - 10_000L, SubscriptionNotificationMode.All,
    )
    private val old = video("oldvideo001", now - 20_000L)
    private val fresh = video("newvideo001", now - 1_000L)
    private val newer = video("newvideo002", now - 500L)
    private val seeded = ChannelUploadNotificationState(channel.addedAtEpochMillis, channel.url, true, listOf("oldvideo001"))

    @Test
    fun firstSuccessfulFetchSeedsAllIdsWithoutHistoricalAlerts() {
        val result = plan(previous = null, uploads = listOf(old, fresh))
        assertTrue(result.accepted)
        assertTrue(result.videos.isEmpty())
        assertTrue(result.nextState?.seeded == true)
        assertEquals(listOf("oldvideo001", "newvideo001"), result.nextState?.seenVideoIds)
    }

    @Test
    fun anEmptySuccessfulFeedSeedsWithoutAlerting() {
        assertTrue(plan(previous = null, uploads = emptyList()).nextState?.seeded == true)
    }

    @Test
    fun allDeliversUnseenEligibleUploadsAndReplayIsSilent() {
        val first = plan(uploads = listOf(old, fresh, newer, fresh))
        assertEquals(listOf(newer.url, fresh.url), first.videos.map { it.url })
        assertTrue(plan(previous = first.nextState, uploads = listOf(fresh, newer)).videos.isEmpty())
    }

    @Test
    fun metadataEditsAreNotNewUploadsEvenIfTheirDateChanges() {
        val state = seeded.copy(seenVideoIds = listOf("newvideo001"))
        val changed = fresh.copy(title = "Edited", viewCount = 42_000L, uploadedAtEpochMillis = now)
        assertTrue(plan(previous = state, uploads = listOf(changed)).videos.isEmpty())
    }

    @Test
    fun noneAndUnsubscribedRejectInFlightFetchWithoutChangingState() {
        listOf(null, channel.copy(notificationMode = SubscriptionNotificationMode.None)).forEach { current ->
            val result = plan(current = current)
            assertFalse(result.accepted)
            assertEquals(seeded, result.nextState)
            assertTrue(result.videos.isEmpty())
        }
    }

    @Test
    fun resubscriptionRejectsOldRequestAndStartsWithANewSeed() {
        val resubscribed = channel.copy(addedAtEpochMillis = now - 200L, notificationsEnabledAtEpochMillis = now - 200L)
        assertFalse(plan(current = resubscribed).accepted)
        val seed = SubscriptionNotificationPolicy.plan(resubscribed, resubscribed, seeded, listOf(newer), emptyList(), now, true)
        assertTrue(seed.videos.isEmpty())
        assertEquals(resubscribed.addedAtEpochMillis, seed.nextState?.addedAtEpochMillis)
    }

    @Test
    fun reEnableUsesEnabledAtCutoffAndRetainsDedupeState() {
        val enabled = channel.copy(notificationsEnabledAtEpochMillis = now - 600L)
        val result = SubscriptionNotificationPolicy.plan(enabled, enabled, seeded, listOf(fresh, newer), emptyList(), now, true)
        assertEquals(listOf(newer), result.videos)
        assertFalse(plan(current = enabled).accepted)
    }

    @Test
    fun cutoffUsesTheLaterOfSubscribeAndEnableTimes() {
        val lateSubscription = channel.copy(addedAtEpochMillis = now - 600L, notificationsEnabledAtEpochMillis = now - 10_000L)
        val state = seeded.copy(addedAtEpochMillis = lateSubscription.addedAtEpochMillis)
        val result = SubscriptionNotificationPolicy.plan(lateSubscription, lateSubscription, state, listOf(fresh, newer), emptyList(), now, true)
        assertEquals(listOf(newer), result.videos)
    }

    @Test
    fun missingOrFuturePublishedDatesAreNotDeliverable() {
        assertTrue(plan(uploads = listOf(fresh.copy(uploadedAtEpochMillis = null), newer.copy(uploadedAtEpochMillis = now + 1))).videos.isEmpty())
    }

    @Test
    fun futureUploadsRemainUnseenUntilTheirPublicationTime() {
        val future = newer.copy(uploadedAtEpochMillis = now + 1)
        listOf(null, seeded).forEach { previous ->
            val early = plan(previous = previous, uploads = listOf(old, future))
            assertTrue(early.videos.isEmpty())
            assertFalse("newvideo002" in early.nextState?.seenVideoIds.orEmpty())
            val published = SubscriptionNotificationPolicy.plan(
                channel, channel, early.nextState, listOf(old, future), emptyList(), now + 1, true,
            )
            assertEquals(listOf(future), published.videos)
            assertTrue(plan(previous = published.nextState, uploads = listOf(future)).videos.isEmpty())
        }
    }

    @Test
    fun permissionAbsenceObservesWithoutPostingOrBuildingABacklog() {
        val blocked = plan(allowed = false)
        assertTrue(blocked.videos.isEmpty())
        assertTrue("newvideo001" in blocked.nextState?.seenVideoIds.orEmpty())
        assertTrue(plan(previous = blocked.nextState, allowed = true).videos.isEmpty())
    }

    @Test
    fun permissionAbsenceStillSeedsExactlyOnce() {
        val first = plan(previous = null, allowed = false)
        assertTrue(plan(previous = first.nextState).videos.isEmpty())
    }

    @Test
    fun personalizedRequiresRecentMatchingHistory() {
        assertTrue(personal(history = emptyList()).videos.isEmpty())
        assertEquals(listOf(fresh), personal(history = listOf(watch())).videos)
        assertTrue(personal(history = listOf(watch(at = now - SubscriptionNotificationPolicy.HISTORY_WINDOW_MILLIS - 1))).videos.isEmpty())
        assertTrue(personal(history = listOf(watch(at = now + 1))).videos.isEmpty())
    }

    @Test
    fun canonicalUploaderUrlWinsOverDisplayName() {
        assertEquals(listOf(fresh), personal(history = listOf(watch(name = "Renamed", uploaderUrl = channel.url.replace("www.youtube.com", "m.youtube.com") + "/videos"))).videos)
        assertTrue(personal(history = listOf(watch(uploaderUrl = "https://www.youtube.com/channel/UC2222222222222222222222"))).videos.isEmpty())
        assertTrue(personal(history = listOf(watch(uploaderUrl = "not a channel"))).videos.isEmpty())
    }

    @Test
    fun resolvedRssIdentityMatchesAHandleSubscription() {
        val handle = channel.copy(key = "@creator", url = "https://www.youtube.com/@creator", notificationMode = SubscriptionNotificationMode.Personalized)
        val result = SubscriptionNotificationPolicy.plan(
            handle, handle, seeded.copy(channelUrl = handle.url), listOf(fresh), listOf(watch()), now, true, channel.url,
        )
        assertEquals(listOf(fresh), result.videos)
    }

    @Test
    fun legacyNameMatchingIsExactAndOnlyForMissingUrl() {
        assertEquals(listOf(fresh), personal(history = listOf(watch(uploaderUrl = null))).videos)
        assertTrue(personal(history = listOf(watch(name = "creator", uploaderUrl = null))).videos.isEmpty())
        assertTrue(personal(history = listOf(watch(name = "Creator ", uploaderUrl = null))).videos.isEmpty())
    }

    @Test
    fun personalizedSelectsOnlyTheLatestUnwatchedUpload() {
        assertEquals(listOf(newer), personal(uploads = listOf(fresh, newer), history = listOf(watch())).videos)
        val watchedNewer = watch().copy(url = "https://youtu.be/newvideo002")
        assertEquals(listOf(fresh), personal(uploads = listOf(fresh, newer), history = listOf(watchedNewer)).videos)
        assertTrue(personal(history = listOf(watch().copy(url = fresh.url + "&t=10"))).videos.isEmpty())
    }

    @Test
    fun personalizedIsRateLimitedForTwentyFourHoursAfterActualDelivery() {
        val first = personal(history = listOf(watch()))
        val committed = SubscriptionNotificationPolicy.acknowledge(first, SubscriptionNotificationMode.Personalized, setOf("newvideo001"), now)
        assertEquals(now, committed?.lastPersonalizedAtEpochMillis)
        val secondUpload = video("newvideo003", now + 1)
        val mode = channel.copy(notificationMode = SubscriptionNotificationMode.Personalized)
        val withinDay = SubscriptionNotificationPolicy.plan(mode, mode, committed, listOf(secondUpload), listOf(watch()), now + 1, true)
        assertTrue(withinDay.videos.isEmpty())
        val tomorrow = SubscriptionNotificationPolicy.plan(mode, mode, committed, listOf(secondUpload), listOf(watch()), now + SubscriptionNotificationPolicy.DAY_MILLIS, true)
        assertEquals(listOf(secondUpload), tomorrow.videos)
    }

    @Test
    fun failedDeliveryDoesNotAdvancePersonalizedTimestamp() {
        val plan = personal(history = listOf(watch()))
        assertEquals(0L, SubscriptionNotificationPolicy.acknowledge(plan, SubscriptionNotificationMode.Personalized, emptySet(), now)?.lastPersonalizedAtEpochMillis)
    }

    @Test
    fun seenIdsAreBoundedAndInvalidFeedUrlsCannotBecomeAlerts() {
        val many = (0..200).map { video("v" + it.toString().padStart(10, '0'), now - 1) }
        val state = plan(uploads = many).nextState
        assertEquals(SubscriptionNotificationPolicy.MAX_SEEN_IDS, state?.seenVideoIds?.size)
        assertTrue(plan(uploads = listOf(fresh.copy(url = "https://evil.example/watch?v=newvideo001"))).videos.isEmpty())
    }

    @Test
    fun aSuccessfulRetryClearsRetryStatusWithoutRepeatingTheSeed() {
        val failed = seeded.copy(retryPending = true)
        val result = plan(previous = failed)
        assertEquals(listOf(fresh), result.videos)
        assertFalse(requireNotNull(result.nextState).retryPending)
        assertTrue(plan(previous = result.nextState).videos.isEmpty())
    }

    @Test
    fun strictVideoIdentityRejectsExternalAndAmbiguousRoutes() {
        assertNotNull(UploadVideoIdentity.fromUrl(fresh.url))
        listOf(
            "http://www.youtube.com/watch?v=newvideo001", "https://youtube.com/watch?v=newvideo001",
            "https://www.youtube.com.evil.test/watch?v=newvideo001", "https://www.youtube.com/watch?v=bad",
            fresh.url + "&v=newvideo002", fresh.url + "#fragment", "https://www.youtube.com:443/watch?v=newvideo001",
            "https://evil@www.youtube.com/watch?v=newvideo001", "https://www.youtube.com/shorts/newvideo001",
        ).forEach { assertNull(it, UploadVideoIdentity.fromUrl(it)) }
        assertEquals("newvideo001", UploadVideoIdentity.watchedId(fresh.url + "&t=10"))
        assertNull(UploadVideoIdentity.watchedId("https://evil.test/watch?v=newvideo001"))
    }

    private fun plan(
        current: SubscriptionEntry? = channel,
        previous: ChannelUploadNotificationState? = seeded,
        uploads: List<VideoSummary> = listOf(fresh),
        allowed: Boolean = true,
    ) = SubscriptionNotificationPolicy.plan(channel, current, previous, uploads, emptyList(), now, allowed)

    private fun personal(
        previous: ChannelUploadNotificationState = seeded,
        uploads: List<VideoSummary> = listOf(fresh),
        history: List<WatchHistoryEntry>,
    ): SubscriptionNotificationPlan {
        val mode = channel.copy(notificationMode = SubscriptionNotificationMode.Personalized)
        return SubscriptionNotificationPolicy.plan(mode, mode, previous, uploads, history, now, true)
    }

    private fun video(id: String, published: Long) = VideoSummary(
        UploadVideoIdentity.url(id), "Title", "Creator", -1, -1, published, null, channel.url,
    )

    private fun watch(name: String = "Creator", uploaderUrl: String? = channel.url, at: Long = now) = WatchHistoryEntry(
        UploadVideoIdentity.url("watched0001"), "Watched", name, null, -1, at, uploaderUrl = uploaderUrl,
    )
}
