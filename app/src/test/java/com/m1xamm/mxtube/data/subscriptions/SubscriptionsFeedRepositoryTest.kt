package com.m1xamm.mxtube.data.subscriptions

import com.m1xamm.mxtube.data.local.SubscriptionEntry
import com.m1xamm.mxtube.data.youtube.ChannelTabBatch
import com.m1xamm.mxtube.data.youtube.VideoSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsFeedRepositoryTest {
    @Test
    fun mergeIsChronologicalDeduplicatedAndStableForMissingDates() {
        val newestDuplicate = video("same", 500L).copy(title = "Newest")
        val batches = listOf(
            listOf(video("z", null), video("same", 100L), video("b", 200L)),
            listOf(video("a", 200L), newestDuplicate, video("c", null), video("", 900L)),
        )
        val merged = mergeSubscriptionsVideos(batches)
        assertEquals(listOf("same", "a", "b", "c", "z"), merged.map { it.url })
        assertEquals("Newest", merged.first().title)
        assertEquals(merged, mergeSubscriptionsVideos(batches.reversed()))
        assertEquals(merged.take(2), mergeSubscriptionsVideos(batches, limit = 2))
    }

    @Test
    fun boundsRetainTheLatestThreeHundredVideos() {
        val bounded = boundSubscriptionsVideos(mapOf(
            "one" to (1L..400L).map { video("video:$it", it) },
            "empty" to emptyList(),
        ))
        assertEquals(300, bounded.values.sumOf { it.size })
        assertEquals(400L, bounded.getValue("one").first().uploadedAtEpochMillis)
        assertEquals(101L, bounded.getValue("one").last().uploadedAtEpochMillis)
        assertTrue(bounded.getValue("empty").isEmpty())
    }

    @Test
    fun fetchesEveryChannelWithFourConcurrentRequestsAndUploaderFallback() = runTest {
        var active = 0
        var maximum = 0
        val opened = mutableSetOf<String>()
        val entries = (1..9).map { entry(it.toString()) }
        val repository = SubscriptionsFeedRepository { channel ->
            active++
            maximum = maxOf(maximum, active)
            opened.add(channel.key)
            delay(10L)
            object : SubscriptionChannelSession {
                override suspend fun first(): ChannelTabBatch {
                    delay(10L)
                    active--
                    return ChannelTabBatch(listOf(video(channel.key, channel.key.toLong())), hasMore = false)
                }
                override suspend fun loadMore() = ChannelTabBatch(hasMore = false)
            }
        }
        val result = repository.open(entries).refresh()
        assertEquals(entries.map { it.key }.toSet(), opened)
        assertEquals(4, maximum)
        assertEquals((9 downTo 1).map { it.toString() }, result.videos().map { it.url })
        assertTrue(result.failures.isEmpty())
        entries.forEach { channel ->
            assertEquals(channel.url, result.videosByChannel.getValue(channel.key).single().uploaderUrl)
        }
    }

    @Test
    fun partialFailureKeepsOfflineVideosAndRetryOnlyRefetchesFailures() = runTest {
        val attempts = mutableMapOf<String, Int>()
        val repository = SubscriptionsFeedRepository { channel ->
            val attempt = attempts.getOrDefault(channel.key, 0) + 1
            attempts[channel.key] = attempt
            if (channel.key == "bad" && attempt == 1) throw IOException("Offline")
            object : SubscriptionChannelSession {
                override suspend fun first() =
                    ChannelTabBatch(listOf(video("new:${channel.key}", 200L)), hasMore = false)
                override suspend fun loadMore() = ChannelTabBatch(hasMore = false)
            }
        }
        val session = repository.open(
            listOf(entry("good"), entry("bad")),
            mapOf("bad" to listOf(video("cached", 100L)), "removed" to listOf(video("removed", 300L))),
        )
        val first = session.refresh()
        assertEquals(setOf("bad"), first.failures.keys)
        assertEquals(setOf("good"), first.successfulChannelKeys)
        assertEquals(listOf("new:good", "cached"), first.videos().map { it.url })
        val retried = session.retryFailed()
        assertTrue(retried.failures.isEmpty())
        assertEquals(1, attempts["good"])
        assertEquals(2, attempts["bad"])
        assertEquals(setOf("new:bad", "new:good"), retried.videos().map { it.url }.toSet())
    }

    @Test
    fun refreshRetainsFailedCacheWhenNewVideosFinishBeforeVanishedCachedVideos() = runTest {
        for (failedFirst in listOf(true, false)) {
            assertRefreshRetainsFailedCache(newVideosFirst = true, failedFirst = failedFirst)
        }
    }

    @Test
    fun refreshRetainsFailedCacheWhenVanishedCachedVideosFinishBeforeNewVideos() = runTest {
        for (failedFirst in listOf(true, false)) {
            assertRefreshRetainsFailedCache(newVideosFirst = false, failedFirst = failedFirst)
        }
    }

    @Test
    fun completedRefreshEnforcesGlobalLimitRegardlessOfCompletionOrder() = runTest {
        val expected = (400L downTo 101L).map { "video:$it" }
        for (firstChannel in listOf("a", "b")) {
            var moreCalls = 0
            val repository = SubscriptionsFeedRepository { channel ->
                object : SubscriptionChannelSession {
                    override suspend fun first(): ChannelTabBatch {
                        delay(if (channel.key == firstChannel) 10L else 20L)
                        val dates = if (channel.key == "a") 1L..200L else 201L..400L
                        return ChannelTabBatch(dates.map { video("video:$it", it) }, hasMore = true)
                    }
                    override suspend fun loadMore(): ChannelTabBatch {
                        moreCalls++
                        return ChannelTabBatch(hasMore = false)
                    }
                }
            }
            val session = repository.open(listOf(entry("a"), entry("b")))
            val result = session.refresh()
            assertEquals(300, result.videosByChannel.values.sumOf { it.size })
            assertEquals(expected, result.videos().map { it.url })
            assertEquals(100, result.videosByChannel.getValue("a").size)
            assertEquals(200, result.videosByChannel.getValue("b").size)
            assertEquals(setOf("a", "b"), result.moreChannelKeys)
            assertFalse(result.hasMore())
            assertFalse(result.hasMore("a"))
            assertFalse(result.hasMore("b"))
            assertEquals(result.videos(), session.loadMore().videos())
            assertEquals(0, moreCalls)
        }
    }

    @Test
    fun paginationEnforcesGlobalLimitAfterAppendingVideos() = runTest {
        val repository = SubscriptionsFeedRepository { _ ->
            object : SubscriptionChannelSession {
                override suspend fun first() =
                    ChannelTabBatch((1L..295L).map { video("video:$it", it) }, hasMore = true)
                override suspend fun loadMore() =
                    ChannelTabBatch((296L..315L).map { video("video:$it", it) }, hasMore = true)
            }
        }
        val session = repository.open(listOf(entry("one")))
        assertTrue(session.refresh().hasMore())
        val result = session.loadMore()
        assertEquals(300, result.videosByChannel.values.sumOf { it.size })
        assertEquals((315L downTo 16L).map { "video:$it" }, result.videos().map { it.url })
        assertFalse(result.hasMore())
    }

    @Test
    fun paginationContinuesThroughEmptyFirstAndFollowingPages() = runTest {
        var moreCalls = 0
        val repository = SubscriptionsFeedRepository { _ ->
            object : SubscriptionChannelSession {
                override suspend fun first() = ChannelTabBatch(hasMore = true)
                override suspend fun loadMore(): ChannelTabBatch {
                    moreCalls++
                    return if (moreCalls == 1) ChannelTabBatch(hasMore = true)
                    else ChannelTabBatch(listOf(video("found", 100L)), hasMore = false)
                }
            }
        }
        val session = repository.open(listOf(entry("one")))
        val first = session.refresh()
        assertTrue(first.videos().isEmpty())
        assertTrue(first.hasMore())
        val emptyNext = session.loadMore()
        assertTrue(emptyNext.videos().isEmpty())
        assertTrue(emptyNext.hasMore())
        val result = session.loadMore()
        assertEquals(listOf("found"), result.videos().map { it.url })
        assertFalse(result.hasMore())
        assertEquals(2, moreCalls)
    }

    @Test
    fun paginationRetryUsesTheLiveSessionAndKeepsAlreadyLoadedVideos() = runTest {
        var opened = 0
        var firstCalls = 0
        var moreCalls = 0
        val repository = SubscriptionsFeedRepository { _ ->
            opened++
            object : SubscriptionChannelSession {
                override suspend fun first(): ChannelTabBatch {
                    firstCalls++
                    return ChannelTabBatch(listOf(video("new", 200L)), hasMore = true)
                }
                override suspend fun loadMore(): ChannelTabBatch {
                    moreCalls++
                    if (moreCalls == 1) throw IOException("Try again")
                    return ChannelTabBatch(listOf(video("old", 100L), video("new", 200L)), hasMore = false)
                }
            }
        }
        val session = repository.open(listOf(entry("one")))
        assertTrue(session.refresh().hasMore())
        val failed = session.loadMore("one")
        assertTrue(failed.failures.getValue("one").isLoadMore)
        assertEquals(listOf("new"), failed.videos().map { it.url })
        val retried = session.retryFailed()
        assertEquals(listOf("new", "old"), retried.videos().map { it.url })
        assertFalse(retried.hasMore())
        assertEquals(1, opened)
        assertEquals(1, firstCalls)
        assertEquals(2, moreCalls)
    }

    @Test
    fun paginationCanTargetOneChannelWithoutTouchingTheOthers() = runTest {
        val paged = mutableListOf<String>()
        val repository = SubscriptionsFeedRepository { channel ->
            object : SubscriptionChannelSession {
                override suspend fun first() = ChannelTabBatch(listOf(video(channel.key, 200L)), hasMore = true)
                override suspend fun loadMore(): ChannelTabBatch {
                    paged.add(channel.key)
                    return ChannelTabBatch(listOf(video("old:${channel.key}", 100L)), hasMore = false)
                }
            }
        }
        val session = repository.open(listOf(entry("a"), entry("b")))
        session.refresh()
        val result = session.loadMore("a")
        assertEquals(listOf("a"), paged)
        assertFalse(result.hasMore("a"))
        assertTrue(result.hasMore("b"))
    }

    private suspend fun assertRefreshRetainsFailedCache(newVideosFirst: Boolean, failedFirst: Boolean) {
        val completed = mutableListOf<String>()
        val cached = video("cached:failed", 100L)
        val repository = SubscriptionsFeedRepository { channel ->
            object : SubscriptionChannelSession {
                override suspend fun first(): ChannelTabBatch {
                    delay(when (channel.key) {
                        "new" -> if (newVideosFirst) 10L else 20L
                        "vanished" -> if (newVideosFirst) 20L else 10L
                        else -> if (failedFirst) 5L else 30L
                    })
                    completed.add(channel.key)
                    if (channel.key == "failed") throw IOException("Offline")
                    return if (channel.key == "vanished") ChannelTabBatch(hasMore = false)
                    else ChannelTabBatch(listOf(video("new:z", 2_000L), video("new:a", 2_000L)), hasMore = true)
                }
                override suspend fun loadMore() = ChannelTabBatch(hasMore = false)
            }
        }
        val result = repository.open(
            listOf(entry("new"), entry("vanished"), entry("failed")),
            mapOf(
                "vanished" to (1L..299L).map { video("cached:vanished:$it", 1_000L + it) },
                "failed" to listOf(cached),
                "removed" to listOf(video("cached:removed", 3_000L)),
            ),
        ).refresh()
        val successfulOrder = if (newVideosFirst) listOf("new", "vanished") else listOf("vanished", "new")
        assertEquals(if (failedFirst) listOf("failed") + successfulOrder else successfulOrder + "failed", completed)
        assertEquals(setOf("failed"), result.failures.keys)
        assertFalse(result.failures.getValue("failed").isLoadMore)
        assertEquals(setOf("new", "vanished"), result.successfulChannelKeys)
        assertEquals(listOf(cached), result.videosByChannel.getValue("failed"))
        assertTrue(result.videosByChannel.getValue("vanished").isEmpty())
        assertFalse("removed" in result.videosByChannel)
        assertEquals(3, result.videosByChannel.values.sumOf { it.size })
        assertEquals(listOf("new:a", "new:z", "cached:failed"), result.videos().map { it.url })
        assertTrue(result.hasMore())
    }

    private fun entry(key: String) = SubscriptionEntry(key, "https://www.youtube.com/@$key", key, null, 1L)

    private fun video(url: String, date: Long?) =
        VideoSummary(url, url, "", 60L, 10L, date, null)
}
