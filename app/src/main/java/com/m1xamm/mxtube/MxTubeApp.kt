package com.m1xamm.mxtube

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m1xamm.mxtube.data.home.HomeTopics
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
import com.m1xamm.mxtube.ui.player.PlaylistQueue
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
    var playerMorphTo by remember { mutableStateOf<Rect?>(null) }
    var playerBridge by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var miniBridge by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var miniExpandTarget by remember { mutableStateOf<Rect?>(null) }
    var miniCollapseFrom by remember { mutableStateOf<Rect?>(null) }
    var miniPosition by remember { mutableStateOf<Offset?>(null) }
    var pendingExpand by remember { mutableStateOf<VideoSummary?>(null) }
    var playerEnterFade by remember { mutableStateOf(true) }
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
    val closeMini: () -> Unit = {
        PlaybackHolder.stop()
        PlaybackService.stop(context)
        miniVideo = null
        miniRelated = emptyList()
        miniBridge = null
        miniPosition = null
        miniCollapseFrom = null
    }
    val finishPlayerMorph: (android.graphics.Bitmap?) -> Unit = { bitmap ->
        val playing = shownVideo
        if (playing != null) {
            miniBridge = bitmap
            miniVideo = playing
            miniRelated = playerRelated
            miniPosition = null
            miniCollapseFrom = playerCover
        }
        playerBridge = null
        playerMorphTo = null
        selectedVideo = null
    }

    val closePlayer: () -> Unit = {
        val playing = shownVideo
        if (playing != null) {
            val returnChannel = playerReturnChannel
            val bottomInset = if (returnChannel != null || isSearchActive) bareBottomInset else navBottomInset
            miniPosition = null
            playerMorphTo = miniThumbRect(bottomInset)
            playerReturnChannel?.let(openChannel)
        }
        playerFullscreen = false
        playerReturnChannel = null
        homeViewModel.onHomeVisible()
        if (playing == null) {
            selectedVideo = null
        }
    }
    val openChannelFromPlayer: (String) -> Unit = { url ->
        playerReturnChannel = url
        closePlayer()
    }
    var playlistQueue by remember { mutableStateOf<PlaylistQueue?>(null) }
    val openPlayer: (VideoSummary, List<VideoSummary>, String?) -> Unit = { video, related, returnChannel ->
        miniVideo = null
        miniBridge = null
        miniExpandTarget = null
        miniCollapseFrom = null
        pendingExpand = null
        playerEnterFade = true
        playlistQueue = null
        playerRelated = related.filter { it.url != video.url }.take(8)
        playerReturnChannel = returnChannel
        playerFullscreen = false
        playerBridge = null
        playerMorphTo = null
        shownVideo = video
        selectedVideo = video
    }
    val playQueueIndex: (Int, PlaylistQueue?) -> Unit = { index, source ->
        val queue = source ?: playlistQueue
        val video = queue?.videos?.getOrNull(index)
        if (queue != null && video != null) {
            scope.launch {
                historyStore.recordWatch(video, HomeTopics.topicsFor(null, video.title, video.uploader))
                homeViewModel.onWatchRecorded()
            }
            playlistQueue = queue.copy(index = index)
            playerRelated = queue.videos.filter { it.url != video.url }.take(8)
            playerMorphTo = null
            playerBridge = null
            shownVideo = video
            selectedVideo = video
        }
    }
    val startMiniExpand: () -> Unit = {
        val video = miniVideo
        if (video != null && miniExpandTarget == null) {
            miniCollapseFrom = null
            pendingExpand = video
            miniExpandTarget = playerCover
        }
    }
    val finishMiniExpand: (android.graphics.Bitmap?) -> Unit = { bitmap ->
        val video = pendingExpand
        if (video != null) {
            playerEnterFade = false
            playerBridge = bitmap
            playerMorphTo = null
            playerRelated = miniRelated
            playerReturnChannel = selectedChannelUrl
            playerFullscreen = false
            shownVideo = video
            selectedVideo = video
            miniVideo = null
        }
        miniExpandTarget = null
        miniCollapseFrom = null
        pendingExpand = null
    }
    LaunchedEffect(miniBridge) {
        if (miniBridge != null) {
            kotlinx.coroutines.delay(180)
            miniBridge = null
        }
    }
    LaunchedEffect(playerBridge) {
        if (playerBridge != null) {
            kotlinx.coroutines.delay(180)
            playerBridge = null
        }
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
            playerMorphTo = null
            playerBridge = null
            miniBridge = null
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
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    slideInHorizontally(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                    ) { width -> direction * width } togetherWith
                        slideOutHorizontally(
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                        ) { width -> -direction * width }
                },
                label = "main-tabs",
            ) { tab ->
            when (tab) {
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
                        onPlaylistQueue = { queue ->
                            val video = queue.current
                            if (video != null) {
                                scope.launch {
                                    historyStore.recordWatch(video, HomeTopics.topicsFor(null, video.title, video.uploader))
                                    homeViewModel.onWatchRecorded()
                                }
                                miniVideo = null
                                miniBridge = null
                                miniExpandTarget = null
                                miniCollapseFrom = null
                                pendingExpand = null
                                playerEnterFade = true
                                playerRelated = queue.videos.filter { it.url != video.url }.take(8)
                                playerReturnChannel = selectedChannelUrl
                                playerFullscreen = false
                                playerBridge = null
                                playerMorphTo = null
                                playlistQueue = queue
                                shownVideo = video
                                selectedVideo = video
                            }
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedVideo != null,
            modifier = Modifier.fillMaxSize(),
            enter =
                if (playerEnterFade) {
                    fadeIn(tween(200)) + scaleIn(initialScale = 0.94f, animationSpec = tween(280))
                } else {
                    androidx.compose.animation.EnterTransition.None
                },
            exit = if (miniVideo != null) androidx.compose.animation.ExitTransition.None else fadeOut(tween(160)),
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
                            playerMorphTo = null
                            playerBridge = null
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
                        playlistQueue = playlistQueue,
                        onQueueSelect = { index -> playQueueIndex(index, null) },
                        onQueueShuffle = { enabled -> playlistQueue = playlistQueue?.withShuffle(enabled) },
                        onQueueLoop = { enabled -> playlistQueue = playlistQueue?.copy(loop = enabled) },
                        onEnded = {
                            val next = playlistQueue?.next()
                            if (next != null) playQueueIndex(next.index, next)
                        },
                        morphTo = playerMorphTo,
                        bridgeFrame = playerBridge,
                        onMorphLanded = finishPlayerMorph,
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
                    onExpand = startMiniExpand,
                    onClose = closeMini,
                    initialPosition = miniPosition,
                    onPositionChange = { miniPosition = it },
                    expandTarget = miniExpandTarget,
                    onExpandLanded = finishMiniExpand,
                    collapseFrom = miniCollapseFrom,
                    onCollapseLanded = { miniCollapseFrom = null },
                    bridgeFrame = miniBridge,
                )
            }
        }
        }
    }
}
