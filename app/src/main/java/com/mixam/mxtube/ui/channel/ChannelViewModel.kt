package com.mixam.mxtube.ui.channel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mixam.mxtube.data.local.BlockedChannelsStore
import com.mixam.mxtube.data.local.SubscriptionNotificationMode
import com.mixam.mxtube.data.local.SubscriptionsStore
import com.mixam.mxtube.data.youtube.ChannelHeader
import com.mixam.mxtube.data.youtube.ChannelIdentity
import com.mixam.mxtube.data.youtube.ChannelPage
import com.mixam.mxtube.data.youtube.ChannelRef
import com.mixam.mxtube.data.youtube.ChannelTabBatch
import com.mixam.mxtube.data.youtube.PlaylistSummary
import com.mixam.mxtube.data.youtube.VideoSummary
import com.mixam.mxtube.data.youtube.YouTubeChannelTabSession
import com.mixam.mxtube.data.youtube.YouTubePlaylistSession
import com.mixam.mxtube.data.youtube.YouTubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs


enum class ChannelProfileTab { Home, Videos, Shorts, Lives, Playlists, Posts }

internal interface ChannelContentSession {
    suspend fun first(): ChannelTabBatch
    suspend fun loadMore(): ChannelTabBatch
}

data class ChannelUiState(
    val header: ChannelHeader? = null,
    val availableTabs: List<ChannelProfileTab> = listOf(ChannelProfileTab.Home),
    val selectedTab: ChannelProfileTab = ChannelProfileTab.Home,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: Boolean = false,
    val videos: List<VideoSummary> = emptyList(),
    val isLoadingVideos: Boolean = false,
    val videosError: Boolean = false,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val playlists: List<PlaylistSummary> = emptyList(),
    val isLoadingPlaylists: Boolean = false,
    val playlistsError: Boolean = false,
    val hasMorePlaylists: Boolean = false,
    val isLoadingMorePlaylists: Boolean = false,
    val selectedPlaylist: PlaylistSummary? = null,
    val playlistVideos: List<VideoSummary> = emptyList(),
    val isLoadingPlaylist: Boolean = false,
    val playlistError: Boolean = false,
    val playlistHasMore: Boolean = false,
    val isLoadingMorePlaylist: Boolean = false,
    val isSubscribed: Boolean = false,
    val notificationMode: SubscriptionNotificationMode = SubscriptionNotificationMode.Personalized,
    val isBlocked: Boolean = false,
)

class ChannelViewModel internal constructor(
    private val channelUrl: String,
    private val subscriptions: SubscriptionsStore,
    private val blockedChannels: BlockedChannelsStore,
    private val openTab: (ChannelPage, String) -> ChannelContentSession? = { page, tab ->
        page.tab(tab)?.asChannelContent()
    },
    private val openPlaylistSource: (String) -> ChannelContentSession = {
        YouTubeRepository().playlist(it).asChannelContent()
    },
    private val searchChannelVideos: suspend (ChannelHeader) -> List<VideoSummary> = { header ->
        YouTubeRepository().channelVideosByUploader(header)
    },
    private val searchChannelPlaylists: suspend (ChannelHeader) -> List<PlaylistSummary> = { header ->
        YouTubeRepository().channelPlaylistsBySearch(header)
    },
    private val openChannel: suspend (String) -> ChannelPage,
) : ViewModel() {
    constructor(
        channelUrl: String,
        subscriptions: SubscriptionsStore,
        blockedChannels: BlockedChannelsStore,
        repository: YouTubeRepository = YouTubeRepository(),
    ) : this(
        channelUrl = channelUrl,
        subscriptions = subscriptions,
        blockedChannels = blockedChannels,
        openPlaylistSource = { repository.playlist(it).asChannelContent() },
        openChannel = repository::channelPage,
        searchChannelVideos = repository::channelVideosByUploader,
        searchChannelPlaylists = repository::channelPlaylistsBySearch,
    )

    private val state = MutableStateFlow(ChannelUiState())
    val uiState = state.asStateFlow()
    private val channelKey = MutableStateFlow(ChannelIdentity.keyOf(channelUrl))
    private var page: ChannelPage? = null
    private var videosSession: ChannelContentSession? = null
    private var playlistsSession: ChannelContentSession? = null
    private var playlistSession: ChannelContentSession? = null
    private var videosLoaded = false
    private var videosFallback = false
    private var playlistsFallback = false
    private var playlistsLoaded = false
    private var loadJob: Job? = null
    private var videosJob: Job? = null
    private var playlistsJob: Job? = null
    private var playlistJob: Job? = null
    private var generation = 0L
    private var playlistGeneration = 0L

    init {
        viewModelScope.launch {
            subscriptions.snapshot()
            combine(subscriptions.state, channelKey) { stored, key ->
                stored.channels.firstOrNull { it.key == key }
            }.collect { entry ->
                state.update {
                    it.copy(
                        isSubscribed = entry != null,
                        notificationMode = entry?.notificationMode ?: SubscriptionNotificationMode.Personalized,
                    )
                }
            }
        }
        viewModelScope.launch {
            blockedChannels.snapshot()
            combine(blockedChannels.state, channelKey) { blocked, key ->
                key in blocked.keys
            }.collect { isBlocked ->
                state.update { it.copy(isBlocked = isBlocked) }
            }
        }
        load()
    }

    fun load() {
        loadJob?.cancel()
        videosJob?.cancel()
        playlistsJob?.cancel()
        val token = ++generation
        page = null
        videosSession = null
        playlistsSession = null
        videosLoaded = false
        videosFallback = false
        playlistsFallback = false
        playlistsLoaded = false
        state.update {
            it.copy(
                isLoading = it.header == null,
                isRefreshing = it.header != null,
                error = false,
                isLoadingVideos = true,
                videosError = false,
                hasMore = false,
                isLoadingMore = false,
                isLoadingPlaylists = false,
                playlistsError = false,
                hasMorePlaylists = false,
                isLoadingMorePlaylists = false,
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val loaded = openChannel(channelUrl)
                if (token != generation) return@launch
                page = loaded
                channelKey.value = loaded.header.key
                val hasVideoTab = ChannelTabs.VIDEOS in loaded.availableTabs
                videosFallback = !hasVideoTab
                videosLoaded = false
                playlistsFallback = ChannelTabs.PLAYLISTS !in loaded.availableTabs
                val tabs = buildList {
                    add(ChannelProfileTab.Home)
                    add(ChannelProfileTab.Videos)
                    add(ChannelProfileTab.Shorts)
                    add(ChannelProfileTab.Lives)
                    if (ChannelTabs.PLAYLISTS in loaded.availableTabs) add(ChannelProfileTab.Playlists)
                    add(ChannelProfileTab.Posts)
                }
                state.update {
                    val selected = it.selectedTab.takeIf(tabs::contains) ?: ChannelProfileTab.Home
                    it.copy(
                        header = loaded.header,
                        availableTabs = tabs,
                        selectedTab = selected,
                        isLoading = false,
                        isRefreshing = false,
                        videos = if (hasVideoTab) it.videos else emptyList(),
                        playlists = if (ChannelProfileTab.Playlists in tabs) it.playlists else emptyList(),
                        isLoadingVideos = selected in listOf(ChannelProfileTab.Home, ChannelProfileTab.Videos),
                        isLoadingPlaylists = ChannelProfileTab.Playlists in tabs && selected in listOf(ChannelProfileTab.Home, ChannelProfileTab.Playlists),
                    )
                }
                subscriptions.updateProfile(loaded.header.key, loaded.header.name, loaded.header.avatarUrl)
                if (token == generation) {
                    ensureSelectedContent()
                    if (playlistsFallback) loadFallbackPlaylists()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token != generation) return@launch
                Log.e("MxTubeChannel", "channel failed: $channelUrl", error)
                state.update {
                    it.copy(isLoading = false, isRefreshing = false, isLoadingVideos = false, isLoadingPlaylists = false, error = true)
                }
            }
        }
    }

    fun refresh() {
        if (state.value.selectedPlaylist != null) loadPlaylist()
        else load()
    }

    fun selectTab(tab: ChannelProfileTab) {
        if (tab !in state.value.availableTabs) return
        if (state.value.selectedTab == tab && state.value.selectedPlaylist == null) return
        if (state.value.selectedPlaylist != null) closePlaylist()
        state.update { it.copy(selectedTab = tab) }
        ensureSelectedContent()
    }

    private fun ensureSelectedContent() {
        val current = state.value
        if (current.selectedTab in listOf(ChannelProfileTab.Home, ChannelProfileTab.Videos) && ChannelProfileTab.Videos in current.availableTabs) {
            loadVideos()
        }
        if (current.selectedTab in listOf(ChannelProfileTab.Home, ChannelProfileTab.Playlists) && ChannelProfileTab.Playlists in current.availableTabs) {
            loadPlaylists()
        }
    }

    private fun loadVideos(force: Boolean = false, more: Boolean = false) {
        if (videosFallback) {
            loadFallbackVideos(force)
            return
        }
        val currentPage = page ?: return
        if (!force && (videosJob?.isActive == true || (!more && videosLoaded))) return
        if (more && (!state.value.hasMore || videosSession == null)) return
        if (force) videosJob?.cancel()
        val token = generation
        state.update { it.copy(isLoadingVideos = !more, isLoadingMore = more, videosError = false) }
        videosJob = viewModelScope.launch {
            try {
                val source = if (more) videosSession else openTab(currentPage, ChannelTabs.VIDEOS)
                val batch = if (more) source?.loadMore() else source?.first()
                if (token != generation) return@launch
                if (!more) videosSession = source
                val added = batch?.videos.orEmpty().map { video ->
                    video.copy(
                        uploader = video.uploader.ifBlank { currentPage.header.name },
                        uploaderUrl = video.uploaderUrl ?: currentPage.header.url,
                    )
                }
                videosLoaded = true
                state.update {
                    val videos = ((if (more) it.videos else emptyList()) + added).distinctBy { video -> video.url }
                    it.copy(videos = videos, isLoadingVideos = false, isLoadingMore = false, hasMore = batch?.hasMore == true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token != generation) return@launch
                Log.e("MxTubeChannel", "channel videos failed: $channelUrl", error)
                state.update { it.copy(isLoadingVideos = false, isLoadingMore = false, videosError = true) }
            }
        }
    }

    private fun loadFallbackVideos(force: Boolean = false) {
        val currentPage = page ?: return
        if (!force && (videosJob?.isActive == true || videosLoaded)) return
        if (force) videosJob?.cancel()
        val token = generation
        state.update { it.copy(isLoadingVideos = true, videosError = false) }
        videosJob = viewModelScope.launch {
            try {
                val found =
                    runCatching { searchChannelVideos(currentPage.header) }
                        .onFailure { Log.w("MxTubeChannel", "topic videos failed", it) }
                        .getOrDefault(emptyList())
                if (token != generation) return@launch
                videosLoaded = true
                state.update {
                    it.copy(
                        videos = found,
                        isLoadingVideos = false,
                        hasMore = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    private fun loadFallbackPlaylists(force: Boolean = false) {
        val currentPage = page ?: return
        if (!force && (playlistsJob?.isActive == true || playlistsLoaded)) return
        if (force) playlistsJob?.cancel()
        val token = generation
        state.update { it.copy(isLoadingPlaylists = true) }
        playlistsJob = viewModelScope.launch {
            try {
                val found =
                    runCatching { searchChannelPlaylists(currentPage.header) }
                        .onFailure { Log.w("MxTubeChannel", "topic playlists failed", it) }
                        .getOrDefault(emptyList())
                if (token != generation) return@launch
                playlistsLoaded = true
                state.update {
                    val tabs =
                        if (found.isNotEmpty() && ChannelProfileTab.Playlists !in it.availableTabs) {
                            val withoutPosts = it.availableTabs.filter { tab -> tab != ChannelProfileTab.Posts }
                            val posts = it.availableTabs.filter { tab -> tab == ChannelProfileTab.Posts }
                            withoutPosts + ChannelProfileTab.Playlists + posts
                        } else {
                            it.availableTabs
                        }
                    it.copy(
                        availableTabs = tabs,
                        playlists = found,
                        isLoadingPlaylists = false,
                        selectedTab = it.selectedTab.takeIf(tabs::contains) ?: it.selectedTab,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("MxTubeChannel", "topic playlists failed", error)
                state.update { it.copy(isLoadingPlaylists = false) }
            }
        }
    }

    private fun loadPlaylists(force: Boolean = false, more: Boolean = false) {
        if (playlistsFallback) {
            loadFallbackPlaylists(force)
            return
        }
        val currentPage = page ?: return
        if (!force && (playlistsJob?.isActive == true || (!more && playlistsLoaded))) return
        if (more && (!state.value.hasMorePlaylists || playlistsSession == null)) return
        if (force) playlistsJob?.cancel()
        val token = generation
        state.update { it.copy(isLoadingPlaylists = !more, isLoadingMorePlaylists = more, playlistsError = false) }
        playlistsJob = viewModelScope.launch {
            try {
                val source = if (more) playlistsSession else openTab(currentPage, ChannelTabs.PLAYLISTS)
                val batch = if (more) source?.loadMore() else source?.first()
                if (token != generation) return@launch
                if (!more) playlistsSession = source
                playlistsLoaded = true
                state.update {
                    val playlists = ((if (more) it.playlists else emptyList()) + batch?.playlists.orEmpty()).distinctBy { playlist -> playlist.url }
                    it.copy(playlists = playlists, isLoadingPlaylists = false, isLoadingMorePlaylists = false, hasMorePlaylists = batch?.hasMore == true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token != generation) return@launch
                Log.e("MxTubeChannel", "channel playlists failed: $channelUrl", error)
                state.update { it.copy(isLoadingPlaylists = false, isLoadingMorePlaylists = false, playlistsError = true) }
            }
        }
    }

    fun loadMore() {
        val current = state.value
        if (current.selectedPlaylist != null) {
            if (!current.isLoadingPlaylist && !current.isLoadingMorePlaylist && !current.playlistError) loadPlaylist(more = true)
            return
        }
        when (current.selectedTab) {
            ChannelProfileTab.Videos -> if (!current.isLoadingVideos && !current.isLoadingMore && !current.videosError) loadVideos(more = true)
            ChannelProfileTab.Playlists -> if (!current.isLoadingPlaylists && !current.isLoadingMorePlaylists && !current.playlistsError) loadPlaylists(more = true)
            ChannelProfileTab.Home -> {
                if (current.videos.isEmpty() && !current.isLoadingVideos && !current.isLoadingMore && !current.videosError) loadVideos(more = true)
                if (current.playlists.isEmpty() && !current.isLoadingPlaylists && !current.isLoadingMorePlaylists && !current.playlistsError) loadPlaylists(more = true)
            }
            ChannelProfileTab.Shorts, ChannelProfileTab.Lives, ChannelProfileTab.Posts -> {}
        }
    }

    fun retryVideos() {
        if (page == null) load()
        else loadVideos(force = true, more = state.value.videos.isNotEmpty() && videosSession != null && state.value.hasMore)
    }

    fun retryPlaylists() {
        if (page == null) load()
        else loadPlaylists(force = true, more = state.value.playlists.isNotEmpty() && playlistsSession != null && state.value.hasMorePlaylists)
    }

    fun openPlaylist(playlist: PlaylistSummary) {
        if (state.value.selectedPlaylist?.url == playlist.url) return
        playlistJob?.cancel()
        ++playlistGeneration
        playlistSession = null
        state.update {
            it.copy(
                selectedPlaylist = playlist,
                playlistVideos = emptyList(),
                isLoadingPlaylist = true,
                playlistError = false,
                playlistHasMore = false,
                isLoadingMorePlaylist = false,
            )
        }
        loadPlaylist()
    }

    fun closePlaylist() {
        playlistJob?.cancel()
        ++playlistGeneration
        playlistSession = null
        state.update {
            it.copy(selectedPlaylist = null, playlistVideos = emptyList(), isLoadingPlaylist = false, playlistError = false, playlistHasMore = false, isLoadingMorePlaylist = false)
        }
    }

    private fun loadPlaylist(more: Boolean = false) {
        val selected = state.value.selectedPlaylist ?: return
        if (more && (!state.value.playlistHasMore || playlistSession == null || playlistJob?.isActive == true)) return
        if (!more) playlistJob?.cancel()
        val token = if (more) playlistGeneration else ++playlistGeneration
        state.update { it.copy(isLoadingPlaylist = !more, isLoadingMorePlaylist = more, playlistError = false) }
        playlistJob = viewModelScope.launch {
            try {
                val source = if (more) playlistSession else openPlaylistSource(selected.url)
                val batch = if (more) source?.loadMore() else source?.first()
                if (token != playlistGeneration || state.value.selectedPlaylist?.url != selected.url) return@launch
                if (!more) playlistSession = source
                state.update {
                    val videos = ((if (more) it.playlistVideos else emptyList()) + batch?.videos.orEmpty()).distinctBy { video -> video.url }
                    it.copy(playlistVideos = videos, isLoadingPlaylist = false, isLoadingMorePlaylist = false, playlistHasMore = batch?.hasMore == true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token != playlistGeneration) return@launch
                Log.e("MxTubeChannel", "playlist failed: ${selected.url}", error)
                state.update { it.copy(isLoadingPlaylist = false, isLoadingMorePlaylist = false, playlistError = true) }
            }
        }
    }

    fun retryPlaylist() {
        loadPlaylist(more = state.value.playlistVideos.isNotEmpty() && playlistSession != null && state.value.playlistHasMore)
    }

    fun subscribe() {
        val header = state.value.header ?: return
        if (state.value.isSubscribed) return
        viewModelScope.launch {
            subscriptions.subscribe(ChannelRef(header.key, header.url), header.name, header.avatarUrl)
        }
    }

    fun unsubscribe() {
        val key = channelKey.value ?: return
        viewModelScope.launch { subscriptions.unsubscribe(key) }
    }

    fun setNotificationMode(mode: SubscriptionNotificationMode) {
        val key = channelKey.value ?: return
        viewModelScope.launch { subscriptions.setNotificationMode(key, mode) }
    }

    suspend fun block(): Boolean {
        val header = state.value.header ?: return false
        return try {
            blockedChannels.block(
                ChannelRef(header.key, header.url),
                header.name,
                header.avatarUrl,
                aliases = listOfNotNull(
                    ChannelIdentity.keyOf(channelUrl),
                    header.handle?.let { ChannelIdentity.keyOf("https://www.youtube.com/$it") },
                ).toSet(),
            )
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("MxTubeChannel", "block failed: ${header.key}", error)
            false
        }
    }

    suspend fun unblock(): Boolean {
        val header = state.value.header ?: return false
        return try {
            blockedChannels.unblock(header.key)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("MxTubeChannel", "unblock failed: ${header.key}", error)
            false
        }
    }
}

private fun YouTubeChannelTabSession.asChannelContent(): ChannelContentSession {
    val source = this
    return object : ChannelContentSession {
        override suspend fun first(): ChannelTabBatch = source.first()
        override suspend fun loadMore(): ChannelTabBatch = source.loadMore()
    }
}

private fun YouTubePlaylistSession.asChannelContent(): ChannelContentSession {
    val source = this
    return object : ChannelContentSession {
        override suspend fun first(): ChannelTabBatch = source.first().let { ChannelTabBatch(videos = it.videos, hasMore = it.hasMore) }
        override suspend fun loadMore(): ChannelTabBatch = source.loadMore().let { ChannelTabBatch(videos = it.videos, hasMore = it.hasMore) }
    }
}
