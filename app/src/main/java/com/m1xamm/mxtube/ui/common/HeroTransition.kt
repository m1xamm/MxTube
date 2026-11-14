package com.m1xamm.mxtube.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.m1xamm.mxtube.data.youtube.VideoSummary
import kotlin.math.roundToInt

data class HeroRects(val image: Rect, val title: Rect, val titleStyle: TextStyle)

object HeroSource {
    val bounds = mutableMapOf<String, HeroRects>()
    private val images = mutableMapOf<String, Rect>()
    private val titles = mutableMapOf<String, Rect>()
    private val styles = mutableMapOf<String, TextStyle>()

    fun image(url: String, rect: Rect) {
        images[url] = rect
        publish(url)
    }

    fun title(url: String, rect: Rect, style: TextStyle) {
        titles[url] = rect
        styles[url] = style
        publish(url)
    }

    private fun publish(url: String) {
        val i = images[url] ?: return
        val t = titles[url] ?: return
        val s = styles[url] ?: return
        bounds[url] = HeroRects(i, t, s)
    }
}

fun Modifier.heroImage(url: String): Modifier =
    this.onGloballyPositioned { HeroSource.image(url, it.boundsInWindow()) }

fun Modifier.heroTitle(url: String, style: TextStyle): Modifier =
    this.onGloballyPositioned { HeroSource.title(url, it.boundsInWindow(), style) }

@Composable
fun HeroOverlay(
    video: VideoSummary,
    source: HeroRects,
    targetImage: Rect,
    targetTitle: Rect,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(video.url) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        finished = true
        onDone()
    }
    if (finished) return
    val p = progress.value
    val targetStyle = MaterialTheme.typography.titleLarge
    val heroStyle =
        targetStyle.copy(
            fontSize = (source.titleStyle.fontSize.value + (targetStyle.fontSize.value - source.titleStyle.fontSize.value) * p).sp,
            lineHeight = (source.titleStyle.lineHeight.value + (targetStyle.lineHeight.value - source.titleStyle.lineHeight.value) * p).sp,
        )
    fun lerp(a: Rect, b: Rect): Rect =
        Rect(
            a.left + (b.left - a.left) * p,
            a.top + (b.top - a.top) * p,
            a.right + (b.right - a.right) * p,
            a.bottom + (b.bottom - a.bottom) * p,
        )
    val img = lerp(source.image, targetImage)
    val ttl = lerp(source.title, targetTitle)
    val corner = with(density) { (20.dp.toPx() * (1f - p)).coerceAtLeast(0f) }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - (p * p) }) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .offset { IntOffset(img.left.roundToInt(), img.top.roundToInt()) }
                .size(
                    with(density) { img.width.toDp() },
                    with(density) { img.height.toDp() },
                )
                .clip(RoundedCornerShape(corner)),
        )
        Text(
            video.title,
            modifier = Modifier
                .offset { IntOffset(ttl.left.roundToInt(), ttl.top.roundToInt()) }
                .size(with(density) { ttl.width.toDp() }, with(density) { ttl.height.toDp() }),
            style = heroStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
