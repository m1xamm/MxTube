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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m1xamm.mxtube.data.home.HomeTopics
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.playback.PlaybackHolder
import com.m1xamm.mxtube.playback.PlaybackService
import com.m1xamm.mxtube.ui.home.HomeViewModel
import com.m1xamm.mxtube.ui.nav.MainTab
import com.m1xamm.mxtube.ui.nav.MxBottomNav
import com.m1xamm.mxtube.ui.nav.SectionPlaceholder
import com.m1xamm.mxtube.ui.player.PlayerScreen
import com.m1xamm.mxtube.ui.search.SearchScreen
import com.m1xamm.mxtube.ui.search.SearchViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

@Composable
fun MxTubeApp() {
    var selectedVideo by remember { mutableStateOf<VideoSummary?>(null) }
    var shownVideo by remember { mutableStateOf<VideoSummary?>(null) }
    var playerRelated by remember { mutableStateOf<List<VideoSummary>>(emptyList()) }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var isSearchActive by remember { mutableStateOf(false) }
    val navHazeState = rememberHazeState()
    val context = LocalContext.current
    val application = context.applicationContext as MxTubeApplication
    val historyStore = application.historyStore
    val scope = rememberCoroutineScope()
    val homeViewModel: HomeViewModel =
        viewModel { HomeViewModel(application.historyStore, application.homeFeedCache) }
    val searchViewModel: SearchViewModel = viewModel { SearchViewModel(historyStore) }
    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val showNav = selectedVideo == null && !isSearchActive

    BackHandler(enabled = selectedTab != MainTab.Home && selectedVideo == null) {
        selectedTab = MainTab.Home
    }

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
                            playerRelated = related.filter { it.url != video.url }.take(8)
                            shownVideo = video
                            selectedVideo = video
                        },
                        onSearchActiveChange = { isSearchActive = it },
                    )

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
            visible = selectedVideo != null,
            modifier = Modifier.fillMaxSize(),
            enter =
                fadeIn(tween(180)) +
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                    ),
            exit =
                fadeOut(tween(140)) +
                    scaleOut(
                        targetScale = 0.98f,
                        animationSpec = tween(160),
                    ),
        ) {
            shownVideo?.let { video ->
                key(video.url) {
                    PlayerScreen(
                        video = video,
                        related = playerRelated,
                        onRelatedClick = { next ->
                            scope.launch {
                                historyStore.recordWatch(
                                    next,
                                    HomeTopics.topicsFor(null, next.title, next.uploader),
                                )
                                homeViewModel.onWatchRecorded()
                            }
                            playerRelated =
                                playerRelated.filter { it.url != next.url }.take(8)
                            shownVideo = next
                            selectedVideo = next
                        },
                        onBack = {
                            PlaybackHolder.stop()
                            PlaybackService.stop(context)
                            selectedVideo = null
                            homeViewModel.onHomeVisible()
                        },
                        onWatchProgress = { positionMs ->
                            scope.launch {
                                historyStore.updateWatchProgress(video.url, positionMs)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
