package com.m1xamm.mxtube

import com.m1xamm.mxtube.data.home.HomeFeedEngine
import com.m1xamm.mxtube.data.home.HomeFeedItem
import com.m1xamm.mxtube.data.home.HomeTopics
import com.m1xamm.mxtube.data.local.HistoryState
import com.m1xamm.mxtube.data.local.SearchHistoryEntry
import com.m1xamm.mxtube.data.local.WatchHistoryEntry
import com.m1xamm.mxtube.data.youtube.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStageLogicTest {
    @Test
    fun dynamicTopicsRequireRepeatedShortSearches() {
        val history =
            HistoryState(
                searches =
                    listOf(
                        SearchHistoryEntry("TED", now, count = 2),
                        SearchHistoryEntry("Minecraft", now, count = 1),
                        SearchHistoryEntry("как выбрать ноутбук", now, count = 3),
                    ),
            )

        val topics = HomeTopics.ordered(history)

        assertEquals(HomeTopics.all.id, topics.first().id)
        assertTrue(topics.any { it.isDynamic && it.label == "TED" })
        assertFalse(topics.any { it.isDynamic && it.label == "Minecraft" })
        assertFalse(topics.any { it.isDynamic && it.label.startsWith("Как") })
        assertTrue(topics.any { it.id == "music" })
        assertTrue(topics.any { it.id == "gaming" })
        assertTrue(topics.any { it.id == "movies" })
    }

    @Test
    fun rankingStartsWithRelatedAndExcludesWatched() {
        val watched = video("watched", "Watched", "Channel A")
        val history =
            HistoryState(
                watches =
                    listOf(
                        WatchHistoryEntry(
                            url = watched.url,
                            title = watched.title,
                            uploader = watched.uploader,
                            thumbnailUrl = null,
                            durationSeconds = 100,
                            watchedAtEpochMillis = now,
                            topics = setOf("music"),
                        ),
                    ),
            )
        val related =
            listOf(
                video("rel-1", "Related 1", "Channel B"),
                video("rel-2", "Related 2", "Channel C"),
            )
        val discovery =
            listOf(
                HomeFeedItem(video("disc-1", "Discovery 1", "Channel D"), setOf("gaming")),
                HomeFeedItem(video("disc-2", "Discovery 2", "Channel E"), setOf("news")),
                HomeFeedItem(video("disc-3", "Discovery 3", "Channel F"), setOf("auto")),
                HomeFeedItem(video("disc-4", "Discovery 4", "Channel G"), setOf("travel")),
            )

        val result =
            HomeFeedEngine.rankInitial(
                history = history,
                relatedBySeed = mapOf(watched.url to related),
                discovery = discovery,
                limit = 8,
            )

        assertFalse(result.any { it.video.url == watched.url })
        assertEquals(related.first().url, result.first().video.url)
        assertTrue(result.any { it.video.url.contains("disc-") })
        assertEquals(result.size, result.distinctBy { it.video.url }.size)
    }

    private fun video(
        id: String,
        title: String,
        uploader: String,
    ): VideoSummary =
        VideoSummary(
            url = "https://youtube.com/watch?v=$id",
            title = title,
            uploader = uploader,
            durationSeconds = 120,
            viewCount = 1000,
            uploadedAtEpochMillis = null,
            thumbnailUrl = null,
        )

    private companion object {
        const val now = 1_000L
    }
}
