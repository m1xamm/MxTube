@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.m1xamm.mxtube.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m1xamm.mxtube.R
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.ui.common.formatRelativeDate
import com.m1xamm.mxtube.ui.common.viewsText
import com.m1xamm.mxtube.ui.search.VideoCluster

@Composable
fun PlayerLoadingScreen(
    video: VideoSummary,
    related: List<VideoSummary>,
    onBack: () -> Unit,
    onRelatedClick: (VideoSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerInfoScreen(
        video = video,
        related = related,
        onBack = onBack,
        onRelatedClick = onRelatedClick,
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .size(60.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White,
            )
        }
    }
}

@Composable
fun PlayerOpenErrorScreen(
    video: VideoSummary,
    related: List<VideoSummary>,
    onBack: () -> Unit,
    onRelatedClick: (VideoSummary) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerInfoScreen(
        video = video,
        related = related,
        onBack = onBack,
        onRelatedClick = onRelatedClick,
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.player_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun PlayerInfoScreen(
    video: VideoSummary,
    related: List<VideoSummary>,
    onBack: () -> Unit,
    onRelatedClick: (VideoSummary) -> Unit,
    modifier: Modifier = Modifier,
    stageContent: @Composable () -> Unit,
) {
    val views = if (video.viewCount >= 0) viewsText(video.viewCount) else ""
    val uploaded = video.uploadedAtEpochMillis?.let(::formatRelativeDate).orEmpty()
    val meta =
        listOfNotNull(
            views.takeIf { it.isNotBlank() },
            uploaded.takeIf { it.isNotBlank() },
        ).joinToString(" • ")
    val channel = video.uploader.ifBlank { stringResource(R.string.unknown_channel) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back_rounded),
                    contentDescription = stringResource(R.string.player_back),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            stageContent()
        }

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "info_details") {
                Column {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = channel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (related.isNotEmpty()) {
                item(key = "info_related_header") {
                    Text(
                        text = stringResource(R.string.more_from_search),
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                item(key = "info_related_cluster") {
                    VideoCluster(
                        videos = related,
                        onVideoClick = onRelatedClick,
                    )
                }
            }
        }
    }
}
