package com.m1xamm.mxtube

import com.m1xamm.mxtube.data.home.HomeFeedItem
import com.m1xamm.mxtube.data.local.SubscriptionNotificationMode
import com.m1xamm.mxtube.data.youtube.PlaybackInfo
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.ui.home.HomeUiState
import com.m1xamm.mxtube.ui.player.PlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedRecommendationsTest {
    private val blockedKey = "UC1111111111111111111111"
    private val blockedUrl = "https://www.youtube.com/channel/$blockedKey"
    private val blocked = video("blocked", blockedUrl)
    private val allowed = video("allowed", "https://www.youtube.com/channel/UC2222222222222222222222")
    private val unknown = video("unknown", null)
    private val invalid = video("invalid", "https://example.com/@blocked")

    @Test
    fun homeFiltersNormalizedChannelKeysAndKeepsRawItemsForUnblock() {
        val mobile = video("mobile", "https://m.youtube.com/channel/$blockedKey/videos?view=0")
        val handle = video("handle", "https://www.youtube.com/@Blocked/videos")
        val items = listOf(blocked, allowed, mobile, unknown, handle, invalid).map { HomeFeedItem(it, setOf("music")) }
        val raw = HomeUiState(
            items = items,
            selectedTopicId = "music",
            isLoading = false,
            hasMore = true,
            isLoadingMore = true,
            loadMoreError = true,
        )

        val filtered = raw.withoutBlockedChannels(setOf(blockedKey, "@blocked"))

        assertEquals(listOf(allowed, unknown, invalid), filtered.items.map { it.video })
        assertEquals(raw.copy(items = filtered.items), filtered)
        assertEquals(items, raw.items)
        assertEquals(raw, raw.withoutBlockedChannels(emptySet()))
    }

    @Test
    fun playerFiltersLoadingRelatedWithoutChangingSubscriptionState() {
        val raw = PlayerUiState(
            related = listOf(blocked, allowed, unknown, invalid),
            uploaderKey = blockedKey,
            isSubscribed = true,
            notificationMode = SubscriptionNotificationMode.All,
            isLoadingRelated = true,
            hasMoreRelated = true,
        )

        val filtered = raw.withoutBlockedChannels(setOf(blockedKey))

        assertTrue(filtered.isLoading)
        assertEquals(listOf(allowed, unknown, invalid), filtered.related)
        assertEquals(raw.copy(related = filtered.related), filtered)
        assertEquals(raw, raw.withoutBlockedChannels(emptySet()))
    }

    @Test
    fun playerKeepsCurrentPlaybackEvenWhenItsChannelIsBlocked() {
        val related = listOf(blocked, allowed)
        val info = PlaybackInfo(
            title = "Current video",
            uploader = "Blocked channel",
            viewCount = 10L,
            uploadedAtEpochMillis = null,
            thumbnailUrl = null,
            durationSeconds = 60L,
            qualities = emptyList(),
            defaultQualityIndex = 0,
            audioTracks = emptyList(),
            defaultAudioTrackIndex = -1,
            subtitles = emptyList(),
            related = related,
            uploaderUrl = blockedUrl,
        )
        val raw = PlayerUiState(
            isLoading = false,
            info = info,
            related = related,
            selectedQualityIndex = 2,
            selectedAudioTrackIndex = 1,
            selectedSubtitleIndex = 0,
            relatedError = true,
        )

        val filtered = raw.withoutBlockedChannels(setOf(blockedKey))

        assertEquals(listOf(allowed), filtered.related)
        assertSame(info, filtered.info)
        assertEquals(related, info.related)
        assertEquals(raw.copy(related = filtered.related), filtered)
        assertEquals(related, raw.related)
        assertEquals(raw, raw.withoutBlockedChannels(emptySet()))
    }

    private fun video(id: String, uploaderUrl: String?) = VideoSummary(
        url = "https://www.youtube.com/watch?v=$id",
        title = id,
        uploader = "Channel",
        durationSeconds = 60L,
        viewCount = 10L,
        uploadedAtEpochMillis = null,
        thumbnailUrl = null,
        uploaderUrl = uploaderUrl,
    )
}
