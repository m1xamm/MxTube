package com.m1xamm.mxtube

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.m1xamm.mxtube.data.home.HomeFeedItem
import com.m1xamm.mxtube.data.home.HomeFeedSession
import com.m1xamm.mxtube.data.local.BlockedChannelsStore
import com.m1xamm.mxtube.data.local.HomeFeedCache
import com.m1xamm.mxtube.data.local.LocalHistoryStore
import com.m1xamm.mxtube.data.local.SubscriptionsStore
import com.m1xamm.mxtube.data.local.UserPreferences
import com.m1xamm.mxtube.data.youtube.ChannelRef
import com.m1xamm.mxtube.data.youtube.PlaybackInfo
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.ui.home.HomeViewModel
import com.m1xamm.mxtube.ui.player.PlayerViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
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
import sun.misc.Unsafe
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class BlockedRecommendationPagingTest {
    @get:Rule val folder = TemporaryFolder()
    private val models = ViewModelStore()
    private val blockedKey = "UC1111111111111111111111"
    private val blockedUrl = "https://www.youtube.com/channel/$blockedKey"
    private val allowedUrl = "https://www.youtube.com/channel/UC2222222222222222222222"
    private val currentUrl = "https://www.youtube.com/watch?v=current"
    private val context by lazy {
        object : ContextWrapper(null) {
            override fun getFilesDir(): File = folder.root
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                Proxy.newProxyInstance(
                    SharedPreferences::class.java.classLoader,
                    arrayOf(SharedPreferences::class.java),
                ) { _, method, args ->
                    check(method.name == "getString")
                    args?.get(1)
                } as SharedPreferences
        }
    }

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        UserPreferences.init(context)
    }

    @After fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun homeAdvancesAllBlockedFirstPageUntilAllowedPageAndKeepsRawCache() = runTest {
        val blocked = blockedStore().also { block(it) }
        val first = video("first", blockedUrl)
        val hidden = video("hidden", blockedUrl)
        val allowed = video("allowed")
        val session = FeedSession(listOf(first), listOf(listOf(hidden), listOf(allowed), listOf(video("later"))))
        val cache = homeCache()
        val model = homeModel(blocked, session, cache)

        val visible = model.uiState.awaitState { it.items.map { item -> item.video } == listOf(allowed) }
        assertTrue(visible.hasMore)
        assertEquals(2, session.moreCalls)
        assertEquals(1, session.openCalls)

        blocked.unblock(blockedKey)
        model.uiState.awaitState { it.items.size == 3 }
        assertEquals(listOf(first, hidden, allowed), model.uiState.value.items.map { it.video })
        assertEquals(2, session.moreCalls)
        assertEquals(1, session.openCalls)
        withContext(Dispatchers.Default) {
            withTimeout(5_000L) {
                while (cache.read() == null) kotlinx.coroutines.yield()
            }
        }
        assertEquals(listOf(first), cache.read()?.items?.map { it.video })
    }

    @Test
    fun homeBlockChangeAdvancesExistingSessionWithoutReopening() = runTest {
        val blocked = blockedStore()
        val first = video("first", blockedUrl)
        val allowed = video("allowed")
        val session = FeedSession(listOf(first), listOf(listOf(allowed)))
        val model = homeModel(blocked, session)
        model.uiState.awaitState { !it.isLoading && it.items.isNotEmpty() }
        assertEquals(0, session.moreCalls)

        block(blocked)
        val visible = model.uiState.awaitState { it.items.map { item -> item.video } == listOf(allowed) }
        assertFalse(visible.hasMore)
        assertEquals(1, session.moreCalls)
        assertEquals(1, session.openCalls)

        blocked.unblock(blockedKey)
        model.uiState.awaitState { it.items.size == 2 }
        assertEquals(listOf(first, allowed), model.uiState.value.items.map { it.video })
        assertEquals(1, session.moreCalls)
    }

    @Test
    fun homeAllFilteredPaginationErrorWaitsForExplicitRetry() = runTest {
        val blocked = blockedStore().also { block(it) }
        val session = FeedSession(listOf(video("first", blockedUrl)), listOf(listOf(video("allowed"))))
            .apply { failMore = true }
        val model = homeModel(blocked, session)
        model.uiState.awaitState { it.loadMoreError }
        assertEquals(1, session.moreCalls)

        blocked.unblock(blockedKey)
        model.uiState.awaitState { it.items.isNotEmpty() }
        block(blocked)
        model.uiState.awaitState { it.items.isEmpty() && it.loadMoreError }
        model.loadMore()
        runCurrent()
        assertEquals(1, session.moreCalls)

        model.retryLoadMore()
        model.uiState.awaitState { it.items.isNotEmpty() && !it.loadMoreError }
        assertEquals(2, session.moreCalls)
        assertEquals(1, session.openCalls)
    }

    @Test
    fun playerReopensPagingAtRawCapAfterBlockChangeAndCapsOnlyVisibleItems() = runTest {
        val blocked = blockedStore()
        val initial = List(239) { video("hidden$it", blockedUrl) } + video("visible")
        var calls = 0
        val model = playerModel(blocked, initial) {
            calls++
            if (calls == 1) listOf(video("extraHidden", blockedUrl), video("extraVisible"))
            else List(238) { video("page$it") }
        }
        val capped = model.uiState.awaitState { it.info != null }
        assertEquals(240, capped.related.size)
        assertFalse(capped.hasMoreRelated)
        model.loadMoreRelated()
        runCurrent()
        assertEquals(0, calls)

        block(blocked)
        model.uiState.awaitState { it.related.size == 1 && it.hasMoreRelated }
        model.loadMoreRelated()
        val paged = model.uiState.awaitState { it.related.size == 2 && !it.isLoadingRelated }
        assertTrue(paged.hasMoreRelated)
        model.loadMoreRelated()
        val full = model.uiState.awaitState { it.related.size == 240 && !it.isLoadingRelated }
        assertFalse(full.hasMoreRelated)
        model.loadMoreRelated()
        runCurrent()
        assertEquals(2, calls)

        blocked.unblock(blockedKey)
        val restored = model.uiState.awaitState { it.related.size == 480 }
        assertFalse(restored.hasMoreRelated)
        assertEquals(initial, restored.info?.related)
        assertEquals(2, calls)
    }

    @Test
    fun playerAllBlockedRawCapAutoPagesOnlyAfterInfoAndNeverInParallel() = runTest {
        val blocked = blockedStore().also { block(it) }
        val initial = List(240) { video("hidden$it", blockedUrl) }
        val resolveStarted = CompletableDeferred<Unit>()
        val releaseInfo = CompletableDeferred<Unit>()
        val pageStarted = CompletableDeferred<Unit>()
        val releasePage = CompletableDeferred<Unit>()
        var calls = 0
        val model = retain(PlayerViewModel(
            videoUrl = currentUrl,
            initialRelated = initial,
            subscriptions = SubscriptionsStore(File(folder.root, "subscriptions.json")),
            blockedChannels = blocked,
            resolveVideo = {
                resolveStarted.complete(Unit)
                releaseInfo.await()
                playback(initial)
            },
            fetchRelated = {
                calls++
                if (calls == 1) {
                    pageStarted.complete(Unit)
                    releasePage.await()
                    listOf(video("extraHidden", blockedUrl))
                } else listOf(video("allowed"))
            },
        ))
        resolveStarted.await()
        model.loadMoreRelated()
        runCurrent()
        assertEquals(0, calls)

        releaseInfo.complete(Unit)
        pageStarted.await()
        model.uiState.awaitState { it.isLoadingRelated }
        model.loadMoreRelated()
        model.retryRelated()
        runCurrent()
        assertEquals(1, calls)

        releasePage.complete(Unit)
        val visible = model.uiState.awaitState { it.related.singleOrNull()?.title == "allowed" }
        assertTrue(visible.hasMoreRelated)
        assertEquals(2, calls)
        blocked.unblock(blockedKey)
        model.uiState.awaitState { it.related.size == 242 }
        assertEquals(2, calls)
    }

    @Test
    fun playerAllFilteredErrorDoesNotAutoRetryOnBlockChanges() = runTest {
        val blocked = blockedStore().also { block(it) }
        var calls = 0
        val model = playerModel(blocked, listOf(video("seed1", blockedUrl), video("seed2", blockedUrl))) {
            calls++
            if (calls == 1) throw IOException("Offline")
            listOf(video("allowed"))
        }
        model.uiState.awaitState { it.relatedError }
        blocked.unblock(blockedKey)
        model.uiState.awaitState { it.related.size == 2 }
        block(blocked)
        model.uiState.awaitState { it.related.isEmpty() && it.relatedError }
        model.loadMoreRelated()
        runCurrent()
        assertEquals(1, calls)

        model.retryRelated()
        model.uiState.awaitState { it.related.isNotEmpty() && !it.relatedError }
        assertEquals(2, calls)
    }

    @Test
    fun playerDoesNotAdvertiseExpandedDuplicateSeedsAfterBlockChanges() = runTest {
        val blocked = blockedStore().also { block(it) }
        val seed = video("seed", blockedUrl)
        var calls = 0
        val model = playerModel(blocked, listOf(seed, seed)) {
            calls++
            emptyList()
        }
        model.uiState.awaitState { it.info != null && !it.isLoadingRelated && !it.hasMoreRelated }
        assertEquals(1, calls)
        blocked.unblock(blockedKey)
        val visible = model.uiState.awaitState { it.related.isNotEmpty() }
        assertFalse(visible.hasMoreRelated)
        block(blocked)
        model.uiState.awaitState { it.related.isEmpty() }
        runCurrent()
        assertFalse(model.uiState.value.hasMoreRelated)
        assertEquals(1, calls)
    }

    private fun blockedStore() = BlockedChannelsStore(File(folder.root, "blocked.json"))

    private suspend fun block(store: BlockedChannelsStore) {
        store.block(ChannelRef(blockedKey, blockedUrl), "Blocked", null)
    }

    private fun homeModel(
        blocked: BlockedChannelsStore,
        session: FeedSession,
        cache: HomeFeedCache = homeCache(),
    ) = retain(HomeViewModel(
        historyStore = LocalHistoryStore(context),
        feedCache = cache,
        blockedChannels = blocked,
        openFeed = { _, _ -> session.apply { openCalls++ } },
    ))

    private fun playerModel(
        blocked: BlockedChannelsStore,
        initial: List<VideoSummary>,
        fetchRelated: suspend (String) -> List<VideoSummary>,
    ) = retain(PlayerViewModel(
        videoUrl = currentUrl,
        initialRelated = initial,
        subscriptions = SubscriptionsStore(File(folder.root, "subscriptions.json")),
        blockedChannels = blocked,
        resolveVideo = { playback(initial) },
        fetchRelated = fetchRelated,
    ))

    private inline fun <reified T : ViewModel> retain(model: T): T =
        ViewModelProvider.create(models, viewModelFactory { initializer { model } })[T::class]

    private suspend fun <T> StateFlow<T>.awaitState(predicate: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(5_000L) { first(predicate) } }

    private fun homeCache(): HomeFeedCache {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
            isAccessible = true
            get(null) as Unsafe
        }
        return (unsafe.allocateInstance(HomeFeedCache::class.java) as HomeFeedCache).apply {
            mapOf("file" to File(folder.root, "home-cache.json"), "localeTag" to "en", "mutex" to Mutex())
                .forEach { (name, value) ->
                    HomeFeedCache::class.java.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
                }
        }
    }

    private fun video(id: String, uploaderUrl: String = allowedUrl) = VideoSummary(
        url = "https://www.youtube.com/watch?v=$id",
        title = id,
        uploader = "Channel",
        durationSeconds = 60L,
        viewCount = 10L,
        uploadedAtEpochMillis = null,
        thumbnailUrl = null,
        uploaderUrl = uploaderUrl,
    )

    private fun playback(related: List<VideoSummary>) = PlaybackInfo(
        title = "Current",
        uploader = "Current channel",
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
    )

    private class FeedSession(
        initial: List<VideoSummary>,
        private val pages: List<List<VideoSummary>>,
    ) : HomeFeedSession {
        override val initialItems = initial.map { HomeFeedItem(it, emptySet()) }
        private var pageIndex = 0
        override val hasMore: Boolean get() = pageIndex < pages.size
        var openCalls = 0
        var moreCalls = 0
        var failMore = false

        override suspend fun loadMore(): List<HomeFeedItem> {
            moreCalls++
            if (failMore) {
                failMore = false
                throw IOException("Offline")
            }
            return pages[pageIndex++].map { HomeFeedItem(it, emptySet()) }
        }
    }
}
