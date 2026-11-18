package com.m1xamm.mxtube.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.m1xamm.mxtube.R
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.ui.common.clusterInnerColor
import com.m1xamm.mxtube.ui.common.formatDuration

data class PlaylistQueue(
    val title: String,
    val videos: List<VideoSummary>,
    val index: Int,
    val shuffled: Boolean = false,
    val loop: Boolean = false,
    val order: List<Int> = emptyList(),
) {
    val current: VideoSummary? get() = videos.getOrNull(index)

    val position: Int
        get() {
            val slot = order.indexOf(index)
            return if (slot >= 0) slot else index
        }

    val size: Int get() = if (order.isEmpty()) videos.size else order.size

    fun next(): PlaylistQueue? {
        val sequence = if (order.isEmpty()) videos.indices.toList() else order
        val slot = sequence.indexOf(index)
        if (slot < 0) return null
        if (slot == sequence.lastIndex) {
            if (!loop) return null
            val first = sequence.firstOrNull() ?: return null
            return copy(index = first)
        }
        return copy(index = sequence[slot + 1])
    }

    fun withShuffle(enabled: Boolean): PlaylistQueue {
        if (enabled) {
            val others = videos.indices.filter { it != index }.shuffled()
            return copy(shuffled = true, order = listOf(index) + others)
        }
        return copy(shuffled = false, order = emptyList())
    }
}

@Composable
fun PlaylistBar(
    queue: PlaylistQueue,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelect: (Int) -> Unit,
    onShuffle: () -> Unit,
    onLoop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sequence = if (queue.order.isEmpty()) queue.videos.indices.toList() else queue.order
    val slot = sequence.indexOf(queue.index).let { if (it >= 0) it else 0 }
    val nextVideo =
        sequence.getOrNull(slot + 1)?.let { queue.videos.getOrNull(it) }
            ?: if (queue.loop) sequence.firstOrNull()?.let { queue.videos.getOrNull(it) } else null
    val chevron by
        animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            label = "playlist-chevron",
        )
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleExpanded,
                        )
                        .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.playlist_queue_title, queue.title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text =
                            nextVideo?.title
                                ?: stringResource(R.string.playlist_queue_end),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${slot + 1}/${sequence.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more_rounded),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).rotate(chevron),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(160)),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(120)),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onShuffle) {
                            ShuffleIcon(
                                tint =
                                    if (queue.shuffled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onLoop) {
                            RepeatIcon(
                                tint =
                                    if (queue.loop) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            bottom = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(
                            sequence.mapNotNull { queue.videos.getOrNull(it)?.let { video -> it to video } },
                            key = { _, entry -> "queue_${entry.first}" },
                        ) { position, entry ->
                            val (originalIndex, video) = entry
                            QueueRow(
                                video = video,
                                playing = originalIndex == queue.index,
                                onClick = { onSelect(originalIndex) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShuffleIcon(tint: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val u = size.minDimension / 24f
        val stroke = 1.9f * u
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(x1 * u, y1 * u),
                end = androidx.compose.ui.geometry.Offset(x2 * u, y2 * u),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        line(3.5f, 7f, 7.5f, 7f)
        line(7.5f, 7f, 16.5f, 17f)
        line(16.5f, 17f, 20.5f, 17f)
        line(3.5f, 17f, 7.5f, 17f)
        line(7.5f, 17f, 16.5f, 7f)
        line(16.5f, 7f, 20.5f, 7f)
        line(18.4f, 4.9f, 20.6f, 7f)
        line(20.6f, 7f, 18.4f, 9.1f)
        line(18.4f, 14.9f, 20.6f, 17f)
        line(20.6f, 17f, 18.4f, 19.1f)
    }
}

@Composable
private fun RepeatIcon(tint: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val u = size.minDimension / 24f
        val stroke = 1.9f * u
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(x1 * u, y1 * u),
                end = androidx.compose.ui.geometry.Offset(x2 * u, y2 * u),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        line(6f, 8.5f, 17f, 8.5f)
        line(17f, 8.5f, 19f, 10.5f)
        line(19f, 10.5f, 19f, 14f)
        line(18f, 15.5f, 7f, 15.5f)
        line(7f, 15.5f, 5f, 13.5f)
        line(5f, 13.5f, 5f, 10f)
        line(14.6f, 6.4f, 17.2f, 8.5f)
        line(17.2f, 8.5f, 14.6f, 10.6f)
        line(9.4f, 13.4f, 6.8f, 15.5f)
        line(6.8f, 15.5f, 9.4f, 17.6f)
    }
}

@Composable
private fun QueueRow(
    video: VideoSummary,
    playing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(72.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(clusterInnerColor()),
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(40.dp),
            )
            val duration = formatDuration(video.durationSeconds)
            if (duration.isNotBlank()) {
                Text(
                    text = duration,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .background(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f),
                                RoundedCornerShape(5.dp),
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (playing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
