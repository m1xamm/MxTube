package com.m1xamm.mxtube

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.playback.PlaybackHolder
import com.m1xamm.mxtube.playback.PlaybackService
import com.m1xamm.mxtube.ui.player.PlayerScreen
import com.m1xamm.mxtube.ui.search.SearchScreen
import com.m1xamm.mxtube.ui.search.SearchViewModel

@Composable
fun MxTubeApp() {
    var selectedVideo by remember { mutableStateOf<VideoSummary?>(null) }
    var shownVideo by remember { mutableStateOf<VideoSummary?>(null) }
    val context = LocalContext.current
    val searchViewModel: SearchViewModel = viewModel { SearchViewModel() }
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        SearchScreen(
            viewModel = searchViewModel,
            listState = listState,
            onVideoClick = { video ->
                shownVideo = video
                selectedVideo = video
            },
        )

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
                        related =
                            searchState.videos
                                .asSequence()
                                .filter { it.url != video.url }
                                .take(8)
                                .toList(),
                        onRelatedClick = { next ->
                            shownVideo = next
                            selectedVideo = next
                        },
                        onBack = {
                            PlaybackHolder.stop()
                            PlaybackService.stop(context)
                            selectedVideo = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
