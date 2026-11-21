@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mixam.mxtube.ui.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.mixam.mxtube.MxTubeApplication
import com.mixam.mxtube.R
import com.mixam.mxtube.data.local.BlockedChannelsStore
import com.mixam.mxtube.data.local.SubscriptionNotificationMode
import com.mixam.mxtube.data.local.SubscriptionsStore
import com.mixam.mxtube.data.local.UserPreferences
import com.mixam.mxtube.data.network.Http
import com.mixam.mxtube.data.youtube.PlaybackAudioTrack
import com.mixam.mxtube.data.youtube.PlaybackInfo
import com.mixam.mxtube.data.youtube.PlaybackQuality
import com.mixam.mxtube.data.youtube.PlaybackSubtitle
import com.mixam.mxtube.data.youtube.VideoSummary
import com.mixam.mxtube.notifications.SubscriptionNotificationScheduler
import com.mixam.mxtube.playback.PlaybackHolder
import com.mixam.mxtube.playback.PlaybackService
import com.mixam.mxtube.ui.common.clusterCardColor
import com.mixam.mxtube.ui.common.clusterInnerColor
import com.mixam.mxtube.ui.common.findActivity
import com.mixam.mxtube.ui.common.compactCount
import com.mixam.mxtube.ui.common.formatDuration
import com.mixam.mxtube.ui.common.formatRelativeDate
import com.mixam.mxtube.ui.common.formatSpeed
import com.mixam.mxtube.ui.common.formatTime
import com.mixam.mxtube.ui.common.BridgeFrame
import com.mixam.mxtube.ui.common.PlayPauseIcon
import com.mixam.mxtube.ui.common.viewsText
import com.mixam.mxtube.ui.search.VideoCluster
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    onChannelClick: (String) -> Unit,
    onWatchProgress: (Long) -> Unit = {},
    startFullscreen: Boolean = false,
    onFullscreenChanged: (Boolean) -> Unit = {},
    playlistQueue: PlaylistQueue? = null,
    onQueueSelect: (Int) -> Unit = {},
    onQueueShuffle: (Boolean) -> Unit = {},
    onQueueLoop: (Boolean) -> Unit = {},
    onEnded: () -> Unit = {},
    morphTo: Rect? = null,
    bridgeFrame: Bitmap? = null,
    onMorphLanded: (Bitmap?) -> Unit = {},
    modifier: Modifier = Modifier,
    subscriptions: SubscriptionsStore =
        (LocalContext.current.applicationContext as MxTubeApplication).subscriptionsStore,
    blockedChannels: BlockedChannelsStore =
        (LocalContext.current.applicationContext as MxTubeApplication).blockedChannelsStore,
    viewModel: PlayerViewModel =
        viewModel(key = video.url) {
            PlayerViewModel(
                video.url,
                initialRelated = related,
                subscriptions = subscriptions,
                blockedChannels = blockedChannels,
                initialUploaderUrl = video.uploaderUrl,
                initialUploaderName = video.uploader,
            )
        },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val info = state.info
    val hazeState = rememberHazeState()
    val settingsHazeState = rememberHazeState()
    val selectedSubtitle = info?.subtitles?.getOrNull(state.selectedSubtitleIndex)
    val currentSubtitle = rememberUpdatedState(selectedSubtitle)
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val darkTheme = isSystemInDarkTheme()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (granted) SubscriptionNotificationScheduler.requestCheck(context)
        }
    val requestNotifications: () -> Unit = {
        if (!notificationsEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (enabled && !notificationsEnabled) SubscriptionNotificationScheduler.requestCheck(context)
        notificationsEnabled = enabled
    }
    val exoPlayer = remember(context) { PlaybackHolder.player(context) }

    var subscriptionMenuOpen by rememberSaveable { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(startFullscreen) }
    var relatedOverlayOpen by rememberSaveable { mutableStateOf(false) }
    var hasEnteredFullscreen by remember { mutableStateOf(isFullscreen) }
    var ended by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var speed by rememberSaveable { mutableFloatStateOf(UserPreferences.playbackSpeed) }
    val boostPlayback: (Boolean) -> Unit = { active ->
        exoPlayer.setPlaybackSpeed(if (active) maxOf(2f, speed) else speed)
    }
    var settingsOpen by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var coverVisible by remember { mutableStateOf(true) }
    var playlistExpanded by remember { mutableStateOf(false) }
    var detailsVisible by remember { mutableStateOf(morphTo == null) }
    val detailsAlpha by
        animateFloatAsState(
            targetValue = if (detailsVisible) 1f else 0f,
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            label = "player-details-alpha",
        )
    var revealTarget by remember { mutableFloatStateOf(1f) }
    val backgroundColor = MaterialTheme.colorScheme.background
    val revealAlpha by
        animateFloatAsState(
            targetValue = revealTarget,
            animationSpec =
                spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
            label = "player-reveal-alpha",
        )
    LaunchedEffect(video.url) {
        coverVisible = true
    }
    val closeMorphLanded = rememberUpdatedState(onMorphLanded)
    val endedHandler = rememberUpdatedState(onEnded)
    val videoView = remember { mutableStateOf<View?>(null) }
    LaunchedEffect(morphTo) {
        if (morphTo != null) {
            val texture = videoView.value?.let { view ->
                (view as? PlayerView)?.videoSurfaceView as? TextureView
            }
            val bitmap =
                texture?.takeIf { it.isAvailable }?.let { view ->
                    runCatching { view.bitmap }.getOrNull()
                }
            closeMorphLanded.value(bitmap)
        }
    }

    val windowWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val statusTopPx =
        with(LocalDensity.current) {
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
        }
    val chromeVisible = controlsVisible && morphTo == null

    val handleBack: () -> Unit = {
        if (subscriptionMenuOpen) {
            subscriptionMenuOpen = false
        } else if (settingsOpen) {
            settingsOpen = false
        } else if (relatedOverlayOpen) {
            relatedOverlayOpen = false
            controlsVisible = true
        } else if (isFullscreen) {
            isFullscreen = false
        } else {
            val position = exoPlayer.currentPosition
            if (position > 0) onWatchProgress(position)
            onBack()
        }
    }

    BackHandler { handleBack() }

    LaunchedEffect(isFullscreen) {
        onFullscreenChanged(isFullscreen)
    }

    val progressReporter = rememberUpdatedState(onWatchProgress)
    DisposableEffect(exoPlayer) {
        onDispose {
            val position = exoPlayer.currentPosition
            if (position > 0) progressReporter.value(position)
        }
    }

    LaunchedEffect(Unit) {
        PlaybackService.start(context)
    }

    LaunchedEffect(Unit) {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
    }

    LaunchedEffect(exoPlayer) {
        isPlaying = exoPlayer.isPlaying
        isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
        durationMs = exoPlayer.duration.coerceAtLeast(0)
        bufferedMs = exoPlayer.bufferedPosition
    }

    LaunchedEffect(video.url) {
        if (PlaybackHolder.preparedKey?.startsWith("${video.url}#") != true) {
            exoPlayer.pause()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener =            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (playing) ended = false
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
                        ended = true
                        endedHandler.value()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    playbackError = true
                    isBuffering = false
                    controlsVisible = true
                }

                override fun onTracksChanged(tracks: Tracks) {
                    applySubtitleSelection(exoPlayer, currentSubtitle.value)
                }

                override fun onRenderedFirstFrame() {
                    coverVisible = false
                }
            }

        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(state.info, state.selectedQualityIndex, state.selectedAudioTrackIndex, reloadToken) {
        val info = state.info ?: return@LaunchedEffect
        val quality = info.qualities.getOrNull(state.selectedQualityIndex) ?: return@LaunchedEffect
        val audioTrack = info.audioTracks.getOrNull(state.selectedAudioTrackIndex)
        val sourceKey = "${video.url}#${quality.videoUrl}#${audioTrack?.url.orEmpty()}"
        try {
            val sameVideo = PlaybackHolder.preparedKey?.startsWith("${video.url}#") == true
            val prepared =
                PlaybackHolder.preparedKey == sourceKey &&
                    exoPlayer.mediaItemCount > 0 &&
                    exoPlayer.playbackState != Player.STATE_IDLE
            if (!prepared) {
                val resumePosition = if (sameVideo) exoPlayer.currentPosition else 0L
                val resumePlaying = if (sameVideo) exoPlayer.playWhenReady else true
                val source = buildMediaSource(context, quality, audioTrack, info)
                exoPlayer.setMediaSource(source)
                exoPlayer.prepare()
                if (resumePosition > 0) exoPlayer.seekTo(resumePosition)
                exoPlayer.playWhenReady = resumePlaying
            }
            exoPlayer.setPlaybackSpeed(speed)
            PlaybackHolder.preparedKey = sourceKey
            playbackError = false
        } catch (_: Exception) {
            playbackError = true
        }
    }

    LaunchedEffect(exoPlayer, selectedSubtitle) {
        applySubtitleSelection(exoPlayer, selectedSubtitle)
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

    LaunchedEffect(controlsVisible, isPlaying, isDragging, settingsOpen, relatedOverlayOpen) {
        if (controlsVisible && isPlaying && !isDragging && !settingsOpen) {
            delay(3000)
            controlsVisible = false
        }
    }

    LaunchedEffect(ended, isFullscreen) {
        if (ended && isFullscreen) {
            relatedOverlayOpen = true
            controlsVisible = true
        }
    }

    LaunchedEffect(Unit) {
        if (!isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    LaunchedEffect(isFullscreen) {
        val currentActivity = activity ?: return@LaunchedEffect
        if (isFullscreen) {
            hasEnteredFullscreen = true
            currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.getInsetsController(currentActivity.window, currentActivity.window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            if (hasEnteredFullscreen) {
                currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
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

    val toggleFullscreen = {
        isFullscreen = !isFullscreen
        relatedOverlayOpen = false
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
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0)
                ended = false
                relatedOverlayOpen = false
            }
            exoPlayer.play()
        }
    }
    val onOpenSettings: () -> Unit = {
        controlsVisible = true
        settingsOpen = true
    }
    val onSubscribeAction: () -> Unit = {
        viewModel.subscribe()
        requestNotifications()
    }
    val onManageAction: () -> Unit = {
        controlsVisible = false
        subscriptionMenuOpen = true
    }

    when {
        state.error -> {
            PlayerOpenErrorScreen(
                video = video,
                related = state.related,
                onBack = handleBack,
                onRelatedClick = onRelatedClick,
                onRetry = viewModel::load,
                isSubscribed = state.isSubscribed,
                pending = state.isSubscribePending,
                notificationMode = state.notificationMode,
                onChannelClick = onChannelClick,
                onSubscribe = onSubscribeAction,
                onManageSubscription = onManageAction,
                modifier = modifier,
            )
        }

        state.isLoading || info == null -> {
            PlayerLoadingScreen(
                video = video,
                related = state.related,
                onBack = handleBack,
                onRelatedClick = onRelatedClick,
                isSubscribed = state.isSubscribed,
                pending = state.isSubscribePending,
                notificationMode = state.notificationMode,
                onChannelClick = onChannelClick,
                onSubscribe = onSubscribeAction,
                onManageSubscription = onManageAction,
                modifier = modifier,
            )
        }

        else -> {
            Box(modifier = modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().hazeSource(settingsHazeState)) {
                    if (isFullscreen) {
                        Box(Modifier.fillMaxSize()) {
                            PlayerStage(
                                exoPlayer = exoPlayer,
                                hazeState = hazeState,
                                title = info.title.ifBlank { video.title },
                                videoUrl = video.url,
                                coverUrl = video.thumbnailUrl ?: info.thumbnailUrl,
                                coverVisible = coverVisible,
                                isFullscreen = true,
                                onBack = handleBack,
                                onToggleFullscreen = toggleFullscreen,
                                onOpenSettings = onOpenSettings,
                                relatedVisible = relatedOverlayOpen,
                                onRelatedVisibilityChanged = { visible ->
                                    relatedOverlayOpen = visible
                                    controlsVisible = true
                                },
                                relatedContent = {
                                    FullscreenRelatedVideos(
                                        related = state.related,
                                        isLoading = state.isLoadingRelated,
                                        hasMore = state.hasMoreRelated,
                                        error = state.relatedError,
                                        onLoadMore = viewModel::loadMoreRelated,
                                        onRetry = viewModel::retryRelated,
                                        onVideoClick = { next ->
                                            relatedOverlayOpen = false
                                            onRelatedClick(next)
                                        },
                                        hazeState = hazeState,
                                    )
                                },
                                controlsVisible = chromeVisible && !settingsOpen,
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
                                speed = speed,
                                bridgeFrame = bridgeFrame,
                                onVideoView = { videoView.value = it },
                                onBoostChanged = boostPlayback,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .drawBehind {
                                        drawRect(
                                            color = backgroundColor,
                                            alpha = revealAlpha.coerceIn(0f, 1f),
                                        )
                                    },
                        ) {
                            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

                            PlayerStage(
                                exoPlayer = exoPlayer,
                                hazeState = hazeState,
                                title = info.title.ifBlank { video.title },
                                videoUrl = video.url,
                                coverUrl = video.thumbnailUrl ?: info.thumbnailUrl,
                                coverVisible = coverVisible,
                                isFullscreen = false,
                                onBack = handleBack,
                                onToggleFullscreen = toggleFullscreen,
                                onOpenSettings = onOpenSettings,
                                controlsVisible = chromeVisible && !settingsOpen,
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
                                speed = speed,
                                ended = ended,
                                endSuggestions =
                                    state.related.filter { it.url != video.url }.take(4),
                                onEndSuggestionClick = { next -> onRelatedClick(next) },
                                onReplay = {
                                    onSeek(0L)
                                    exoPlayer.play()
                                },
                                bridgeFrame = bridgeFrame,
                                onVideoView = { videoView.value = it },
                                onBoostChanged = boostPlayback,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(20.dp)),
                            )

                            Box(modifier = Modifier.weight(1f).graphicsLayer { alpha = detailsAlpha }) {
                                PlayerDetails(
                                    video = video,
                                    info = info,
                                    topPadding = if (playlistQueue != null) 92.dp else 16.dp,
                                    related = state.related,
                                    onRelatedClick = onRelatedClick,
                                    isSubscribed = state.isSubscribed,
                                    isSubscribePending = state.isSubscribePending,
                                    notificationMode = state.notificationMode,
                                    subscriptionError = state.subscriptionError,
                                    onChannelClick = onChannelClick,
                                    onSubscribe = {
                                        viewModel.subscribe()
                                        requestNotifications()
                                    },
                                    onManageSubscription = {
                                        controlsVisible = false
                                        subscriptionMenuOpen = true
                                    },
                                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                                )

                                if (playlistQueue != null) {
                                    PlaylistBar(
                                        queue = playlistQueue,
                                        expanded = playlistExpanded,
                                        onToggleExpanded = { playlistExpanded = !playlistExpanded },
                                        onSelect = onQueueSelect,
                                        onShuffle = { onQueueShuffle(!playlistQueue.shuffled) },
                                        onLoop = { onQueueLoop(!playlistQueue.loop) },
                                        hazeState = hazeState,
                                        modifier = Modifier.align(Alignment.TopCenter),
                                    )
                                }
                            }
                        }
                    }
                }

                if (subscriptionMenuOpen) {
                    SubscriptionMenuSheet(
                        mode = state.notificationMode,
                        hazeState = settingsHazeState,
                        notificationsEnabled = notificationsEnabled,
                        onEnableNotifications = requestNotifications,
                        onModeSelected = { mode ->
                            viewModel.setNotificationMode(mode)
                            if (mode != SubscriptionNotificationMode.None) requestNotifications()
                        },
                        onUnsubscribe = viewModel::unsubscribe,
                        onDismiss = { subscriptionMenuOpen = false },
                    )
                }

                if (settingsOpen) {
                    PlayerSettingsOverlay(
                        info = info,
                        selectedQualityIndex = state.selectedQualityIndex,
                        selectedAudioTrackIndex = state.selectedAudioTrackIndex,
                        selectedSubtitleIndex = state.selectedSubtitleIndex,
                        speed = speed,
                        hazeState = settingsHazeState,
                        onDismiss = { settingsOpen = false },
                        onQualitySelected = viewModel::selectQuality,
                        onAudioTrackSelected = viewModel::selectAudioTrack,
                        onSubtitleSelected = viewModel::selectSubtitle,
                        onSpeedSelected = { value ->
                            speed = value
                            UserPreferences.playbackSpeed = value
                            exoPlayer.setPlaybackSpeed(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerStage(
    exoPlayer: ExoPlayer,
    hazeState: HazeState,
    title: String,
    videoUrl: String,
    coverUrl: String?,
    coverVisible: Boolean,
    isFullscreen: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    relatedVisible: Boolean = false,
    onRelatedVisibilityChanged: ((Boolean) -> Unit)? = null,
    relatedContent: (@Composable () -> Unit)? = null,
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
    speed: Float = 1f,
    ended: Boolean = false,
    endSuggestions: List<VideoSummary> = emptyList(),
    onEndSuggestionClick: (VideoSummary) -> Unit = {},
    onReplay: () -> Unit = {},
    bridgeFrame: Bitmap? = null,
    onVideoView: (View) -> Unit = {},
    onBoostChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var feedbackVisible by remember { mutableStateOf(false) }
    var feedbackBackward by remember { mutableStateOf(false) }
    var feedbackToken by remember { mutableIntStateOf(0) }
    var scrubPositionMs by remember { mutableLongStateOf(positionMs) }
    val playShape = MaterialShapes.Cookie9Sided.toShape()
    val feedbackShape = MaterialShapes.Cookie9Sided.toShape()
    var boost by remember { mutableStateOf(false) }
    val boostHandler = rememberUpdatedState(onBoostChanged)
    val playingState = rememberUpdatedState(isPlaying)
    val gestureScope = rememberCoroutineScope()
    var boostJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            boostJob?.cancel()
            boostHandler.value(false)
        }
    }
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
    val subtitleShift = with(LocalDensity.current) { if (controlsVisible) 40.dp.toPx() else 0f }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(isFullscreen) {
        zoomScale = 1f
        zoomOffset = Offset.Zero
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

    val relatedVisibilityChanged = rememberUpdatedState(onRelatedVisibilityChanged)
    BoxWithConstraints(
        modifier =
            modifier
                .background(Color.Black)
                .clipToBounds()
                .pointerInput(Unit) {
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    detectTapGestures(
                        onPress = { pressOffset ->
                            boostJob?.cancel()
                            val sidePress =
                                pressOffset.x < size.width / 3f ||
                                    pressOffset.x > size.width * 2f / 3f
                            if (sidePress) {
                                boostJob =
                                    gestureScope.launch {
                                        delay(longPressTimeout + 16)
                                        if (playingState.value) {
                                            boost = true
                                            boostHandler.value(true)
                                        }
                                    }
                            }
                            tryAwaitRelease()
                            boostJob?.cancel()
                            if (boost) {
                                boost = false
                                boostHandler.value(false)
                            }
                        },
                        onLongPress = {},
                        onTap = { onToggleControls() },
                        onDoubleTap = { offset ->
                            onHideControls()
                            seekBy(
                                if (offset.x < size.width / 2f) -10_000L else 10_000L,
                                true,
                            )
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var scale = zoomScale
                        var offset = zoomOffset
                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.count { it.pressed }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (pointers >= 2) {
                                scale = (scale * zoomChange).coerceIn(1f, 4f)
                                val maxX = (scale - 1f) * size.width / 2f
                                val maxY = (scale - 1f) * size.height / 2f
                                offset =
                                    Offset(
                                        (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                        (offset.y + panChange.y).coerceIn(-maxY, maxY),
                                    )
                                event.changes.forEach { it.consume() }
                            } else if (scale > 1f) {
                                val maxX = (scale - 1f) * size.width / 2f
                                val maxY = (scale - 1f) * size.height / 2f
                                offset =
                                    Offset(
                                        (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                        (offset.y + panChange.y).coerceIn(-maxY, maxY),
                                    )
                                event.changes.forEach { it.consume() }
                            }
                            zoomScale = scale
                            zoomOffset = offset
                        } while (event.changes.any { it.pressed })
                        if (scale <= 1.01f) {
                            zoomScale = 1f
                            zoomOffset = Offset.Zero
                        }
                    }
                }
                .then(
                    if (onRelatedVisibilityChanged != null) {
                        Modifier.pointerInput(Unit) {
                            var dragDistance = 0f
                            detectVerticalDragGestures(
                                onDragStart = { dragDistance = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    dragDistance += dragAmount
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (dragDistance < -48.dp.toPx()) {
                                        relatedVisibilityChanged.value?.invoke(true)
                                    } else if (dragDistance > 48.dp.toPx()) {
                                        relatedVisibilityChanged.value?.invoke(false)
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
    ) {
        val relatedHeight = minOf(208.dp, maxHeight * 0.55f)
        val controlsBottomInset by animateDpAsState(
            targetValue = if (relatedVisible) relatedHeight else 0.dp,
            animationSpec = tween(300),
            label = "related-controls-inset",
        )
        AndroidView(
            factory = { context ->
                (LayoutInflater.from(context).inflate(R.layout.view_player, null) as PlayerView).apply {
                    isClickable = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setOnTouchListener { _, _ -> false }
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    getSubtitleView()?.apply {
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.argb(140, 0, 0, 0),
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_NONE,
                                android.graphics.Color.WHITE,
                                null,
                            ),
                        )
                        setFractionalTextSize(0.08f)
                        setBottomPaddingFraction(0.12f)
                    }
                    player = exoPlayer
                    onVideoView(this)
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.getSubtitleView()?.apply {
                    translationY = -subtitleShift
                    setFractionalTextSize(if (isFullscreen) 0.045f else 0.08f)
                }
                val radius = if (isFullscreen) 0f else 20f * view.resources.displayMetrics.density
                view.outlineProvider =
                    object : android.view.ViewOutlineProvider() {
                        override fun getOutline(
                            target: android.view.View,
                            outline: android.graphics.Outline,
                        ) {
                            outline.setRoundRect(0, 0, target.width, target.height, radius)
                        }
                    }
                view.clipToOutline = true
            },
            onRelease = { view -> view.player = null },
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = zoomOffset.x
                        translationY = zoomOffset.y
                    }
                    .hazeSource(hazeState),
        )

        BridgeFrame(bridgeFrame = bridgeFrame)

        AnimatedVisibility(
            visible = ended && !isFullscreen,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.fillMaxSize(),
        ) {
            EndScreen(
                suggestions = endSuggestions,
                onReplay = onReplay,
                onSuggestionClick = onEndSuggestionClick,
            )
        }

        AnimatedVisibility(
            visible = coverVisible,
            enter = androidx.compose.animation.EnterTransition.None,
            exit = fadeOut(tween(240)),
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isBuffering && !controlsVisible) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(playShape)
                        .background(Color.Black.copy(alpha = 0.32f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(26.dp),
                    color = Color.White,
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !(ended && !isFullscreen),
            modifier =
                Modifier
                    .fillMaxSize()
                    .zIndex(1f),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxSize().padding(bottom = controlsBottomInset)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .then(
                                if (isFullscreen) {
                                    Modifier.windowInsetsPadding(WindowInsets.displayCutout)
                                } else {
                                    Modifier
                                },
                            )
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

                    if (kotlin.math.abs(speed - 1f) > 0.001f) {
                        PlayerSpeedChip(text = formatSpeed(speed), hazeState = hazeState)
                        Spacer(Modifier.width(6.dp))
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

                if (!ended) {
                    Surface(
                        onClick = onPlayPause,
                        interactionSource = playInteractionSource,
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .size(56.dp)
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
                            androidx.compose.animation.Crossfade(
                                targetState = isBuffering,
                                animationSpec = tween(140, easing = FastOutSlowInEasing),
                                label = "play-buffering",
                            ) { buffering ->
                                if (buffering) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = Color.White,
                                    )
                                } else {
                                    PlayPauseIcon(
                                        playing = isPlaying,
                                        modifier = Modifier.size(30.dp),
                                        tint = Color.White,
                                        contentDescription =
                                            stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .then(
                                if (isFullscreen) {
                                    Modifier
                                        .windowInsetsPadding(WindowInsets.displayCutout)
                                        .navigationBarsPadding()
                                } else {
                                    Modifier
                                },
                            )
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

        AnimatedVisibility(
            visible = boost,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .zIndex(2f),
            enter =
                fadeIn(tween(120)) +
                    scaleIn(
                        initialScale = 0.7f,
                        animationSpec =
                            spring(
                                dampingRatio = 0.56f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                    ),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f, animationSpec = tween(150)),
        ) {
            Surface(
                modifier =
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .hazeBlur(
                            input = HazeInput.Sources(hazeState),
                            style = HazeMaterials.ultraThin(),
                        ),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                contentColor = chromeContent,
            ) {
                Row(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatSpeed(maxOf(2f, speed)),
                        style = MaterialTheme.typography.labelLarge,
                        color = chromeContent,
                    )
                    Spacer(Modifier.width(8.dp))
                    BoostChevrons(tint = chromeContent)
                }
            }
        }

        if (relatedContent != null) {
            AnimatedVisibility(
                visible = relatedVisible,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(relatedHeight)
                        .zIndex(1f),
                enter = slideInVertically(tween(300), initialOffsetY = { it }) + fadeIn(tween(180)),
                exit = slideOutVertically(tween(300), targetOffsetY = { it }) + fadeOut(tween(180)),
            ) {
                relatedContent()
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
private fun EndScreen(
    suggestions: List<VideoSummary>,
    onReplay: () -> Unit,
    onSuggestionClick: (VideoSummary) -> Unit,
) {
    val items = suggestions.take(3)
    var autoplay by remember { mutableStateOf(items.isNotEmpty()) }
    var remaining by remember { mutableIntStateOf(AUTOPLAY_SECONDS) }
    val currentSuggestionClick = rememberUpdatedState(onSuggestionClick)

    LaunchedEffect(items.firstOrNull()?.url, autoplay) {
        val next = items.firstOrNull() ?: return@LaunchedEffect
        if (!autoplay) return@LaunchedEffect
        remaining = AUTOPLAY_SECONDS
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
        currentSuggestionClick.value(next)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onReplay,
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh_rounded),
                            contentDescription = stringResource(R.string.player_replay),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (autoplay && items.isNotEmpty()) {
                    Surface(
                        onClick = { autoplay = false },
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White,
                    ) {
                        Text(
                            text = stringResource(R.string.player_next_in, remaining),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFeatureSettings = "tnum",
                            ),
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.take(3).forEach { video ->
                        EndSuggestion(
                            video = video,
                            onClick = { onSuggestionClick(video) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EndSuggestion(
    video: VideoSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(2.dp),
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BoostChevrons(tint: Color) {
    val transition = rememberInfiniteTransition(label = "boost-chevrons")
    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "boost-chevrons-phase",
        )
    Canvas(Modifier.size(width = 26.dp, height = 14.dp)) {
        val unit = size.height / 14f
        val stroke = 2.1f * unit
        for (index in 0..2) {
            val local = (phase + index * 0.22f) % 1f
            val alpha =
                (when {
                    local < 0.18f -> local / 0.18f
                    local > 0.82f -> (1f - local) / 0.18f
                    else -> 1f
                }).coerceIn(0f, 1f)
            val x = 1.6f * unit + index * 6.6f * unit + local * 4.2f * unit
            val color = tint.copy(alpha = alpha)
            drawLine(
                color = color,
                start = Offset(x, 2.4f * unit),
                end = Offset(x + 4.8f * unit, 7f * unit),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(x + 4.8f * unit, 7f * unit),
                end = Offset(x, 11.6f * unit),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun PlayerSpeedChip(
    text: String,
    hazeState: HazeState,
) {
    Box(
        modifier =
            Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = HazeMaterials.ultraThin(),
                )
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
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
private fun FullscreenRelatedVideos(
    related: List<VideoSummary>,
    isLoading: Boolean,
    hasMore: Boolean,
    error: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onVideoClick: (VideoSummary) -> Unit,
    hazeState: HazeState,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 2
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, isLoading, error, related.size) {
        if (shouldLoadMore && hasMore && !isLoading && !error) onLoadMore()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(
                items = related,
                key = { _, video -> video.url },
            ) { index, video ->
                FullscreenRelatedCard(
                    video = video,
                    onClick = { onVideoClick(video) },
                    modifier = Modifier.width(224.dp).fillMaxHeight(),
                    startCorner = if (index == 0) 28.dp else 4.dp,
                    endCorner = if (index == related.lastIndex && !hasMore && !isLoading) 28.dp else 4.dp,
                    hazeState = hazeState,
                )
            }

            when {
                isLoading -> item(key = "related_loading") {
                    Box(
                        modifier = Modifier.width(64.dp).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    }
                }
                error -> item(key = "related_error") {
                    Surface(
                        modifier = Modifier.width(240.dp).fillMaxHeight(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.player_related_error),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                related.isEmpty() -> item(key = "related_empty") {
                    Surface(
                        modifier = Modifier.width(240.dp).fillMaxHeight(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.player_related_empty),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenRelatedCard(
    video: VideoSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    startCorner: Dp = 28.dp,
    endCorner: Dp = 28.dp,
    hazeState: HazeState,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val animatedStartCorner by animateDpAsState(
        targetValue = if (pressed) 28.dp else startCorner,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "related-start-corner",
    )
    val animatedEndCorner by animateDpAsState(
        targetValue = if (pressed) 28.dp else endCorner,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "related-end-corner",
    )
    val duration = formatDuration(video.durationSeconds)
    val views = if (video.viewCount >= 0) compactCount(video.viewCount) else ""
    val uploaded =
        video.uploadedAtEpochMillis?.let { epoch -> formatRelativeDate(epoch) }.orEmpty()
    val meta =
        listOfNotNull(
            video.uploader.ifBlank { stringResource(R.string.unknown_channel) },
            views.takeIf { it.isNotBlank() },
            uploaded.takeIf { it.isNotBlank() },
        ).joinToString(" • ")

    val shape =
        RoundedCornerShape(
            topStart = animatedStartCorner,
            bottomStart = animatedStartCorner,
            topEnd = animatedEndCorner,
            bottomEnd = animatedEndCorner,
        )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.clip(shape),
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .hazeBlur(
                        input = HazeInput.Sources(hazeState),
                        style = HazeMaterials.ultraThin(),
                    ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(clusterInnerColor()),
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    if (duration.isNotBlank()) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                            shape = RoundedCornerShape(7.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            contentColor = Color.White,
                        ) {
                            Text(
                                text = duration,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlayerDetails(
    video: VideoSummary,
    info: PlaybackInfo,
    topPadding: Dp = 16.dp,
    related: List<VideoSummary>,
    onRelatedClick: (VideoSummary) -> Unit,
    isSubscribed: Boolean,
    isSubscribePending: Boolean,
    notificationMode: SubscriptionNotificationMode,
    subscriptionError: Boolean,
    onChannelClick: (String) -> Unit,
    onSubscribe: () -> Unit,
    onManageSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val channelName = info.uploader.ifBlank { stringResource(R.string.unknown_channel) }
    val views = if (info.viewCount >= 0) viewsText(info.viewCount) else ""
    val uploaded = info.uploadedAtEpochMillis?.let { formatRelativeDate(it) }.orEmpty()
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = 24.dp),
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

                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))

                ChannelSubscribeCard(
                    channelName = channelName,
                    avatarUrl = info.uploaderAvatarUrl,
                    uploaderUrl = info.uploaderUrl,
                    isSubscribed = isSubscribed,
                    pending = isSubscribePending,
                    notificationMode = notificationMode,
                    onChannelClick = onChannelClick,
                    onSubscribe = onSubscribe,
                    onManageSubscription = onManageSubscription,
                )
                if (subscriptionError) {
                    Text(
                        text = stringResource(R.string.subscription_error),
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (related.isNotEmpty()) {
            item(key = "related_header") {
                Text(
                    text = stringResource(R.string.player_related),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            item(key = "related_cluster") {
                VideoCluster(
                    videos = related.take(MAX_PORTRAIT_RELATED),
                    onVideoClick = onRelatedClick,
                )
            }
        }
    }

}

private const val MAX_PORTRAIT_RELATED = 30
private const val AUTOPLAY_SECONDS = 8

@Composable
internal fun ChannelSubscribeCard(
    channelName: String,
    avatarUrl: String?,
    uploaderUrl: String?,
    isSubscribed: Boolean,
    pending: Boolean,
    notificationMode: SubscriptionNotificationMode,
    onChannelClick: (String) -> Unit,
    onSubscribe: () -> Unit,
    onManageSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = clusterCardColor(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                    .then(uploaderUrl?.let { url -> Modifier.clickable { onChannelClick(url) } } ?: Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChannelAvatar(url = avatarUrl, name = channelName)
                Text(
                    text = channelName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SubscribeButton(
                subscribed = isSubscribed,
                pending = pending,
                notificationMode = notificationMode,
                channelName = channelName,
                onClick = if (isSubscribed) onManageSubscription else onSubscribe,
            )
        }
    }
}

@Composable
internal fun ChannelAvatar(
    url: String?,
    name: String,
    size: Dp = 40.dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text(
                text = name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
internal fun SubscribeButton(
    subscribed: Boolean,
    pending: Boolean,
    notificationMode: SubscriptionNotificationMode,
    channelName: String,
    onClick: () -> Unit,
) {
    SubscribePill(
        subscribed = subscribed,
        pending = pending,
        notificationMode = notificationMode,
        onClick = onClick,
        compact = true,
        channelName = channelName,
    )
}

private fun buildMediaSource(
    context: Context,
    quality: PlaybackQuality,
    audioTrack: PlaybackAudioTrack?,
    info: PlaybackInfo,
): MediaSource {
    val httpDataSourceFactory =
        OkHttpDataSource.Factory(Http.client)
            .setUserAgent("MxTube/0.1")
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

    val subtitleConfigurations =
        info.subtitles.map { subtitle ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                .setMimeType(subtitle.mimeType)
                .setLanguage(subtitle.languageTag)
                .setLabel(subtitle.label)
                .setId(subtitle.id)
                .build()
        }

    val videoItem =
        MediaItem.Builder()
            .setUri(quality.videoUrl)
            .setMimeType(quality.videoMimeType)
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(subtitleConfigurations)
            .build()
    val videoSource = DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(videoItem)

    val audioUrl = audioTrack?.url
    if (audioUrl.isNullOrBlank()) return videoSource

    val audioSource =
        ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(audioUrl)
                    .setMimeType(audioTrack.mimeType.ifBlank { "audio/mp4" })
                    .build(),
            )

    return MergingMediaSource(videoSource, audioSource)
}

private fun applySubtitleSelection(
    player: ExoPlayer,
    subtitle: PlaybackSubtitle?,
) {
    val builder =
        player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)

    if (subtitle == null) {
        player.trackSelectionParameters =
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        return
    }

    val group =
        player.currentTracks.groups.firstOrNull { trackGroup ->
            trackGroup.type == C.TRACK_TYPE_TEXT &&
                (0 until trackGroup.length).any {
                    val format = trackGroup.getTrackFormat(it)
                    format.id?.endsWith(subtitle.id) == true ||
                        (format.language == subtitle.languageTag && format.label == subtitle.label)
                }
        }
    val trackIndex =
        group?.let { trackGroup ->
            (0 until trackGroup.length).firstOrNull {
                val format = trackGroup.getTrackFormat(it)
                format.id?.endsWith(subtitle.id) == true ||
                    (format.language == subtitle.languageTag && format.label == subtitle.label)
            }
        }

    player.trackSelectionParameters =
        if (group != null && trackIndex != null) {
            builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                .build()
        } else {
            builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLabels(subtitle.label)
                .build()
        }
}
