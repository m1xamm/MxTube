package com.m1xamm.mxtube

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m1xamm.mxtube.data.home.HomeTopics
import com.m1xamm.mxtube.ui.common.HeroFrame
import com.m1xamm.mxtube.ui.common.HeroOverlay
import com.m1xamm.mxtube.ui.common.HeroSource
import com.m1xamm.mxtube.ui.common.LocalMiniPlayerVisible
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.playback.PlaybackHolder
import com.m1xamm.mxtube.playback.PlaybackService
import com.m1xamm.mxtube.ui.channel.ChannelScreen
import com.m1xamm.mxtube.ui.channel.ChannelViewModel
import com.m1xamm.mxtube.ui.home.HomeViewModel
import com.m1xamm.mxtube.ui.nav.MainTab
import com.m1xamm.mxtube.ui.nav.MxBottomNav
import com.m1xamm.mxtube.ui.nav.SectionPlaceholder
import com.m1xamm.mxtube.ui.player.MiniPlayer
import com.m1xamm.mxtube.ui.player.PlayerScreen
import com.m1xamm.mxtube.ui.search.SearchScreen
import com.m1xamm.mxtube.ui.search.SearchViewModel
import com.m1xamm.mxtube.ui.settings.SettingsScreen
import com.m1xamm.mxtube.ui.subscriptions.SubscriptionsScreen
import com.m1xamm.mxtube.ui.subscriptions.SubscriptionsViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

@Composable
fun MxTubeApp(
    notificationVideo: VideoSummary? = null,
    onNotificationConsumed: () -> Unit = {},
) {
    var selectedVideo by remember { mutableStateOf<VideoSummary?>(null) }
    var shownVideo by remember { mutableStateOf<VideoSummary?>(null) }
    var miniVideo by remember { mutableStateOf<VideoSummary?>(null) }
    var playerRelated by remember { mutableStateOf<List<VideoSummary>>(emptyList()) }
    var miniRelated by remember { mutableStateOf<List<VideoSummary>>(emptyList()) }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var isSearchActive by remember { mutableStateOf(false) }
    var playerFullscreen by rememberSaveable { mutableStateOf(false) }
    var selectedChannelUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var shownChannelUrl by remember { mutableStateOf(selectedChannelUrl) }
    var playerReturnChannel by rememberSaveable { mutableStateOf<String?>(null) }
    var channelSheetOpen by remember { mutableStateOf(false) }
    val channelStateHolder = rememberSaveableStateHolder()
    val navHazeState = rememberHazeState()
    val context = LocalContext.current
    val application = context.applicationContext as MxTubeApplication
    val historyStore = application.historyStore
    val scope = rememberCoroutineScope()
    val homeViewModel: HomeViewModel =
        viewModel { HomeViewModel(application.historyStore, application.homeFeedCache, application.blockedChannelsStore) }
    val searchViewModel: SearchViewModel = viewModel { SearchViewModel(historyStore) }
    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val subscriptionsListState = rememberLazyListState()
    var hero by remember { mutableStateOf<HeroFrame?>(null) }
    val density = LocalDensity.current
    val container = LocalWindowInfo.current.containerSize
    val statusTopPx =
        with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
    val navBarPx =
        with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
    val widthPx = container.width.toFloat()
    val playerCover = Rect(0f, statusTopPx, widthPx, statusTopPx + widthPx * 9f / 16f)
    val navBottomInset = 76.dp
    val bareBottomInset = 12.dp
    fun miniThumbRect(bottomInset: androidx.compose.ui.unit.Dp): Rect {
        val widthPx = with(density) { 176.dp.toPx() }
        val heightPx = widthPx * 9f / 16f
        val marginPx = with(density) { 8.dp.toPx() }
        val bottom = container.height - navBarPx - with(density) { bottomInset.toPx() }
        val top = bottom - heightPx
        return Rect(
            container.width - widthPx - marginPx,
            top,
            container.width - marginPx,
            bottom,
        )
    }
    val showNav = selectedVideo == null && selectedChannelUrl == null && !isSearchActive
    val miniVisible = miniVideo != null && selectedVideo == null && !channelSheetOpen
    val openChannel: (String) -> Unit = { url ->
        shownChannelUrl = url
        selectedChannelUrl = url
    }
    val openChannelFromPlayer: (String) -> Unit = { url ->
        shownVideo?.let { playing ->
            miniVideo = playing
            miniRelated = playerRelated
        }
        playerFullscreen = false
        playerReturnChannel = null
        selectedVideo = null
        openChannel(url)
    }
    val openPlayer: (VideoSummary, List<VideoSummary>, String?) -> Unit = { video, related, returnChannel ->
        miniVideo = null
        playerRelated = related.filter { it.url != video.url }.take(8)
        playerReturnChannel = returnChannel
        playerFullscreen = false
        shownVideo = video
        hero = HeroSource.get(video.url)?.let { HeroFrame(video, it, playerCover) }
        selectedVideo = video
    }
    val expandMini: () -> Unit = {
        val video = miniVideo
        if (video != null) {
            val from = HeroSource.get(HeroSource.miniKey(video.url)) ?: miniThumbRect(bareBottomInset)
            hero =
                HeroFrame(
                    video = video,
                    from = from,
                    to = playerCover,
                    fromCornerDp = 12f,
                    toCornerDp = 0f,
                )
            playerRelated = miniRelated
            playerReturnChannel = selectedChannelUrl
            playerFullscreen = false
            shownVideo = video
            miniVideo = null
            selectedVideo = video
        }
    }
    val closeMini: () -> Unit = {
        PlaybackHolder.stop()
        PlaybackService.stop(context)
        miniVideo = null
        miniRelated = emptyList()
    }
    val closePlayer: () -> Unit = {
        val playing = shownVideo
        if (playing != null) {
            val returnChannel = playerReturnChannel
            val bottomInset = if (returnChannel != null || isSearchActive) bareBottomInset else navBottomInset
            hero =
                HeroSource.get(HeroSource.playerKey(playing.url))?.let { from ->
                    HeroFrame(
                        video = playing,
                        from = from,
                        to = miniThumbRect(bottomInset),
                        fromCornerDp = 0f,
                        toCornerDp = 12f,
                    )
                }
            miniRelated = playerRelated
            miniVideo = playing
        }
        playerFullscreen = false
        selectedVideo = null
        playerReturnChannel?.let(openChannel)
        playerReturnChannel = null
        homeViewModel.onHomeVisible()
    }

    LaunchedEffect(notificationVideo) {
        notificationVideo?.let { video ->
            historyStore.recordWatch(video, HomeTopics.topicsFor(null, video.title, video.uploader))
            homeViewModel.onWatchRecorded()
            selectedTab = MainTab.Subscriptions
            isSearchActive = false
            selectedChannelUrl = null
            playerReturnChannel = null
            playerRelated = emptyList()
            playerFullscreen = false
            miniVideo = null
            shownVideo = video
            selectedVideo = video
            onNotificationConsumed()
        }
    }

    BackHandler(enabled = selectedTab != MainTab.Home && selectedVideo == null && selectedChannelUrl == null) {
        selectedTab = MainTab.Home
    }

    CompositionLocalProvider(LocalMiniPlayerVisible provides miniVisible) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
        Box(Modifier.fillMaxSize().hazeSource(navHazeState)) {
            when (selectedTab) {
                MainTab.Home ->
                    SearchScreen(
                        viewModel = searchViewModel,
                        homeViewModel = homeViewModel,
                        searchListState = searchListState,
                        homeListState = homeListState,
                        onVideoClick = { video, topics, sourceQuery, related ->
                            scope.launch {
                                historyStore.recordWatch(video, topics, sourceQuery)
                                homeViewModel.onWatchRecorded()
                            }
                            openPlayer(video, related, null)
                        },
                        onSearchActiveChange = { isSearchActive = it },
                    )

                MainTab.Subscriptions -> {
                    val subscriptionsViewModel: SubscriptionsViewModel =
                        viewModel {
                            SubscriptionsViewModel(application.subscriptionsStore, application.subscriptionsFeedCache)
                        }
                    SubscriptionsScreen(
                        viewModel = subscriptionsViewModel,
                        listState = subscriptionsListState,
                        onChannelClick = openChannel,
                        onVideoClick = { video, related ->
                            scope.launch {
                                historyStore.recordWatch(video, HomeTopics.topicsFor(null, video.title, video.uploader))
                                homeViewModel.onWatchRecorded()
                            }
                            openPlayer(video, related, null)
                        },
                    )
                }

                MainTab.Settings -> SettingsScreen()

                else -> SectionPlaceholder(tab = selectedTab)
            }
        }

        AnimatedVisibility(
            visible = showNav,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter =
                slideInVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    initialOffsetY = { it },
                ) + fadeIn(tween(160)),
            exit =
                slideOutVertically(
                    animationSpec = tween(160, easing = FastOutSlowInEasing),
                    targetOffsetY = { it },
                ) + fadeOut(tween(120)),
        ) {
            MxBottomNav(
                selected = selectedTab,
                onSelect = { tab ->
                    if (tab == MainTab.Home && selectedTab != MainTab.Home) {
                        homeViewModel.onHomeVisible()
                    }
                    selectedTab = tab
                },
                hazeState = navHazeState,
            )
        }

        AnimatedVisibility(
            visible = selectedChannelUrl != null,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { it / 12 }),
            exit = fadeOut(tween(140)),
        ) {
            shownChannelUrl?.let { url ->
                channelStateHolder.SaveableStateProvider(url) {
                    val channelViewModel: ChannelViewModel =
                        viewModel(key = "channel:$url") {
                            ChannelViewModel(url, application.subscriptionsStore, application.blockedChannelsStore)
                        }
                    ChannelScreen(
                        viewModel = channelViewModel,
                        onBack = { selectedChannelUrl = null },
                        onSheetVisibilityChange = { channelSheetOpen = it },
                        onVideoClick = { video, related ->
                            scope.launch {
                                historyStore.recordWatch(video, HomeTopics.topicsFor(null, video.title, video.uploader))
                                homeViewModel.onWatchRecorded()
                            }
                            openPlayer(video, related, null)
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedVideo != null,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(160)),
        ) {
            shownVideo?.let { video ->
                key(video.url) {
                    PlayerScreen(
                        video = video,
                        related = playerRelated,
                        onChannelClick = openChannelFromPlayer,
                        onRelatedClick = { next ->
                            scope.launch {
                                historyStore.recordWatch(
                                    next,
                                    HomeTopics.topicsFor(null, next.title, next.uploader),
                                )
                                homeViewModel.onWatchRecorded()
                            }
                            playerRelated = emptyList()
                            shownVideo = next
                            selectedVideo = next
                        },
                        onBack = closePlayer,
                        onWatchProgress = { positionMs ->
                            scope.launch {
                                historyStore.updateWatchProgress(video.url, positionMs)
                            }
                        },
                        startFullscreen = playerFullscreen,
                        onFullscreenChanged = { playerFullscreen = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (miniVisible) {
            miniVideo?.let { video ->
                MiniPlayer(
                    video = video,
                    bottomInset = if (showNav) navBottomInset else bareBottomInset,
                    onExpand = expandMini,
                    onClose = closeMini,
                )
            }
        }

        hero?.let { frame ->
            HeroOverlay(frame = frame, onDone = { hero = null })
        }
        }
    }
}
