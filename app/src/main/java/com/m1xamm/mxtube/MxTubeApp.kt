package com.m1xamm.mxtube

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.ui.player.PlayerScreen
import com.m1xamm.mxtube.ui.search.SearchScreen
import com.m1xamm.mxtube.ui.search.SearchViewModel

@Composable
fun MxTubeApp() {
    var selectedVideo by remember { mutableStateOf<VideoSummary?>(null) }
    val searchViewModel: SearchViewModel = viewModel { SearchViewModel() }
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        SearchScreen(
            viewModel = searchViewModel,
            listState = listState,
            onVideoClick = { selectedVideo = it },
        )

        selectedVideo?.let { video ->
            PlayerScreen(
                video = video,
                onBack = { selectedVideo = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
