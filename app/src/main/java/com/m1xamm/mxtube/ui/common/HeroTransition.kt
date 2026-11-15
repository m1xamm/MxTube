package com.m1xamm.mxtube.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.m1xamm.mxtube.data.youtube.VideoSummary
import kotlin.math.roundToInt

data class HeroFrame(
    val video: VideoSummary,
    val from: Rect,
    val to: Rect,
    val fromCornerDp: Float = 20f,
    val toCornerDp: Float = 0f,
)

object HeroSource {
    private val bounds = mutableMapOf<String, Rect>()

    fun put(key: String, rect: Rect) {
        bounds[key] = rect
    }

    fun get(key: String): Rect? = bounds[key]

    fun playerKey(url: String): String = "player:$url"

    fun miniKey(url: String): String = "mini:$url"
}

fun Modifier.heroImage(key: String): Modifier =
    this.onGloballyPositioned { HeroSource.put(key, it.boundsInWindow()) }

@Composable
fun HeroOverlay(frame: HeroFrame, onDone: () -> Unit) {
    val density = LocalDensity.current
    val progress = remember(frame) { Animatable(0f) }
    LaunchedEffect(frame) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(420, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        )
        onDone()
    }
    val p = progress.value
    val img =
        Rect(
            frame.from.left + (frame.to.left - frame.from.left) * p,
            frame.from.top + (frame.to.top - frame.from.top) * p,
            frame.from.right + (frame.to.right - frame.from.right) * p,
            frame.from.bottom + (frame.to.bottom - frame.from.bottom) * p,
        )
    val corner = with(density) {
        (frame.fromCornerDp + (frame.toCornerDp - frame.fromCornerDp) * p).dp.toPx()
    }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = frame.video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .offset { IntOffset(img.left.roundToInt(), img.top.roundToInt()) }
                    .size(
                        with(density) { img.width.toDp() },
                        with(density) { img.height.toDp() },
                    )
                    .clip(RoundedCornerShape(with(density) { corner.toDp() })),
        )
    }
}
