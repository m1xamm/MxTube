package com.m1xamm.mxtube.ui.channel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.m1xamm.mxtube.data.local.SubscriptionNotificationMode
import com.m1xamm.mxtube.data.local.SubscriptionsStore
import com.m1xamm.mxtube.data.youtube.ChannelHeader
import com.m1xamm.mxtube.data.youtube.ChannelPage
import com.m1xamm.mxtube.data.youtube.ChannelTabBatch
import com.m1xamm.mxtube.data.youtube.PlaylistSummary
import com.m1xamm.mxtube.data.youtube.VideoSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelViewModelTest {
    @get:Rule val folder = TemporaryFolder()
    private val models = ViewModelStore()
    private val key = "UC1111111111111111111111"
    private val url = "https://www.youtube.com/channel/$key"

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun loadsRealHeaderFieldsAndHandlesNoVideoTab() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        val model = retain(ChannelViewModel(url, store) { page("Channel") })
        val state = model.awaitState { it.header != null && !it.isLoadingVideos }
        assertEquals("Channel", state.header?.name)
        assertEquals(1200L, state.header?.subscriberCount)
        assertEquals(listOf(ChannelProfileTab.Home), state.availableTabs)
        model.selectTab(ChannelProfileTab.Videos)
        assertEquals(ChannelProfileTab.Home, model.uiState.value.selectedTab)
        assertTrue(state.videos.isEmpty())
        assertFalse(state.hasMore)
        assertFalse(state.error)
    }

    @Test
    fun subscriptionAndBellSettingsPersistAndUnsubscribeUpdatesProfile() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        val model = retain(ChannelViewModel(url, store) { page("Channel") })
        model.awaitState { it.header != null && !it.isLoadingVideos }
        model.subscribe()
        model.awaitState { it.isSubscribed }
        model.setNotificationMode(SubscriptionNotificationMode.All)
        model.awaitState { it.notificationMode == SubscriptionNotificationMode.All }
        assertEquals(SubscriptionNotificationMode.All, SubscriptionsStore(File(folder.root, "subscriptions.json")).snapshot().channels.single().notificationMode)
        model.unsubscribe()
        model.awaitState { !it.isSubscribed }
        assertTrue(store.snapshot().channels.isEmpty())
    }

    @Test
    fun failedMetadataCanBeRetried() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        var fails = true
        val model = retain(ChannelViewModel(url, store) { if (fails) throw IOException("Offline") else page("Recovered") })
        model.awaitState { it.error }
        fails = false
        model.load()
        val state = model.awaitState { it.header?.name == "Recovered" && !it.isLoadingVideos }
        assertFalse(state.error)
    }

    @Test
    fun refreshingCancelsOldChannelLoad() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        val started = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        var calls = 0
        val model = retain(ChannelViewModel(url, store) {
            calls++
            if (calls == 1) {
                started.complete(Unit)
                try { released.await() } finally { cancelled.complete(Unit) }
                page("Stale")
            } else page("Latest")
        })
        started.await()
        model.load()
        cancelled.await()
        model.awaitState { it.header?.name == "Latest" && !it.isLoadingVideos }
        assertEquals(2, calls)
    }

    @Test
    fun showsOnlyServerTabsAndReusesLoadedContent() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        val videos = ContentSource(
            ChannelTabBatch(videos = listOf(video("homevideo01")), hasMore = true),
            listOf(ChannelTabBatch(videos = listOf(video("homevideo02")), hasMore = false)),
        )
        val playlists = ContentSource(ChannelTabBatch(playlists = listOf(playlist("playlist001")), hasMore = false))
        val opened = mutableListOf<String>()
        val model = retain(ChannelViewModel(
            url,
            store,
            openTab = { _, tab ->
                opened += tab
                when (tab) {
                    ChannelTabs.VIDEOS -> videos
                    ChannelTabs.PLAYLISTS -> playlists
                    else -> null
                }
            },
        ) { page("Channel", ChannelTabs.VIDEOS, ChannelTabs.PLAYLISTS) })
        val state = model.awaitState { it.header != null && !it.isLoadingVideos && !it.isLoadingPlaylists }
        assertEquals(listOf(ChannelProfileTab.Home, ChannelProfileTab.Videos, ChannelProfileTab.Playlists), state.availableTabs)
        assertEquals(1, state.videos.size)
        assertEquals(1, state.playlists.size)
        assertEquals("Channel", state.videos.single().uploader)
        assertEquals(url, state.videos.single().uploaderUrl)

        model.selectTab(ChannelProfileTab.Videos)
        model.selectTab(ChannelProfileTab.Home)
        model.selectTab(ChannelProfileTab.Playlists)
        assertEquals(1, videos.firstCalls)
        assertEquals(1, playlists.firstCalls)

        model.selectTab(ChannelProfileTab.Videos)
        model.loadMore()
        val paged = model.awaitState { it.videos.size == 2 && !it.isLoadingMore }
        assertFalse(paged.hasMore)
        assertEquals(1, videos.moreCalls)
        model.loadMore()
        assertEquals(1, videos.moreCalls)
        assertEquals(listOf(ChannelTabs.VIDEOS, ChannelTabs.PLAYLISTS), opened)
    }

    @Test
    fun videoPaginationErrorKeepsLoadedVideosAndRetriesContinuation() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        val videos = ContentSource(
            ChannelTabBatch(videos = listOf(video("retryvideo1")), hasMore = true),
            listOf(ChannelTabBatch(videos = listOf(video("retryvideo2")), hasMore = false)),
        ).apply { failMore = true }
        val model = retain(ChannelViewModel(url, store, openTab = { _, _ -> videos }) { page("Channel", ChannelTabs.VIDEOS) })
        model.awaitState { it.header != null && !it.isLoadingVideos }
        model.selectTab(ChannelProfileTab.Videos)
        model.loadMore()
        val failed = model.awaitState { it.videosError }
        assertEquals(listOf("retryvideo1"), failed.videos.map(::videoId))
        assertTrue(failed.hasMore)
        model.retryVideos()
        val recovered = model.awaitState { it.videos.size == 2 && !it.videosError }
        assertEquals(listOf("retryvideo1", "retryvideo2"), recovered.videos.map(::videoId))
        assertEquals(1, videos.firstCalls)
        assertEquals(2, videos.moreCalls)
    }

    @Test
    fun playlistDetailLoadsContinuationsAndBackKeepsCatalogue() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        val videos = ContentSource(ChannelTabBatch(videos = listOf(video("catalog0001")), hasMore = false))
        val playlistItem = playlist("detail00001")
        val playlists = ContentSource(ChannelTabBatch(playlists = listOf(playlistItem), hasMore = false))
        val detail = ContentSource(
            ChannelTabBatch(videos = listOf(video("detailvid01")), hasMore = true),
            listOf(ChannelTabBatch(videos = listOf(video("detailvid02")), hasMore = false)),
        )
        val model = retain(ChannelViewModel(
            url,
            store,
            openTab = { _, tab -> if (tab == ChannelTabs.VIDEOS) videos else playlists },
            openPlaylistSource = { detail },
        ) { page("Channel", ChannelTabs.VIDEOS, ChannelTabs.PLAYLISTS) })
        model.awaitState { it.header != null && !it.isLoadingVideos && !it.isLoadingPlaylists }
        model.openPlaylist(playlistItem)
        model.awaitState { it.selectedPlaylist == playlistItem && it.playlistVideos.size == 1 && !it.isLoadingPlaylist }
        model.loadMore()
        val paged = model.awaitState { it.playlistVideos.size == 2 && !it.isLoadingMorePlaylist }
        assertEquals(listOf("detailvid01", "detailvid02"), paged.playlistVideos.map(::videoId))
        assertFalse(paged.playlistHasMore)
        model.closePlaylist()
        val back = model.awaitState { it.selectedPlaylist == null }
        assertNull(back.selectedPlaylist)
        assertTrue(back.playlistVideos.isEmpty())
        assertEquals(listOf(playlistItem), back.playlists)
        assertEquals(1, back.videos.size)
        model.selectTab(ChannelProfileTab.Playlists)
        assertEquals(1, playlists.firstCalls)
        assertEquals(1, detail.firstCalls)
        assertEquals(1, detail.moreCalls)
    }

    @Test
    fun playlistPaginationErrorKeepsVideosAndRetriesContinuation() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        val playlistItem = playlist("retrylist01")
        val playlists = ContentSource(ChannelTabBatch(playlists = listOf(playlistItem), hasMore = false))
        val detail = ContentSource(
            ChannelTabBatch(videos = listOf(video("listretry01")), hasMore = true),
            listOf(ChannelTabBatch(videos = listOf(video("listretry02")), hasMore = false)),
        ).apply { failMore = true }
        val model = retain(ChannelViewModel(
            url,
            store,
            openTab = { _, _ -> playlists },
            openPlaylistSource = { detail },
        ) { page("Channel", ChannelTabs.PLAYLISTS) })
        model.awaitState { it.header != null && !it.isLoadingPlaylists }
        model.openPlaylist(playlistItem)
        model.awaitState { it.playlistVideos.size == 1 && !it.isLoadingPlaylist }
        model.loadMore()
        val failed = model.awaitState { it.playlistError }
        assertEquals(listOf("listretry01"), failed.playlistVideos.map(::videoId))
        assertTrue(failed.playlistHasMore)
        model.retryPlaylist()
        val recovered = model.awaitState { it.playlistVideos.size == 2 && !it.playlistError }
        assertEquals(listOf("listretry01", "listretry02"), recovered.playlistVideos.map(::videoId))
        assertEquals(2, detail.moreCalls)
    }

    @Test
    fun stalePlaylistResponseCannotOverwriteNewSelection() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        val first = playlist("stalelist01")
        val second = playlist("freshlist01")
        val playlists = ContentSource(ChannelTabBatch(playlists = listOf(first, second), hasMore = false))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val stale = BlockingSource(started, release, cancelled, ChannelTabBatch(videos = listOf(video("stalevideo1")), hasMore = false))
        val fresh = ContentSource(ChannelTabBatch(videos = listOf(video("freshvideo1")), hasMore = false))
        val model = retain(ChannelViewModel(
            url,
            store,
            openTab = { _, _ -> playlists },
            openPlaylistSource = { if (it == first.url) stale else fresh },
        ) { page("Channel", ChannelTabs.PLAYLISTS) })
        model.awaitState { it.header != null && !it.isLoadingPlaylists }
        model.openPlaylist(first)
        started.await()
        model.openPlaylist(second)
        cancelled.await()
        val state = model.awaitState { it.selectedPlaylist == second && it.playlistVideos.size == 1 && !it.isLoadingPlaylist }
        release.complete(Unit)
        assertEquals(listOf("freshvideo1"), state.playlistVideos.map(::videoId))
        assertEquals(1, fresh.firstCalls)
    }

    @Test
    fun refreshCancelsStaleVideoContinuation() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val initial = BlockingMoreSource(
            ChannelTabBatch(videos = listOf(video("initial0001")), hasMore = true),
            started,
            release,
            cancelled,
            ChannelTabBatch(videos = listOf(video("stalepage01")), hasMore = false),
        )
        val latest = ContentSource(ChannelTabBatch(videos = listOf(video("latest00001")), hasMore = false))
        var tabCalls = 0
        var channelCalls = 0
        val model = retain(ChannelViewModel(
            url,
            store,
            openTab = { _, _ ->
                tabCalls++
                if (tabCalls == 1) initial else latest
            },
        ) {
            channelCalls++
            page(if (channelCalls == 1) "Channel" else "Latest", ChannelTabs.VIDEOS)
        })
        model.awaitState { it.header != null && !it.isLoadingVideos }
        model.selectTab(ChannelProfileTab.Videos)
        model.loadMore()
        started.await()
        model.load()
        cancelled.await()
        val state = model.awaitState { it.header?.name == "Latest" && !it.isLoadingVideos && !it.isLoadingMore }
        release.complete(Unit)
        assertEquals(listOf("latest00001"), state.videos.map(::videoId))
        assertFalse(state.hasMore)
        assertEquals(1, latest.firstCalls)
    }

    @Test
    fun homeOnlyFollowsContinuationForEmptyPreview() = runTest {
        val store = SubscriptionsStore(File(folder.root, "subscriptions.json"))
        val videos = ContentSource(
            ChannelTabBatch(videos = emptyList(), hasMore = true),
            listOf(ChannelTabBatch(videos = listOf(video("homeempty01")), hasMore = false)),
        )
        val playlists = ContentSource(
            ChannelTabBatch(playlists = listOf(playlist("homekeep001")), hasMore = true),
            listOf(ChannelTabBatch(playlists = listOf(playlist("homeextra01")), hasMore = false)),
        )
        val model = retain(ChannelViewModel(
            url,
            store,
            openTab = { _, tab -> if (tab == ChannelTabs.VIDEOS) videos else playlists },
        ) { page("Channel", ChannelTabs.VIDEOS, ChannelTabs.PLAYLISTS) })
        model.awaitState { it.header != null && !it.isLoadingVideos && !it.isLoadingPlaylists }
        model.loadMore()
        val state = model.awaitState { it.videos.size == 1 && !it.isLoadingMore }
        assertEquals(1, state.playlists.size)
        assertEquals(1, videos.moreCalls)
        assertEquals(0, playlists.moreCalls)
    }

    private fun page(name: String, vararg tabs: String) = ChannelPage(
        ChannelHeader(key, url, name, null, null, 1200L, "Description"),
        ServiceList.YouTube,
        tabs.map { tab -> ListLinkHandler("$url/$tab", "$url/$tab", key, listOf(tab), "") },
    )

    private fun video(id: String) = VideoSummary(
        url = "https://www.youtube.com/watch?v=${id.padEnd(11, '0').take(11)}",
        title = id,
        uploader = "",
        durationSeconds = 60L,
        viewCount = 10L,
        uploadedAtEpochMillis = null,
        thumbnailUrl = null,
    )

    private fun videoId(video: VideoSummary) = video.title

    private fun playlist(id: String) = PlaylistSummary(
        url = "https://www.youtube.com/playlist?list=$id",
        name = id,
        videoCount = 2L,
        thumbnailUrl = null,
        uploader = "Channel",
    )

    private fun retain(model: ChannelViewModel): ChannelViewModel =
        ViewModelProvider.create(models, viewModelFactory { initializer { model } })[ChannelViewModel::class]

    private suspend fun ChannelViewModel.awaitState(predicate: (ChannelUiState) -> Boolean): ChannelUiState =
        withContext(Dispatchers.Default) { withTimeout(5_000L) { uiState.first(predicate) } }

    private class ContentSource(
        private val initial: ChannelTabBatch,
        private val continuations: List<ChannelTabBatch> = emptyList(),
    ) : ChannelContentSession {
        var firstCalls = 0
        var moreCalls = 0
        var failMore = false
        private var moreIndex = 0

        override suspend fun first(): ChannelTabBatch {
            firstCalls++
            return initial
        }

        override suspend fun loadMore(): ChannelTabBatch {
            moreCalls++
            if (failMore) {
                failMore = false
                throw IOException("Continuation failed")
            }
            return continuations.getOrElse(moreIndex++) { ChannelTabBatch(hasMore = false) }
        }
    }

    private class BlockingSource(
        private val started: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>,
        private val cancelled: CompletableDeferred<Unit>,
        private val batch: ChannelTabBatch,
    ) : ChannelContentSession {
        override suspend fun first(): ChannelTabBatch = block()
        override suspend fun loadMore(): ChannelTabBatch = block()

        private suspend fun block(): ChannelTabBatch {
            started.complete(Unit)
            try {
                release.await()
            } catch (error: CancellationException) {
                cancelled.complete(Unit)
                throw error
            }
            return batch
        }
    }

    private class BlockingMoreSource(
        private val initial: ChannelTabBatch,
        private val started: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>,
        private val cancelled: CompletableDeferred<Unit>,
        private val batch: ChannelTabBatch,
    ) : ChannelContentSession {
        override suspend fun first(): ChannelTabBatch = initial

        override suspend fun loadMore(): ChannelTabBatch {
            started.complete(Unit)
            try {
                release.await()
            } catch (error: CancellationException) {
                cancelled.complete(Unit)
                throw error
            }
            return batch
        }
    }
}