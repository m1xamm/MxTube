@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.m1xamm.mxtube.ui.player

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.m1xamm.mxtube.R
import com.m1xamm.mxtube.data.youtube.PlaybackInfo
import com.m1xamm.mxtube.data.youtube.PlaybackQuality
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.playback.PlaybackHolder
import com.m1xamm.mxtube.playback.PlaybackService
import com.m1xamm.mxtube.ui.common.findActivity
import com.m1xamm.mxtube.ui.common.formatRelativeDate
import com.m1xamm.mxtube.ui.common.formatTime
import com.m1xamm.mxtube.ui.common.viewsText
import com.m1xamm.mxtube.ui.search.VideoCluster
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class PlayButtonState {
    Buffering,
    Playing,
    Paused,
}

@Composable
fun PlayerScreen(
    video: VideoSummary,
    related: List<VideoSummary>,
    onRelatedClick: (VideoSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(key = video.url) { PlayerViewModel(video.url) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val darkTheme = isSystemInDarkTheme()
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val exoPlayer = remember(context) { PlaybackHolder.player(context) }

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

    LaunchedEffect(Unit) {
        PlaybackService.start(context)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(exoPlayer) {
        isPlaying = exoPlayer.isPlaying
        isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
        durationMs = exoPlayer.duration.coerceAtLeast(0)
        bufferedMs = exoPlayer.bufferedPosition
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
                        bufferedMs = exoPlayer.bufferedPosition
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
            val source = buildMediaSource(context, quality, info)

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
            }
            bufferedMs = exoPlayer.bufferedPosition
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

    DisposableEffect(activity, darkTheme) {
        val controller =
            activity?.let {
                WindowCompat.getInsetsController(it.window, it.window.decorView)
            }
        controller?.isAppearanceLightStatusBars = !darkTheme
        controller?.isAppearanceLightNavigationBars = !darkTheme

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
            PlayerOpenErrorScreen(
                video = video,
                related = related,
                onBack = handleBack,
                onRelatedClick = onRelatedClick,
                onRetry = viewModel::load,
                modifier = modifier,
            )
        }

        state.isLoading || info == null -> {
            PlayerLoadingScreen(
                video = video,
                related = related,
                onBack = handleBack,
                onRelatedClick = onRelatedClick,
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
                    durationMs = durationMs,
                    bufferedMs = bufferedMs,
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
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

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
                        durationMs = durationMs,
                        bufferedMs = bufferedMs,
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
                        related = related,
                        onRelatedClick = onRelatedClick,
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
    durationMs: Long,
    bufferedMs: Long,
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
    val playShape = MaterialShapes.Cookie9Sided.toShape()
    val feedbackShape = MaterialShapes.Cookie9Sided.toShape()
    val hazeState = rememberHazeState()
    val playInteractionSource = remember { MutableInteractionSource() }
    val playPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by
        animateFloatAsState(
            targetValue = if (playPressed) 0.93f else 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "play-scale",
        )
    val chromeContent = MaterialTheme.colorScheme.onSurface
    val playButtonState =
        when {
            isBuffering -> PlayButtonState.Buffering
            isPlaying -> PlayButtonState.Playing
            else -> PlayButtonState.Paused
        }

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
                (LayoutInflater.from(context).inflate(R.layout.view_player, null) as PlayerView).apply {
                    isClickable = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setOnTouchListener { _, _ -> false }
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = exoPlayer
                }
            },
            update = { it.player = exoPlayer },
            modifier =
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
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

        if (isBuffering && !controlsVisible) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(60.dp)
                        .clip(playShape)
                        .background(Color.Black.copy(alpha = 0.32f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White,
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier =
                Modifier
                    .fillMaxSize()
                    .zIndex(1f),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerIconButton(
                        iconRes = R.drawable.ic_arrow_back_rounded,
                        contentDescription = stringResource(R.string.player_back),
                        onClick = onBack,
                        hazeState = hazeState,
                    )

                    if (isFullscreen) {
                        Surface(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                                    .clip(RoundedCornerShape(50))
                                    .hazeBlur(
                                        input = HazeInput.Sources(hazeState),
                                        style = HazeMaterials.ultraThin(),
                                    ),
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent,
                            contentColor = chromeContent,
                        ) {
                            Text(
                                text = title,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = chromeContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    PlayerIconButton(
                        iconRes = R.drawable.ic_settings_rounded,
                        contentDescription = stringResource(R.string.player_settings),
                        onClick = onOpenSettings,
                        hazeState = hazeState,
                    )

                    Spacer(Modifier.width(8.dp))

                    PlayerFullscreenButton(
                        isFullscreen = isFullscreen,
                        onClick = onToggleFullscreen,
                        hazeState = hazeState,
                    )
                }

                Surface(
                    onClick = onPlayPause,
                    interactionSource = playInteractionSource,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(60.dp)
                            .clip(playShape)
                            .hazeBlur(
                                input = HazeInput.Sources(hazeState),
                                style = HazeMaterials.ultraThin(),
                            )
                            .background(Color.Black.copy(alpha = 0.32f))
                            .graphicsLayer {
                                scaleX = playScale
                                scaleY = playScale
                            },
                    shape = playShape,
                    color = Color.Transparent,
                    contentColor = Color.White,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = playButtonState,
                            transitionSpec = {
                                (fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.7f)) togetherWith
                                    (fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.7f))
                            },
                            label = "playPause",
                        ) { state ->
                            when (state) {
                                PlayButtonState.Buffering ->
                                    LoadingIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = Color.White,
                                    )

                                PlayButtonState.Playing ->
                                    Icon(
                                        painter = painterResource(R.drawable.ic_pause_rounded),
                                        contentDescription = stringResource(R.string.player_pause),
                                        modifier = Modifier.size(28.dp),
                                        tint = Color.White,
                                    )

                                PlayButtonState.Paused ->
                                    Icon(
                                        painter = painterResource(R.drawable.ic_play_arrow_rounded),
                                        contentDescription = stringResource(R.string.player_play),
                                        modifier = Modifier.size(32.dp),
                                        tint = Color.White,
                                    )
                            }
                        }
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .hazeBlur(
                                    input = HazeInput.Sources(hazeState),
                                    style = HazeMaterials.ultraThin(),
                                ),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Transparent,
                        contentColor = chromeContent,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatTime((if (isDragging) scrubPositionMs else positionMs) / 1000),
                                modifier = Modifier.width(50.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = chromeContent,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                            )

                            VideoSeekBar(
                                positionMs = positionMs,
                                bufferedMs = bufferedMs,
                                durationMs = durationMs,
                                isPlaying = isPlaying,
                                onSeek = onSeek,
                                onDraggingChanged = onDraggingChanged,
                                onScrubPositionChanged = { scrubPositionMs = it },
                                modifier = Modifier.weight(1f),
                            )

                            Text(
                                text = formatTime(durationMs / 1000),
                                modifier = Modifier.width(50.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = chromeContent,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
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
                    .padding(horizontal = 28.dp)
                    .zIndex(2f),
        ) {
            AnimatedVisibility(
                visible = feedbackVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = feedbackShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (feedbackBackward) "−10" else "+10",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        if (playbackError) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .zIndex(3f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.playback_error_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.playback_error_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

@Composable
private fun PlayerIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    hazeState: HazeState,
) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = HazeMaterials.ultraThin(),
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PlayerFullscreenButton(
    isFullscreen: Boolean,
    onClick: () -> Unit,
    hazeState: HazeState,
) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = HazeMaterials.ultraThin(),
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isFullscreen,
            transitionSpec = {
                (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.75f)) togetherWith
                    (fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.75f))
            },
            label = "fullscreenIcon",
        ) { fullscreen ->
            Icon(
                painter =
                    painterResource(
                        if (fullscreen) {
                            R.drawable.ic_fullscreen_exit_rounded
                        } else {
                            R.drawable.ic_fullscreen_rounded
                        },
                    ),
                contentDescription =
                    stringResource(
                        if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen,
                    ),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PlayerDetails(
    video: VideoSummary,
    info: PlaybackInfo,
    related: List<VideoSummary>,
    onRelatedClick: (VideoSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val views = if (info.viewCount >= 0) viewsText(info.viewCount) else ""
    val uploaded = info.uploadedAtEpochMillis?.let(::formatRelativeDate).orEmpty()
    val meta =
        listOfNotNull(
            views.takeIf { it.isNotBlank() },
            uploaded.takeIf { it.isNotBlank() },
        ).joinToString(" • ")

    LazyColumn(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "player_details") {
            Column {
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

        if (related.isNotEmpty()) {
            item(key = "related_header") {
                Text(
                    text = stringResource(R.string.more_from_search),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            item(key = "related_cluster") {
                VideoCluster(
                    videos = related,
                    onVideoClick = onRelatedClick,
                )
            }
        }
    }
}

private fun buildMediaSource(
    context: Context,
    quality: PlaybackQuality,
    info: PlaybackInfo,
): MediaSource {
    val httpDataSourceFactory =
        DefaultHttpDataSource.Factory()
            .setUserAgent("MxTube/0.1")
            .setAllowCrossProtocolRedirects(true)
    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    val metadata =
        MediaMetadata.Builder()
            .setTitle(info.title.ifBlank { "MxTube" })
            .setArtist(info.uploader)
            .setArtworkUri(
                info.thumbnailUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let(Uri::parse),
            )
            .build()

    val videoSource =
        ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(quality.videoUrl)
                    .setMimeType(quality.videoMimeType)
                    .setMediaMetadata(metadata)
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
