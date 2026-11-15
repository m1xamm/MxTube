@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.m1xamm.mxtube.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun BoxScope.ExpressiveRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val pull = state.distanceFraction.coerceIn(0f, 1f)
    val shown by
        animateFloatAsState(
            targetValue = if (isRefreshing) 1f else pull,
            animationSpec = if (isRefreshing) tween(180, easing = FastOutSlowInEasing) else snap(),
            label = "refresh-shown",
        )
    val density = LocalDensity.current
    val travel = with(density) { 40.dp.toPx() }
    Box(
        modifier =
            modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 4.dp)
                .offset { IntOffset(0, ((shown - 1f) * travel).roundToInt()) }
                .graphicsLayer {
                    alpha = shown
                    scaleX = 0.72f + 0.28f * shown
                    scaleY = 0.72f + 0.28f * shown
                },
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
