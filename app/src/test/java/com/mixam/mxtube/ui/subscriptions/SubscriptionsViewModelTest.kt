package com.mixam.mxtube.ui.subscriptions

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mixam.mxtube.data.local.SubscriptionNotificationMode
import com.mixam.mxtube.data.local.SubscriptionsFeedCache
import com.mixam.mxtube.data.local.SubscriptionsStore
import com.mixam.mxtube.data.subscriptions.SubscriptionChannelSession
import com.mixam.mxtube.data.subscriptions.SubscriptionsFeedRepository
import com.mixam.mxtube.data.youtube.ChannelRef
import com.mixam.mxtube.data.youtube.ChannelTabBatch
import com.mixam.mxtube.data.youtube.VideoSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val models = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun filteringAndNotificationChangesDoNotRefetchButProfilesAreObserved() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        store.subscribe(ref("one"), "One", null, now = 1L)
        store.subscribe(ref("two"), "Two", null, now = 2L)
        var opened = 0
        val repository = SubscriptionsFeedRepository { channel ->
            opened++
            singlePage(channel.key)
        }
        val model = model(store, repository)
        model.awaitState { !it.isRefreshing && it.videos.size == 2 }
        assertEquals(2, opened)

        model.selectChannel("one")
        assertEquals(listOf("one"), model.uiState.value.videos.map { it.url })
        model.selectChannel(null)
        assertEquals(2, model.uiState.value.videos.size)
        assertEquals(2, opened)

        store.setNotificationMode("one", SubscriptionNotificationMode.None)
        runCurrent()
        assertEquals(2, opened)
        store.updateProfile("one", "Updated", "https://avatar/one")
        model.awaitState { state -> state.channels.any { it.name == "Updated" && it.avatarUrl == "https://avatar/one" } }
        assertEquals(2, opened)
        assertEquals(2, model.uiState.value.videos.size)
    }

    @Test
    fun unsubscribeImmediatelyPrunesVideosResetsSelectionAndCancelsStaleRefresh() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        store.subscribe(ref("one"), "One", null, now = 1L)
        store.subscribe(ref("two"), "Two", null, now = 2L)
        val staleStarted = CompletableDeferred<Unit>()
        val siblingStarted = CompletableDeferred<Unit>()
        val staleCancelled = CompletableDeferred<Unit>()
        val staleRelease = CompletableDeferred<Unit>()
        val remainingRelease = CompletableDeferred<Unit>()
        val attempts = mutableMapOf<String, Int>()
        val repository = SubscriptionsFeedRepository { channel ->
            val attempt = attempts.getOrDefault(channel.key, 0) + 1
            attempts[channel.key] = attempt
            object : SubscriptionChannelSession {
                override suspend fun first(): ChannelTabBatch {
                    if (channel.key == "one" && attempt == 2) {
                        staleStarted.complete(Unit)
                        try {
                            staleRelease.await()
                        } finally {
                            staleCancelled.complete(Unit)
                        }
                    }
                    if (channel.key == "two" && attempt == 2) siblingStarted.complete(Unit)
                    if (channel.key == "two" && attempt == 3) remainingRelease.await()
                    return ChannelTabBatch(listOf(video(channel.key)), hasMore = false)
                }
                override suspend fun loadMore() = ChannelTabBatch(hasMore = false)
            }
        }
        val model = model(store, repository)
        model.awaitState { !it.isRefreshing && it.videos.size == 2 }
        model.selectChannel("one")
        model.refresh()
        staleStarted.await()
        siblingStarted.await()
        store.unsubscribe("one")
        val pruned = model.awaitState {
            it.channels.size == 1 && it.selectedChannelKey == null &&
                it.videos.map { video -> video.url } == listOf("two")
        }
        assertTrue(pruned.isRefreshing)
        staleCancelled.await()
        assertEquals(2, attempts["one"])
        remainingRelease.complete(Unit)
        model.awaitState { !it.isRefreshing && it.videos.map { video -> video.url } == listOf("two") }
        assertFalse(model.uiState.value.channels.any { it.key == "one" })
    }

    @Test
    fun offlineCacheIsFilteredAndSuccessfulRetryPrunesRemovedChannelsOnDisk() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        store.subscribe(ref("one"), "One", null, now = 1L)
        val cache = SubscriptionsFeedCache(File(folder.root, "feed.json"))
        cache.write(mapOf("one" to listOf(video("cached")), "removed" to listOf(video("removed"))))
        var offline = true
        val repository = SubscriptionsFeedRepository { _ ->
            if (offline) throw IOException("Offline")
            singlePage("fresh")
        }
        val model = SubscriptionsViewModel(store, cache, repository).also { retain(it) }
        val cached = model.awaitState { !it.isRefreshing && it.failedChannelKeys == setOf("one") }
        assertEquals(listOf("cached"), cached.videos.map { it.url })
        assertFalse(cached.error)
        offline = false
        model.retry()
        model.awaitState { !it.isRefreshing && it.videos.map { video -> video.url } == listOf("fresh") }
        assertTrue(cache.read(setOf("removed")).isEmpty())
    }

    @Test
    fun retryRepeatsFailedPaginationAtCapWithoutReopeningOrDuplicatingRequests() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        store.subscribe(ref("one"), "One", null, now = 1L)
        store.subscribe(ref("two"), "Two", null, now = 2L)
        val retryStarted = CompletableDeferred<Unit>()
        val retryRelease = CompletableDeferred<Unit>()
        val opened = mutableMapOf<String, Int>()
        val firstCalls = mutableMapOf<String, Int>()
        val moreCalls = mutableMapOf<String, Int>()
        val repository = SubscriptionsFeedRepository { channel ->
            opened[channel.key] = opened.getOrDefault(channel.key, 0) + 1
            object : SubscriptionChannelSession {
                override suspend fun first(): ChannelTabBatch {
                    firstCalls[channel.key] = firstCalls.getOrDefault(channel.key, 0) + 1
                    val dates = if (channel.key == "one") 1L..298L else 299L..299L
                    return ChannelTabBatch(
                        dates.map { video("video:$it").copy(uploadedAtEpochMillis = it) },
                        hasMore = true,
                    )
                }
                override suspend fun loadMore(): ChannelTabBatch {
                    val attempt = moreCalls.getOrDefault(channel.key, 0) + 1
                    moreCalls[channel.key] = attempt
                    if (channel.key == "one") {
                        return ChannelTabBatch(
                            listOf(video("video:300").copy(uploadedAtEpochMillis = 300L)),
                            hasMore = true,
                        )
                    }
                    if (attempt == 1) throw IOException("Try again")
                    retryStarted.complete(Unit)
                    retryRelease.await()
                    return ChannelTabBatch(
                        listOf(video("retry").copy(uploadedAtEpochMillis = 400L)),
                        hasMore = false,
                    )
                }
            }
        }
        val model = model(store, repository)
        val first = model.awaitState { !it.isRefreshing && it.videos.size == 299 }
        assertTrue(first.hasMore)
        model.loadMore()
        val failed = model.awaitState { !it.isLoadingMore && it.loadMoreError }
        assertEquals(300, failed.videos.size)
        assertFalse(failed.hasMore)
        assertFalse(failed.error)
        assertEquals(mapOf("one" to 1, "two" to 1), moreCalls)

        model.selectChannel("one")
        assertFalse(model.uiState.value.loadMoreError)
        model.selectChannel("two")
        assertTrue(model.uiState.value.loadMoreError)
        assertFalse(model.uiState.value.hasMore)
        model.selectChannel(null)
        model.loadMore()
        runCurrent()
        assertEquals(mapOf("one" to 1, "two" to 1), moreCalls)

        model.retry()
        assertTrue(model.uiState.value.isRefreshing)
        retryStarted.await()
        model.retry()
        model.loadMore()
        runCurrent()
        assertEquals(mapOf("one" to 1, "two" to 2), moreCalls)
        retryRelease.complete(Unit)
        val retried = model.awaitState { !it.isRefreshing && !it.loadMoreError && it.videos.firstOrNull()?.url == "retry" }
        assertFalse(retried.hasMore)
        assertEquals(300, retried.videos.size)
        assertEquals(listOf("retry") + (300L downTo 2L).map { "video:$it" }, retried.videos.map { it.url })
        assertEquals(mapOf("one" to 1, "two" to 1), opened)
        assertEquals(mapOf("one" to 1, "two" to 1), firstCalls)
        assertEquals(mapOf("one" to 1, "two" to 2), moreCalls)
    }

    private fun model(store: SubscriptionsStore, repository: SubscriptionsFeedRepository) =
        SubscriptionsViewModel(store, SubscriptionsFeedCache(File(folder.root, "feed.json")), repository)
            .also { retain(it) }

    private fun retain(model: SubscriptionsViewModel): SubscriptionsViewModel =
        ViewModelProvider.create(models, viewModelFactory { initializer { model } })[SubscriptionsViewModel::class]

    private suspend fun SubscriptionsViewModel.awaitState(
        predicate: (SubscriptionsUiState) -> Boolean,
    ): SubscriptionsUiState = withContext(Dispatchers.Default) {
        withTimeout(5_000L) { uiState.first(predicate) }
    }

    private fun ref(key: String) = ChannelRef(key, "https://www.youtube.com/@$key")

    private fun video(key: String) = VideoSummary(key, key, "Channel", 10L, 20L, 100L, null)

    private fun singlePage(key: String) = object : SubscriptionChannelSession {
        override suspend fun first() = ChannelTabBatch(listOf(video(key)), hasMore = false)
        override suspend fun loadMore() = ChannelTabBatch(hasMore = false)
    }
}
