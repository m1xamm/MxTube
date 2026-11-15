@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.m1xamm.mxtube.ui.player

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.m1xamm.mxtube.R
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.playback.PlaybackHolder
import com.m1xamm.mxtube.ui.common.HeroSource
import com.m1xamm.mxtube.ui.common.PlayPauseIcon
import com.m1xamm.mxtube.ui.common.heroImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

private const val WINDOW_WIDTH_DP = 176f
private const val WINDOW_ASPECT = 9f / 16f
private const val EDGE_DP = 8f

@Composable
fun MiniPlayer(
    video: VideoSummary,
    bottomInset: Dp,
    onExpand: () -> Unit,
    onClose: () -> Unit,
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
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
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
        var position by remember { mutableStateOf<Offset?>(null) }
        val snapSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val current =
            (position ?: Offset(maxX, maxY)).let {
                Offset(it.x.coerceIn(minX, maxX), it.y.coerceIn(minY, maxY))
            }
        Surface(
            modifier =
                Modifier
                    .offset { IntOffset(current.x.roundToInt(), current.y.roundToInt()) }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = current
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
                                            (start.x + travel.x).coerceIn(minX, maxX),
                                            (start.y + travel.y).coerceIn(minY, maxY),
                                        )
                                    change.consume()
                                }
                            }
                            if (dragging) {
                                val dropped = position ?: start
                                val snapX = if (dropped.x < (minX + maxX) / 2f) minX else maxX
                                position = dropped
                                scope.launch {
                                    animate(
                                        initialValue = dropped.x,
                                        targetValue = snapX,
                                        animationSpec = snapSpec,
                                    ) { value, _ ->
                                        position = Offset(value, dropped.y)
                                    }
                                }
                            }
                        }
                    }
                    .size(width = WINDOW_WIDTH_DP.dp, height = (WINDOW_WIDTH_DP * WINDOW_ASPECT).dp)
                    .heroImage(HeroSource.miniKey(video.url)),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black,
            shadowElevation = 8.dp,
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
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
                        }
                    },
                    update = { view -> view.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
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
                    visible = controlsVisible,
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
                                controlsVisible = false
                            },
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .size(26.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shadowElevation = 3.dp,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                PlayPauseIcon(
                                    playing = playing,
                                    modifier = Modifier.size(14.dp),
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
                                    .padding(6.dp)
                                    .size(26.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shadowElevation = 3.dp,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close_rounded),
                                    contentDescription = stringResource(R.string.close),
                                    modifier = Modifier.size(14.dp),
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
