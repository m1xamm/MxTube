package com.m1xamm.mxtube.data.local

import com.m1xamm.mxtube.data.youtube.VideoSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SubscriptionsFeedCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun roundtripPreservesAllVideoSummaryFieldsAndNulls() = runBlocking {
        val file = File(folder.root, "feed.json")
        val videos = listOf(
            VideoSummary("https://video/1", "Название", "Channel", 62L, 9_000_000_000L,
                123_456_789L, "https://image/1", "https://www.youtube.com/@one"),
            VideoSummary("https://video/2", "Unknown date", "", -1L, -1L, null, null, null),
        )
        SubscriptionsFeedCache(file).write(mapOf("one" to videos, "empty" to emptyList()))
        val result = SubscriptionsFeedCache(file).read(setOf("one", "empty"))
        assertEquals(videos, result.getValue("one"))
        assertTrue(result.getValue("empty").isEmpty())
    }

    @Test
    fun readOnlyReturnsActiveChannels() = runBlocking {
        val cache = SubscriptionsFeedCache(File(folder.root, "feed.json"))
        cache.write(mapOf("one" to listOf(video(1)), "removed" to listOf(video(2))))
        assertEquals(setOf("one"), cache.read(setOf("one")).keys)
        assertTrue(cache.read(emptySet()).isEmpty())
        assertTrue(cache.read(setOf("unknown")).isEmpty())
    }

    @Test
    fun successfulWritePrunesRemovedChannelsAndCanClearEmptyChannels() = runBlocking {
        val file = File(folder.root, "feed.json")
        val cache = SubscriptionsFeedCache(file)
        val previous = mapOf("one" to listOf(video(1)), "removed" to listOf(video(2)))
        cache.write(previous)
        cache.write(previous, activeChannelKeys = setOf("one"))
        assertTrue(SubscriptionsFeedCache(file).read(setOf("removed")).isEmpty())
        cache.write(mapOf("one" to emptyList()))
        assertTrue(cache.read(setOf("one")).getValue("one").isEmpty())
        cache.write(emptyMap(), emptySet())
        assertTrue(cache.read(setOf("one", "removed")).isEmpty())
    }

    @Test
    fun storesOnlyTheLatestThreeHundredVideosAcrossChannels() = runBlocking {
        val cache = SubscriptionsFeedCache(File(folder.root, "feed.json"))
        cache.write(mapOf(
            "one" to (1..200).map(::video),
            "two" to (201..400).map(::video),
        ))
        val result = cache.read(setOf("one", "two"))
        assertEquals(300, result.values.sumOf { it.size })
        assertEquals(101L, result.values.flatten().minOf { it.uploadedAtEpochMillis ?: 0L })
        assertEquals(400L, result.values.flatten().maxOf { it.uploadedAtEpochMillis ?: 0L })
    }

    @Test
    fun missingCorruptAndUnknownSchemaFilesAreEmpty() = runBlocking {
        val file = File(folder.root, "feed.json")
        val cache = SubscriptionsFeedCache(file)
        assertTrue(cache.read(setOf("one")).isEmpty())
        file.writeText("not json")
        assertTrue(cache.read(setOf("one")).isEmpty())
        file.writeText("""{"v":99,"channels":[]}""")
        assertTrue(cache.read(setOf("one")).isEmpty())
    }

    private fun video(number: Int) =
        VideoSummary("video:$number", "Video $number", "Channel", 10L, 20L, number.toLong(), null)
}
