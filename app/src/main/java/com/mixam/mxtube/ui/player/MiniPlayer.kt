@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mixam.mxtube.ui.player

import android.graphics.Bitmap
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mixam.mxtube.R
import com.mixam.mxtube.data.youtube.VideoSummary
import com.mixam.mxtube.playback.PlaybackHolder
import com.mixam.mxtube.ui.common.BridgeFrame
import com.mixam.mxtube.ui.common.PlayPauseIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

private const val WINDOW_WIDTH_DP = 176f
private const val WINDOW_ASPECT = 9f / 16f
private const val EDGE_DP = 8f

private data class MiniBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
)

@Composable
fun MiniPlayer(
    video: VideoSummary,
    bottomInset: Dp,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    initialPosition: Offset? = null,
    onPositionChange: (Offset) -> Unit = {},
    expandTarget: Rect? = null,
    onExpandLanded: (Bitmap?) -> Unit = {},
    collapseFrom: Rect? = null,
    onCollapseLanded: () -> Unit = {},
    bridgeFrame: Bitmap? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(context) { PlaybackHolder.player(context) }
    var playing by remember(player) { mutableStateOf(player.playWhenReady) }
    var buffering by remember(player) {
        mutableStateOf(
            player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_IDLE,
        )
    }
    var controlsVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val videoView = remember { mutableStateOf<PlayerView?>(null) }
    val expandProgress = remember { Animatable(0f) }
    val collapseProgress = remember(collapseFrom) { Animatable(if (collapseFrom == null) 1f else 0f) }
    val expandSpec = tween<Float>(380, easing = CubicBezierEasing(0.26f, 1.06f, 0.18f, 1f))
    val landExpand = rememberUpdatedState(onExpandLanded)
    val landCollapse = rememberUpdatedState(onCollapseLanded)
    LaunchedEffect(expandTarget) {
        if (expandTarget != null) {
            expandProgress.snapTo(0f)
            expandProgress.animateTo(1f, expandSpec)
            val texture = videoView.value?.videoSurfaceView as? TextureView
            val bitmap =
                texture?.takeIf { it.isAvailable }?.let { view ->
                    runCatching { view.bitmap }.getOrNull()
                }
            landExpand.value(bitmap)
        } else {
            expandProgress.snapTo(0f)
        }
    }
    LaunchedEffect(collapseFrom) {
        if (collapseFrom != null) {
            collapseProgress.snapTo(0f)
            collapseProgress.animateTo(1f, expandSpec)
            landCollapse.value()
        } else {
            collapseProgress.snapTo(1f)
        }
    }
    val navBarPx =
        with(density) {
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx()
        }
    val statusTopPx =
        with(density) {
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
        }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = player.playWhenReady
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    playing = playWhenReady
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    buffering =
                        playbackState == Player.STATE_BUFFERING ||
                            playbackState == Player.STATE_IDLE
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            delay(3000)
            controlsVisible = false
        }
    }
    val togglePlay: () -> Unit = {
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
            player.play()
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { WINDOW_WIDTH_DP.dp.toPx() }
        val heightPx = widthPx * WINDOW_ASPECT
        val edgePx = with(density) { EDGE_DP.dp.toPx() }
        val minX = edgePx
        val maxX = (with(density) { maxWidth.toPx() } - widthPx - edgePx).coerceAtLeast(minX)
        val minY = statusTopPx + edgePx
        val maxY =
            (with(density) { maxHeight.toPx() } - navBarPx -
                with(density) { bottomInset.toPx() } - heightPx)
                .coerceAtLeast(minY)
        var position by remember { mutableStateOf(initialPosition) }
        var snapJob by remember { mutableStateOf<Job?>(null) }
        val snapSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val bounds =
            rememberUpdatedState(
                MiniBounds(
                    minX = minX,
                    maxX = maxX,
                    minY = minY,
                    maxY = maxY,
                ),
            )
        Box(
            modifier =
                Modifier
                    .offset {
                        val b = bounds.value
                        val p = position ?: Offset(b.maxX, b.maxY)
                        IntOffset(
                            p.x.coerceIn(b.minX, b.maxX).roundToInt(),
                            p.y.coerceIn(b.minY, b.maxY).roundToInt(),
                        )
                    }                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            snapJob?.cancel()
                            val b = bounds.value
                            val start =
                                (position ?: Offset(b.maxX, b.maxY)).let {
                                    Offset(
                                        it.x.coerceIn(b.minX, b.maxX),
                                        it.y.coerceIn(b.minY, b.maxY),
                                    )
                                }
                            var travel = Offset.Zero
                            var dragging = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                travel += change.positionChange()
                                if (!dragging &&
                                    travel.getDistance() > viewConfiguration.touchSlop
                                ) {
                                    dragging = true
                                }
                                if (dragging) {
                                    position =
                                        Offset(
                                            (start.x + travel.x).coerceIn(b.minX, b.maxX),
                                            (start.y + travel.y).coerceIn(b.minY, b.maxY),
                                        )
                                    change.consume()
                                }
                            }
                            if (dragging) {
                                val dropped = position ?: start
                                val snapX = if (dropped.x < (b.minX + b.maxX) / 2f) b.minX else b.maxX
                                position = dropped
                                onPositionChange(dropped)
                                snapJob =
                                    scope.launch {
                                        animate(
                                            initialValue = dropped.x,
                                            targetValue = snapX,
                                            animationSpec = snapSpec,
                                        ) { value, _ ->
                                            position = Offset(value, dropped.y)
                                        }
                                        onPositionChange(Offset(snapX, dropped.y))
                                    }
                            }
                        }
                    }
                    .size(width = WINDOW_WIDTH_DP.dp, height = (WINDOW_WIDTH_DP * WINDOW_ASPECT).dp)
                    .graphicsLayer {
                        val target = expandTarget
                        if (target != null) {
                            val p = expandProgress.value.coerceIn(0f, 1f)
                            val current = position ?: Offset(bounds.value.maxX, bounds.value.maxY)
                            val currentCenter =
                                Offset(
                                    current.x + size.width / 2f,
                                    current.y + size.height / 2f,
                                )
                            val scale =
                                1f + (target.width / size.width - 1f) * p
                            translationX = (target.center.x - currentCenter.x) * p
                            translationY = (target.center.y - currentCenter.y) * p
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            clip = true
                            shape = RoundedCornerShape((12f * (1f - p) / scale).dp)
                        } else if (collapseFrom != null) {
                            val p = collapseProgress.value.coerceIn(0f, 1f)
                            val current = position ?: Offset(bounds.value.maxX, bounds.value.maxY)
                            val currentCenter =
                                Offset(
                                    current.x + size.width / 2f,
                                    current.y + size.height / 2f,
                                )
                            val startScale = collapseFrom.width / size.width
                            val scale = startScale + (1f - startScale) * p
                            translationX = (collapseFrom.center.x - currentCenter.x) * (1f - p)
                            translationY = (collapseFrom.center.y - currentCenter.y) * (1f - p)
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            clip = true
                            shape = RoundedCornerShape((12f * p / scale).dp)
                        }
                    },
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape =
                    if (expandTarget != null || collapseFrom != null) {
                        androidx.compose.ui.graphics.RectangleShape
                    } else {
                        RoundedCornerShape(12.dp)
                    },
                color = Color.Black,
                shadowElevation = if (expandTarget != null || collapseFrom != null) 0.dp else 8.dp,
            ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(
                        if (expandTarget != null || collapseFrom != null) {
                            androidx.compose.ui.graphics.RectangleShape
                        } else {
                            RoundedCornerShape(12.dp)
                        },
                    ),
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                AndroidView(
                    factory = { ctx ->
                        (android.view.LayoutInflater.from(ctx).inflate(R.layout.view_player, null) as PlayerView).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            isClickable = false
                            isFocusable = false
                            isFocusableInTouchMode = false
                            setOnTouchListener { _, _ -> false }
                            this.player = player
                            videoView.value = this
                        }
                    },
                    update = { view -> view.player = player },
                    onRelease = { view -> view.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
                BridgeFrame(bridgeFrame = bridgeFrame)
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (controlsVisible) onExpand() else controlsVisible = true
                            },
                )
                AnimatedVisibility(
                    visible = buffering,
                    enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = controlsVisible && expandTarget == null,
                    modifier = Modifier.zIndex(1f),
                    enter =
                        fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                            scaleIn(
                                initialScale = 0.88f,
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            ),
                    exit =
                        fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                            scaleOut(
                                targetScale = 0.88f,
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            ),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Surface(
                            onClick = {
                                togglePlay()
                                controlsVisible = true
                            },
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shadowElevation = 3.dp,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                PlayPauseIcon(
                                    playing = playing,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    contentDescription =
                                        stringResource(
                                            if (playing) {
                                                R.string.player_pause
                                            } else {
                                                R.string.player_play
                                            },
                                        ),
                                )
                            }
                        }
                        Surface(
                            onClick = onClose,
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shadowElevation = 3.dp,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close_rounded),
                                    contentDescription = stringResource(R.string.close),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}
