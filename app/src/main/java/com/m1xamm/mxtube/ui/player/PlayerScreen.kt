package com.m1xamm.mxtube.ui.player

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.m1xamm.mxtube.R
import com.m1xamm.mxtube.data.youtube.PlaybackInfo
import com.m1xamm.mxtube.data.youtube.PlaybackQuality
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.ui.common.findActivity
import com.m1xamm.mxtube.ui.common.formatRelativeDate
import com.m1xamm.mxtube.ui.common.formatTime
import com.m1xamm.mxtube.ui.common.viewsText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PlayerScreen(
    video: VideoSummary,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(key = video.url) { PlayerViewModel(video.url) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val darkTheme = isSystemInDarkTheme()
    val exoPlayer = remember(context) { ExoPlayer.Builder(context).build() }

    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var settingsOpen by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var hasSource by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (settingsOpen) {
            settingsOpen = false
        } else if (isFullscreen) {
            isFullscreen = false
        } else {
            onBack()
        }
    }

    BackHandler { handleBack() }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(exoPlayer) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        durationMs = exoPlayer.duration.coerceAtLeast(0)
                        playbackError = false
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        controlsVisible = true
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    playbackError = true
                    isBuffering = false
                    controlsVisible = true
                }
            }

        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(state.info, state.selectedQualityIndex, reloadToken) {
        val info = state.info ?: return@LaunchedEffect
        val quality = info.qualities.getOrNull(state.selectedQualityIndex) ?: return@LaunchedEffect

        try {
            val resumePosition = if (hasSource) exoPlayer.currentPosition else 0L
            val resumePlaying = if (hasSource) exoPlayer.playWhenReady else true
            val source = buildMediaSource(context, quality)

            exoPlayer.setMediaSource(source)
            exoPlayer.prepare()
            if (hasSource) exoPlayer.seekTo(resumePosition)
            exoPlayer.setPlaybackSpeed(speed)
            exoPlayer.playWhenReady = resumePlaying
            if (!hasSource) exoPlayer.play()
            hasSource = true
            playbackError = false
        } catch (_: Exception) {
            playbackError = true
        }
    }

    LaunchedEffect(exoPlayer, isDragging) {
        while (isActive) {
            if (!isDragging) {
                positionMs = exoPlayer.currentPosition
                bufferedMs = exoPlayer.bufferedPosition
            }
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isDragging, settingsOpen) {
        if (controlsVisible && isPlaying && !isDragging && !settingsOpen) {
            delay(3000)
            controlsVisible = false
        }
    }

    LaunchedEffect(isFullscreen) {
        val currentActivity = activity ?: return@LaunchedEffect
        if (isFullscreen) {
            currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.getInsetsController(currentActivity.window, currentActivity.window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.getInsetsController(currentActivity.window, currentActivity.window.decorView).show(
                WindowInsetsCompat.Type.systemBars(),
            )
        }
    }

    DisposableEffect(activity) {
        val controller =
            activity?.let {
                WindowCompat.getInsetsController(it.window, it.window.decorView)
            }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false

        onDispose {
            controller?.isAppearanceLightStatusBars = !darkTheme
            controller?.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.let {
                WindowCompat.getInsetsController(it.window, it.window.decorView).show(
                    WindowInsetsCompat.Type.systemBars(),
                )
            }
        }
    }

    val info = state.info
    val toggleFullscreen = {
        isFullscreen = !isFullscreen
        controlsVisible = true
    }
    val onSeek: (Long) -> Unit = { ms ->
        exoPlayer.seekTo(ms)
        positionMs = ms
    }
    val onDraggingChanged: (Boolean) -> Unit = { dragging ->
        isDragging = dragging
        if (dragging) controlsVisible = true
    }
    val onPlayPause: () -> Unit = {
        controlsVisible = true
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0)
            exoPlayer.play()
        }
    }
    val onOpenSettings: () -> Unit = {
        controlsVisible = true
        settingsOpen = true
    }

    when {
        state.error -> {
            PlayerErrorScreen(
                onBack = handleBack,
                onRetry = viewModel::load,
                modifier = modifier,
            )
        }

        state.isLoading || info == null -> {
            PlayerSkeletonScreen(
                onBack = handleBack,
                modifier = modifier,
            )
        }

        else -> {
            if (isFullscreen) {
                PlayerStage(
                    exoPlayer = exoPlayer,
                    title = info.title.ifBlank { video.title },
                    isFullscreen = true,
                    onBack = handleBack,
                    onToggleFullscreen = toggleFullscreen,
                    onOpenSettings = onOpenSettings,
                    controlsVisible = controlsVisible,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onHideControls = { controlsVisible = false },
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    playbackError = playbackError,
                    onRetry = {
                        playbackError = false
                        reloadToken++
                    },
                    positionMs = positionMs,
                    bufferedMs = bufferedMs,
                    durationMs = durationMs,
                    isDragging = isDragging,
                    onSeek = onSeek,
                    onDraggingChanged = onDraggingChanged,
                    onPlayPause = onPlayPause,
                    modifier = modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier =
                        modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                ) {
                    PlayerStage(
                        exoPlayer = exoPlayer,
                        title = info.title.ifBlank { video.title },
                        isFullscreen = false,
                        onBack = handleBack,
                        onToggleFullscreen = toggleFullscreen,
                        onOpenSettings = onOpenSettings,
                        controlsVisible = controlsVisible,
                        onToggleControls = { controlsVisible = !controlsVisible },
                        onHideControls = { controlsVisible = false },
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        playbackError = playbackError,
                        onRetry = {
                            playbackError = false
                            reloadToken++
                        },
                        positionMs = positionMs,
                        bufferedMs = bufferedMs,
                        durationMs = durationMs,
                        isDragging = isDragging,
                        onSeek = onSeek,
                        onDraggingChanged = onDraggingChanged,
                        onPlayPause = onPlayPause,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                    )

                    PlayerDetails(
                        video = video,
                        info = info,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (settingsOpen) {
                PlayerSettingsSheet(
                    info = info,
                    selectedQualityIndex = state.selectedQualityIndex,
                    speed = speed,
                    onDismiss = { settingsOpen = false },
                    onQualitySelected = viewModel::selectQuality,
                    onSpeedSelected = { value ->
                        speed = value
                        exoPlayer.setPlaybackSpeed(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerStage(
    exoPlayer: ExoPlayer,
    title: String,
    isFullscreen: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onHideControls: () -> Unit,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playbackError: Boolean,
    onRetry: () -> Unit,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    isDragging: Boolean,
    onSeek: (Long) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var feedbackVisible by remember { mutableStateOf(false) }
    var feedbackBackward by remember { mutableStateOf(false) }
    var feedbackToken by remember { mutableIntStateOf(0) }
    var scrubPositionMs by remember { mutableLongStateOf(positionMs) }

    LaunchedEffect(feedbackToken) {
        if (feedbackToken > 0) {
            delay(650)
            feedbackVisible = false
        }
    }

    val seekBy: (Long, Boolean) -> Unit = { delta, showFeedback ->
        val target =
            (exoPlayer.currentPosition + delta)
                .coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        onSeek(target)
        if (showFeedback) {
            feedbackBackward = delta < 0
            feedbackVisible = true
            feedbackToken++
        }
    }

    Box(
        modifier =
            modifier
                .background(Color.Black),
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    player = exoPlayer
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onToggleControls() },
                            onDoubleTap = { offset ->
                                onHideControls()
                                seekBy(
                                    if (offset.x < size.width / 2f) -10_000L else 10_000L,
                                    true,
                                )
                            },
                        )
                    },
        )

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(104.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                                ),
                            ),
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.player_back),
                        onClick = onBack,
                    )

                    if (isFullscreen) {
                        Text(
                            text = title,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    GlassIconButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.player_settings),
                        onClick = onOpenSettings,
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassTextButton(
                        text = "−10",
                        onClick = { seekBy(-10_000L, false) },
                    )

                    Spacer(Modifier.width(18.dp))

                    Box(
                        modifier =
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.42f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                .clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isPlaying) {
                            Icon(
                                painter = painterResource(R.drawable.ic_pause),
                                contentDescription = stringResource(R.string.player_pause),
                                modifier = Modifier.size(34.dp),
                                tint = Color.White,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.player_play),
                                modifier = Modifier.size(42.dp),
                                tint = Color.White,
                            )
                        }
                    }

                    Spacer(Modifier.width(18.dp))

                    GlassTextButton(
                        text = "+10",
                        onClick = { seekBy(10_000L, false) },
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(26.dp))
                                .background(Color.Black.copy(alpha = 0.42f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatTime((if (isDragging) scrubPositionMs else positionMs) / 1000),
                            modifier = Modifier.width(56.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                        )

                        VideoSeekBar(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            bufferedMs = bufferedMs,
                            onSeek = onSeek,
                            onDraggingChanged = onDraggingChanged,
                            onScrubPositionChanged = { scrubPositionMs = it },
                            modifier = Modifier.weight(1f),
                        )

                        Text(
                            text = formatTime(durationMs / 1000),
                            modifier = Modifier.width(56.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                        )

                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (isFullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen,
                                    ),
                                contentDescription =
                                    stringResource(
                                        if (isFullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen,
                                    ),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .align(if (feedbackBackward) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 28.dp),
        ) {
            AnimatedVisibility(
                visible = feedbackVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (feedbackBackward) "−10" else "+10",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                }
            }
        }

        if (playbackError) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.playback_error_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.playback_error_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
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
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.38f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
        )
    }
}

@Composable
private fun GlassTextButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.38f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun PlayerDetails(
    video: VideoSummary,
    info: PlaybackInfo,
    modifier: Modifier = Modifier,
) {
    val views = if (info.viewCount >= 0) viewsText(info.viewCount) else ""
    val uploaded = info.uploadedAtEpochMillis?.let(::formatRelativeDate).orEmpty()
    val meta =
        listOfNotNull(
            views.takeIf { it.isNotBlank() },
            uploaded.takeIf { it.isNotBlank() },
        ).joinToString(" • ")

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
    ) {
        Text(
            text = info.title.ifBlank { video.title },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = info.uploader.ifBlank { stringResource(R.string.unknown_channel) },
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

@Composable
private fun PlayerErrorScreen(
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.player_back),
                tint = Color.White,
            )
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.player_error_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.retry),
                    color = Color.White,
                )
            }
        }
    }
}

private fun buildMediaSource(
    context: Context,
    quality: PlaybackQuality,
): MediaSource {
    val httpDataSourceFactory =
        DefaultHttpDataSource.Factory()
            .setUserAgent("MxTube/0.1")
            .setAllowCrossProtocolRedirects(true)
    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    val videoSource =
        ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(quality.videoUrl)
                    .setMimeType(quality.videoMimeType)
                    .build(),
            )

    val audioUrl = quality.audioUrl
    if (audioUrl.isNullOrBlank()) return videoSource

    val audioSource =
        ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(audioUrl)
                    .setMimeType(quality.audioMimeType ?: "audio/mp4")
                    .build(),
            )

    return MergingMediaSource(videoSource, audioSource)
}
