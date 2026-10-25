package com.m1xamm.mxtube.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m1xamm.mxtube.ui.common.clusterCardColor
import com.m1xamm.mxtube.ui.common.clusterInnerColor

@Composable
fun VideoRowSkeleton(
    alpha: Float,
    topCorner: Dp,
    bottomCorner: Dp,
    modifier: Modifier = Modifier,
) {
    val cardColor = clusterCardColor()
    val thumbColor = clusterInnerColor()

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
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(140.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(thumbColor),
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                SkeletonBar(widthFraction = 0.92f, height = 15.dp, alpha = alpha)
                Spacer(Modifier.height(6.dp))
                SkeletonBar(widthFraction = 0.64f, height = 15.dp, alpha = alpha)
                Spacer(Modifier.height(8.dp))
                SkeletonBar(widthFraction = 0.42f, height = 11.dp, alpha = alpha)
                Spacer(Modifier.height(5.dp))
                SkeletonBar(widthFraction = 0.56f, height = 11.dp, alpha = alpha)
            }
        }
    }
}

@Composable
private fun SkeletonBar(
    widthFraction: Float,
    height: Dp,
    alpha: Float,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(widthFraction)
                .height(height)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)),
    )
}
