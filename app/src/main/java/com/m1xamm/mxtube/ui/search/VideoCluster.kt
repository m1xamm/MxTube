package com.m1xamm.mxtube.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.m1xamm.mxtube.data.youtube.VideoSummary

@Composable
fun VideoCluster(
    videos: List<VideoSummary>,
    onVideoClick: (VideoSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        videos.forEachIndexed { index, video ->
            VideoRow(
                video = video,
                onClick = { onVideoClick(video) },
                modifier = Modifier.fillMaxWidth(),
                topCorner = if (index == 0) 28.dp else 4.dp,
                bottomCorner = if (index == videos.lastIndex) 28.dp else 4.dp,
            )
        }
    }
}
