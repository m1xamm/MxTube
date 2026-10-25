package com.m1xamm.mxtube.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.ui.common.clusterCardColor
import com.m1xamm.mxtube.ui.common.clusterInnerColor
import com.m1xamm.mxtube.ui.common.compactCount
import com.m1xamm.mxtube.ui.common.formatDuration
import com.m1xamm.mxtube.ui.common.formatRelativeDate

@Composable
fun HomeVideoCard(
    video: VideoSummary,
    onClick: () -> Unit,
    topCorner: Dp = 28.dp,
    bottomCorner: Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    val placeholderColor = clusterInnerColor()
    val placeholder = ColorPainter(placeholderColor)
    val cardColor = clusterCardColor()
    val duration = formatDuration(video.durationSeconds)
    val views = if (video.viewCount > 0) compactCount(video.viewCount) else ""
    val uploaded = video.uploadedAtEpochMillis?.let(::formatRelativeDate).orEmpty()
    val meta =
        listOfNotNull(
            views.takeIf { it.isNotBlank() },
            uploaded.takeIf { it.isNotBlank() },
        ).joinToString(" • ")

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val animatedTopCorner by
        animateDpAsState(
            targetValue = if (pressed) 28.dp else topCorner,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "home-cluster-top-corner",
        )
    val animatedBottomCorner by
        animateDpAsState(
            targetValue = if (pressed) 28.dp else bottomCorner,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "home-cluster-bottom-corner",
        )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(
                topStart = animatedTopCorner,
                topEnd = animatedTopCorner,
                bottomStart = animatedBottomCorner,
                bottomEnd = animatedBottomCorner,
            ),
        color = cardColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(placeholderColor),
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = placeholder,
                    error = placeholder,
                    contentScale = ContentScale.Crop,
                )
                if (duration.isNotBlank()) {
                    Text(
                        text = duration,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = video.uploader,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (video.uploader.isNotBlank() && meta.isNotBlank()) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun HomeVideoCardSkeleton(
    alpha: Float,
    topCorner: Dp,
    bottomCorner: Dp,
    modifier: Modifier = Modifier,
) {
    val cardColor = clusterCardColor()
    val innerColor = clusterInnerColor().copy(alpha = alpha)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(
                topStart = topCorner,
                topEnd = topCorner,
                bottomStart = bottomCorner,
                bottomEnd = bottomCorner,
            ),
        color = cardColor,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(innerColor),
            )
            Spacer(Modifier.height(14.dp))
            SkeletonLine(widthFraction = 0.92f, color = innerColor)
            Spacer(Modifier.height(7.dp))
            SkeletonLine(widthFraction = 0.62f, color = innerColor)
            Spacer(Modifier.height(10.dp))
            SkeletonLine(widthFraction = 0.4f, color = innerColor, height = 10)
        }
    }
}

@Composable
private fun SkeletonLine(
    widthFraction: Float,
    color: Color,
    height: Int = 14,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(widthFraction)
                .height(height.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color),
    )
}
