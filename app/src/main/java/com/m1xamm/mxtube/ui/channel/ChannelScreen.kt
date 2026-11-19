@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.m1xamm.mxtube.ui.channel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.m1xamm.mxtube.R
import com.m1xamm.mxtube.data.local.SubscriptionNotificationMode
import com.m1xamm.mxtube.data.youtube.PlaylistSummary
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.notifications.SubscriptionNotificationScheduler
import com.m1xamm.mxtube.playback.PlaybackHolder
import com.m1xamm.mxtube.ui.common.GlassBottomSheet
import com.m1xamm.mxtube.ui.common.ExpressiveRefreshIndicator
import com.m1xamm.mxtube.ui.common.LocalMiniPlayerVisible
import com.m1xamm.mxtube.ui.common.clusterCardColor
import com.m1xamm.mxtube.ui.common.clusterInnerColor
import com.m1xamm.mxtube.ui.common.compactCount
import com.m1xamm.mxtube.ui.common.formatDuration
import com.m1xamm.mxtube.ui.common.formatRelativeDate
import com.m1xamm.mxtube.ui.common.heroImage
import com.m1xamm.mxtube.ui.player.SubscribePill
import com.m1xamm.mxtube.ui.player.SubscriptionMenuSheet
import com.m1xamm.mxtube.ui.search.VideoRow
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun ChannelScreen(
    viewModel: ChannelViewModel,
    onBack: () -> Unit,
    onVideoClick: (VideoSummary, List<VideoSummary>) -> Unit,
    onPlaylistQueue: (com.m1xamm.mxtube.ui.player.PlaylistQueue) -> Unit = {},
    modifier: Modifier = Modifier,
    onSheetVisibilityChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val header = state.header
    val haze = rememberHazeState()
    val homeList = rememberLazyListState()
    val videosList = rememberLazyListState()
    val playlistsList = rememberLazyListState()
    val shortsList = rememberLazyListState()
    val livesList = rememberLazyListState()
    val postsList = rememberLazyListState()
    val tabRowState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val miniVisible = LocalMiniPlayerVisible.current
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (granted) SubscriptionNotificationScheduler.requestCheck(context)
    }
    val requestNotifications: () -> Unit = {
        if (!notificationsEnabled) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (enabled && !notificationsEnabled) SubscriptionNotificationScheduler.requestCheck(context)
        notificationsEnabled = enabled
    }
    BackHandler {
        when {
            aboutOpen -> aboutOpen = false
            overflowOpen -> overflowOpen = false
            menuOpen -> menuOpen = false
            searchOpen -> searchOpen = false
            state.selectedPlaylist != null -> viewModel.closePlaylist()
            else -> onBack()
        }
    }
    LaunchedEffect(state.selectedTab, state.videos.size, state.playlists.size, state.hasMore, state.hasMorePlaylists, state.videosError, state.playlistsError) {
        if (state.selectedTab == ChannelProfileTab.Home &&
            ((state.videos.isEmpty() && state.hasMore) || (state.playlists.isEmpty() && state.hasMorePlaylists))
        ) viewModel.loadMore()
    }
    val sheetVisible = menuOpen || searchOpen
    LaunchedEffect(sheetVisible) { onSheetVisibilityChange(sheetVisible) }
    DisposableEffect(Unit) { onDispose { onSheetVisibilityChange(false) } }
    val activeList = when (state.selectedTab) {
        ChannelProfileTab.Home -> homeList
        ChannelProfileTab.Videos -> videosList
        ChannelProfileTab.Playlists -> playlistsList
        ChannelProfileTab.Shorts -> shortsList
        ChannelProfileTab.Lives -> livesList
        ChannelProfileTab.Posts -> postsList
    }
    val banner = header?.bannerUrl?.takeIf { it.isNotBlank() }
    val headerIndex = if (banner != null) 1 else 0
    val scrolledPastHeader by remember(activeList, headerIndex) {
        derivedStateOf {
            activeList.firstVisibleItemIndex > headerIndex ||
                (activeList.firstVisibleItemIndex == headerIndex && activeList.firstVisibleItemScrollOffset > 0)
        }
    }
    val tabsVisible = state.selectedPlaylist == null && state.availableTabs.size > 1
    val shareUrl = state.selectedPlaylist?.url ?: header?.url
    val onShare: () -> Unit = {
        shareUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { url ->
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url),
                    context.getString(R.string.channel_share),
                ),
            )
        }
    }
    val onSubscribe: () -> Unit = {
        if (state.isSubscribed) menuOpen = true else {
            viewModel.subscribe()
            requestNotifications()
        }
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val selectedPlaylist = state.selectedPlaylist
        val refreshState = rememberPullToRefreshState()
        val busy =
            state.isRefreshing ||
                state.isLoading ||
                state.isLoadingVideos ||
                state.isLoadingPlaylists ||
                state.isLoadingPlaylist
        PullToRefreshBox(
            isRefreshing = busy,
            onRefresh = viewModel::refresh,
            state = refreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {},
        ) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                if (selectedPlaylist != null) {
                    key(selectedPlaylist.url) {
                        PlaylistDetailList(
                            state = state,
                            playlist = selectedPlaylist,
                            hasMiniPlayer = miniVisible,
                            onVideoClick = onVideoClick,
                            onPlaylistQueue = onPlaylistQueue,
                            onRetry = viewModel::retryPlaylist,
                            onLoadMore = viewModel::loadMore,
                        )
                    }
                } else {
                    Crossfade(
                        targetState = state.selectedTab,
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        label = "channel-tab-content",
                    ) { tab ->
                        when (tab) {
                            ChannelProfileTab.Home -> ChannelList(homeList, state, miniVisible, tabsVisible, viewModel::loadMore, viewModel::load, { aboutOpen = true }, onSubscribe, viewModel::selectTab, tabRowState) {
                                homeContent(state, onVideoClick, viewModel::openPlaylist, viewModel::selectTab, viewModel::retryVideos, viewModel::retryPlaylists)
                            }
                            ChannelProfileTab.Videos -> ChannelList(videosList, state, miniVisible, tabsVisible, viewModel::loadMore, viewModel::load, { aboutOpen = true }, onSubscribe, viewModel::selectTab, tabRowState) {
                                videosContent(state, onVideoClick, viewModel::retryVideos)
                            }
                            ChannelProfileTab.Playlists -> ChannelList(playlistsList, state, miniVisible, tabsVisible, viewModel::loadMore, viewModel::load, { aboutOpen = true }, onSubscribe, viewModel::selectTab, tabRowState) {
                                playlistsContent(state, viewModel::openPlaylist, viewModel::retryPlaylists)
                            }
                            ChannelProfileTab.Shorts -> ChannelList(shortsList, state, miniVisible, tabsVisible, viewModel::loadMore, viewModel::load, { aboutOpen = true }, onSubscribe, viewModel::selectTab, tabRowState) {
                                item(key = "shorts_soon") { EmptyItem(stringResource(R.string.section_soon)) }
                            }
                            ChannelProfileTab.Lives -> ChannelList(livesList, state, miniVisible, tabsVisible, viewModel::loadMore, viewModel::load, { aboutOpen = true }, onSubscribe, viewModel::selectTab, tabRowState) {
                                item(key = "lives_soon") { EmptyItem(stringResource(R.string.section_soon)) }
                            }
                            ChannelProfileTab.Posts -> ChannelList(postsList, state, miniVisible, tabsVisible, viewModel::loadMore, viewModel::load, { aboutOpen = true }, onSubscribe, viewModel::selectTab, tabRowState) {
                                item(key = "posts_soon") { EmptyItem(stringResource(R.string.section_soon)) }
                            }
                        }
                    }
                }
            }
        }
        if (overflowOpen) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { overflowOpen = false },
                ),
            )
        }
        val topInset =
            WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .height(topInset + 8.dp)
                .background(MaterialTheme.colorScheme.background),
        )
        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            ChannelActionToolbar(
                title = selectedPlaylist?.name ?: header?.name.orEmpty(),
                titleVisible = selectedPlaylist != null || scrolledPastHeader,
                tabsVisible = tabsVisible,
                state = state,
                tabRowState = tabRowState,
                onSelectTab = viewModel::selectTab,
                expanded = overflowOpen,
                canShare = shareUrl != null,
                hasChannel = header != null,
                isBlocked = state.isBlocked,
                haze = haze,
                onBack = {
                    when {
                        overflowOpen -> overflowOpen = false
                        selectedPlaylist != null -> viewModel.closePlaylist()
                        else -> onBack()
                    }
                },
                onSearch = { overflowOpen = false; searchOpen = true },
                onToggleMenu = { overflowOpen = !overflowOpen },
                onShare = {
                    overflowOpen = false
                    onShare()
                },
                onBlock = {
                    overflowOpen = false
                    val wasBlocked = state.isBlocked
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val saved = if (wasBlocked) viewModel.unblock() else viewModel.block()
                        if (!saved) {
                            snackbar.showSnackbar(context.getString(R.string.channel_block_error))
                        } else if (!wasBlocked) {
                            if (snackbar.showSnackbar(
                                    message = context.getString(R.string.channel_blocked_message),
                                    actionLabel = context.getString(R.string.undo),
                                    duration = SnackbarDuration.Short,
                                ) == SnackbarResult.ActionPerformed && !viewModel.unblock()
                            ) {
                                snackbar.showSnackbar(context.getString(R.string.channel_block_error))
                            }
                        }
                    }
                },
                onAbout = {
                    overflowOpen = false
                    aboutOpen = true
                },
            )
        }
        ExpressiveRefreshIndicator(
            state = refreshState,
            isRefreshing = busy,
        )
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(bottom = if (miniVisible) 136.dp else 0.dp),
        ) { data ->
            GlassSnackbar(data, haze)
        }
        if (menuOpen) {
            SubscriptionMenuSheet(
                mode = state.notificationMode,
                hazeState = haze,
                notificationsEnabled = notificationsEnabled,
                onEnableNotifications = requestNotifications,
                onModeSelected = { mode ->
                    viewModel.setNotificationMode(mode)
                    if (mode != SubscriptionNotificationMode.None) requestNotifications()
                },
                onUnsubscribe = viewModel::unsubscribe,
                onDismiss = { menuOpen = false },
            )
        }
        AnimatedVisibility(
            visible = aboutOpen && header != null,
            enter =
                fadeIn(tween(180)) +
                    slideInVertically(animationSpec = tween(260, easing = FastOutSlowInEasing)) { it / 20 },
            exit =
                fadeOut(tween(140)) +
                    slideOutVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) { it / 24 },
        ) {
            header?.let {
                AboutChannelScreen(
                    header = it,
                    onBack = { aboutOpen = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (searchOpen) {
            GlassBottomSheet(hazeState = haze, title = stringResource(R.string.channel_search), onDismiss = { searchOpen = false }) { _ ->
                Text(stringResource(R.string.section_soon), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChannelActionToolbar(
    title: String,
    titleVisible: Boolean,
    tabsVisible: Boolean,
    state: ChannelUiState,
    tabRowState: androidx.compose.foundation.lazy.LazyListState,
    onSelectTab: (ChannelProfileTab) -> Unit,
    expanded: Boolean,
    canShare: Boolean,
    hasChannel: Boolean,
    isBlocked: Boolean,
    haze: HazeState,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onToggleMenu: () -> Unit,
    onShare: () -> Unit,
    onBlock: () -> Unit,
    onAbout: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(
        targetValue = if (expanded) 28.dp else 24.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "channel-toolbar-corner",
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = motion.defaultEffectsSpec(),
        label = "channel-toolbar-title",
    )
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier =
            Modifier.fillMaxWidth().drawWithContent {
                drawContent()
                val inset = 8.dp.toPx()
                val radius = corner.toPx()
                val outer = Path().apply { addRect(Rect(0f, 0f, size.width, size.height - inset)) }
                val panel =
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(inset, inset, size.width - inset, size.height - inset),
                                cornerRadius = CornerRadius(radius, radius),
                            ),
                        )
                    }
                drawPath(
                    Path.combine(PathOperation.Difference, outer, panel),
                    background,
                )
            },
    ) {
        Column(
            Modifier.padding(8.dp).fillMaxWidth().clip(RoundedCornerShape(corner))
                .hazeBlur(input = HazeInput.Sources(haze), style = HazeMaterials.ultraThin())
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.48f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back_rounded), stringResource(R.string.player_back), Modifier.size(24.dp))
            }
            Text(
                title,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = titleAlpha },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onSearch) {
                Icon(painterResource(R.drawable.ic_search_rounded), stringResource(R.string.channel_search), Modifier.size(24.dp))
            }
            IconButton(onClick = onToggleMenu) {
                MorphingOverflowIcon(expanded)
            }
        }
        if (tabsVisible) {
            ChannelTabGroup(state, onSelectTab, tabRowState, Modifier.padding(bottom = 4.dp))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = motion.defaultSpatialSpec(), expandFrom = Alignment.Top) +
                fadeIn(animationSpec = motion.defaultEffectsSpec()),
            exit = shrinkVertically(animationSpec = motion.defaultSpatialSpec(), shrinkTowards = Alignment.Top) +
                fadeOut(animationSpec = motion.defaultEffectsSpec()),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ChannelMenuRow(
                    label = stringResource(R.string.channel_share),
                    icon = R.drawable.ic_share_rounded,
                    enabled = canShare,
                    topCorner = 24.dp,
                    bottomCorner = 4.dp,
                    onClick = onShare,
                )
                ChannelMenuRow(
                    label = stringResource(if (isBlocked) R.string.channel_unblock else R.string.channel_block),
                    icon = R.drawable.ic_block_rounded,
                    enabled = hasChannel,
                    topCorner = 4.dp,
                    bottomCorner = 4.dp,
                    onClick = onBlock,
                )
                ChannelMenuRow(
                    label = stringResource(R.string.channel_about),
                    icon = R.drawable.ic_info_rounded,
                    enabled = hasChannel,
                    topCorner = 4.dp,
                    bottomCorner = 24.dp,
                    onClick = onAbout,
                )
            }
        }
    }
    }
}

@Composable
private fun ChannelMenuRow(
    label: String,
    icon: Int,
    enabled: Boolean,
    topCorner: Dp,
    bottomCorner: Dp,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = topCorner, topEnd = topCorner, bottomStart = bottomCorner, bottomEnd = bottomCorner),
        color = clusterCardColor().copy(alpha = 0.60f),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(icon), null, Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MorphingOverflowIcon(expanded: Boolean) {
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "channel-overflow-morph",
    )
    val color = MaterialTheme.colorScheme.onSurface
    val label = stringResource(if (expanded) R.string.close else R.string.channel_more)
    Canvas(Modifier.size(24.dp).semantics { contentDescription = label }) {
        val unit = size.width / 24f
        val p = progress.coerceIn(0f, 1f)
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset((12f - 5f * p) * unit, (6f + p) * unit),
            end = androidx.compose.ui.geometry.Offset((12f + 5f * p) * unit, (6f + 11f * p) * unit),
            strokeWidth = 2.5f * unit,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset((12f - 5f * p) * unit, (18f - p) * unit),
            end = androidx.compose.ui.geometry.Offset((12f + 5f * p) * unit, (18f - 11f * p) * unit),
            strokeWidth = 2.5f * unit,
            cap = StrokeCap.Round,
        )
        drawCircle(color, radius = 1.5f * unit * (1f - p), center = center)
    }
}

@Composable
private fun ChannelTabGroup(
    state: ChannelUiState,
    onSelectTab: (ChannelProfileTab) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        itemsIndexed(state.availableTabs, key = { _, tab -> tab }) { index, tab ->
            ToggleButton(
                checked = state.selectedTab == tab,
                onCheckedChange = { onSelectTab(tab) },
                buttonSize = ToggleButtonSize.Small,
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        state.availableTabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                elevation = null,
                modifier = Modifier.semantics { role = Role.RadioButton },
            ) {
                Text(
                    text = stringResource(
                        when (tab) {
                            ChannelProfileTab.Home -> R.string.channel_home
                            ChannelProfileTab.Videos -> R.string.channel_videos
                            ChannelProfileTab.Shorts -> R.string.nav_shorts
                            ChannelProfileTab.Lives -> R.string.channel_lives
                            ChannelProfileTab.Playlists -> R.string.channel_playlists
                            ChannelProfileTab.Posts -> R.string.channel_posts
                        },
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ChannelList(
    list: androidx.compose.foundation.lazy.LazyListState,
    state: ChannelUiState,
    hasMiniPlayer: Boolean,
    hasTabs: Boolean,
    onNearEnd: () -> Unit,
    onRetryMetadata: () -> Unit,
    onAbout: () -> Unit,
    onSubscribe: () -> Unit,
    onSelectTab: (ChannelProfileTab) -> Unit,
    tabRowState: androidx.compose.foundation.lazy.LazyListState,
    content: LazyListScope.() -> Unit,
) {
    val nearEnd by remember { derivedStateOf {
        list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } == true
    } }
    LaunchedEffect(
        nearEnd, state.selectedTab, state.videos.size, state.playlists.size,
        state.hasMore, state.hasMorePlaylists, state.isLoadingMore, state.isLoadingMorePlaylists,
        state.videosError, state.playlistsError,
    ) {
        if (nearEnd) onNearEnd()
    }
    val header = state.header
    val banner = header?.bannerUrl?.takeIf { it.isNotBlank() }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        ),
        contentPadding = PaddingValues(
            top = if (hasTabs) 116.dp else 72.dp,
            bottom = if (hasMiniPlayer) 160.dp else 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (banner != null) {
            item(key = "banner") {
                AsyncImage(
                    model = banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(24.dp)),
                )
            }
        }
        if (header != null) {
            item(key = "header") { ChannelHeaderContent(header, state, onAbout, onSubscribe) }
        }
        if (state.error) {
            item(key = "metadata_error") { ErrorItem(stringResource(R.string.channel_error), onRetryMetadata) }
        }
        content()
    }
}

@Composable
private fun ChannelHeaderContent(
    header: com.m1xamm.mxtube.data.youtube.ChannelHeader,
    state: ChannelUiState,
    onAbout: () -> Unit,
    onSubscribe: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(header.name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall)
                AsyncImage(model = header.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = header.name.ifBlank { stringResource(R.string.unknown_channel) },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (header.isVerified) {
                        Icon(
                            painterResource(R.drawable.ic_verified_rounded),
                            contentDescription = stringResource(R.string.channel_verified),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                header.handle?.takeIf { it.isNotBlank() }?.let { handle ->
                    Text(handle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val meta = listOfNotNull(
                    header.subscriberCount.takeIf { it >= 0 }?.let { stringResource(R.string.channel_subscribers, compactCount(it)) },
                    header.videoCount.takeIf { it >= 0 }?.let { stringResource(R.string.channel_video_count, compactCount(it)) },
                ).joinToString(" • ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (header.description.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = header.description.lineSequence().filter { it.isNotBlank() }.joinToString(" "),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onAbout) { Text(stringResource(R.string.channel_more)) }
            }
        }
        SubscribePill(
            subscribed = state.isSubscribed,
            pending = false,
            notificationMode = state.notificationMode,
            onClick = onSubscribe,
            fullWidth = true,
            channelName = header.name,
        )
    }
}

private fun LazyListScope.homeContent(
    state: ChannelUiState,
    onVideoClick: (VideoSummary, List<VideoSummary>) -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onSelectTab: (ChannelProfileTab) -> Unit,
    onRetryVideos: () -> Unit,
    onRetryPlaylists: () -> Unit,
) {
    if ((state.isLoadingVideos || state.isLoadingPlaylists) && state.videos.isEmpty() && state.playlists.isEmpty()) {
        item(key = "home_loading") { LoadingItem(compact = true) }
    }
    if (state.videosError && state.videos.isEmpty()) {
        item(key = "home_videos_error") { ErrorItem(stringResource(R.string.channel_videos_error), onRetryVideos) }
    }
    if (state.playlistsError && state.playlists.isEmpty()) {
        item(key = "home_playlists_error") { ErrorItem(stringResource(R.string.channel_playlists_error), onRetryPlaylists) }
    }
    if (state.videos.isNotEmpty()) {
        item(key = "home_videos_title") {
            SectionTitle(stringResource(R.string.channel_videos), showAll = ChannelProfileTab.Videos in state.availableTabs) { onSelectTab(ChannelProfileTab.Videos) }
        }
        item(key = "home_videos_row") {
            val videos = state.videos.take(6)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(videos, key = { _, video -> video.url }) { index, video ->
                    OverviewCard(
                        video = video,
                        startCorner = if (index == 0) 28.dp else 4.dp,
                        endCorner = if (index == videos.lastIndex) 28.dp else 4.dp,
                    ) { onVideoClick(video, state.videos.filterNot { it.url == video.url }.take(16)) }
                }
            }
        }
    }
    if (state.playlists.isNotEmpty()) {
        item(key = "home_playlists_title") {
            SectionTitle(stringResource(R.string.channel_playlists), showAll = ChannelProfileTab.Playlists in state.availableTabs) { onSelectTab(ChannelProfileTab.Playlists) }
        }
        val playlists = state.playlists.take(4)
        itemsIndexed(playlists, key = { _, playlist -> "home_${playlist.url}" }) { index, playlist ->
            PlaylistRow(
                playlist,
                topCorner = if (index == 0) 28.dp else 4.dp,
                bottomCorner = if (index == playlists.lastIndex) 28.dp else 4.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { onOpenPlaylist(playlist) }
        }
    }
    if (!state.isLoadingVideos && !state.isLoadingPlaylists && !state.videosError && !state.playlistsError && state.videos.isEmpty() && state.playlists.isEmpty()) {
        item(key = "home_empty") { EmptyItem(stringResource(R.string.home_empty_title)) }
    }
    homePagination(state, onRetryVideos, onRetryPlaylists)
}

private fun LazyListScope.videosContent(
    state: ChannelUiState,
    onVideoClick: (VideoSummary, List<VideoSummary>) -> Unit,
    onRetry: () -> Unit,
) {
    if (state.videosError && state.videos.isEmpty()) {
        item(key = "videos_error") { ErrorItem(stringResource(R.string.channel_videos_error), onRetry) }
    }
    if (state.isLoadingVideos && state.videos.isEmpty() && !state.videosError) {
        item(key = "videos_loading") { LoadingItem(compact = true) }
    }
    itemsIndexed(state.videos, key = { _, video -> video.url }) { index, video ->
        VideoRow(
            video = video,
            onClick = { onVideoClick(video, state.videos.filterNot { it.url == video.url }.take(16)) },
            topCorner = if (index == 0) 28.dp else 4.dp,
            bottomCorner = if (index == state.videos.lastIndex) 28.dp else 4.dp,
            showUploader = false,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    if (!state.isLoadingVideos && !state.videosError && state.videos.isEmpty()) {
        item(key = "videos_empty") { EmptyItem(stringResource(R.string.subscriptions_no_videos)) }
    }
    if (state.isLoadingMore) {
        item(key = "videos_loading_more") { LoadingItem(compact = true) }
    } else if (state.videosError && state.videos.isNotEmpty()) {
        item(key = "videos_more_error") { MoreErrorItem(onRetry) }
    }
}

private fun LazyListScope.playlistsContent(
    state: ChannelUiState,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onRetry: () -> Unit,
) {
    if (state.playlistsError && state.playlists.isEmpty()) {
        item(key = "playlists_error") { ErrorItem(stringResource(R.string.channel_playlists_error), onRetry) }
    }
    itemsIndexed(state.playlists, key = { _, playlist -> playlist.url }) { index, playlist ->
        PlaylistRow(
            playlist = playlist,
            topCorner = if (index == 0) 28.dp else 4.dp,
            bottomCorner = if (index == state.playlists.lastIndex) 28.dp else 4.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { onOpenPlaylist(playlist) }
    }
    if (!state.isLoadingPlaylists && !state.playlistsError && state.playlists.isEmpty()) {
        item(key = "playlists_empty") { EmptyItem(stringResource(R.string.channel_playlists_empty)) }
    }
    if (state.isLoadingMorePlaylists) {
        item(key = "playlists_loading_more") { LoadingItem(compact = true) }
    } else if (state.playlistsError && state.playlists.isNotEmpty()) {
        item(key = "playlists_more_error") { MoreErrorItem(onRetry) }
    }
}

private fun LazyListScope.homePagination(state: ChannelUiState, onRetryVideos: () -> Unit, onRetryPlaylists: () -> Unit) {
    if (state.isLoadingMore || state.isLoadingMorePlaylists) {
        item(key = "home_loading_more") { LoadingItem(compact = true) }
    } else {
        if (state.videosError && state.videos.isNotEmpty()) item(key = "home_videos_more_error") { MoreErrorItem(onRetryVideos) }
        if (state.playlistsError && state.playlists.isNotEmpty()) item(key = "home_playlists_more_error") { MoreErrorItem(onRetryPlaylists) }
    }
}

@Composable
private fun PlaylistDetailList(
    state: ChannelUiState,
    playlist: PlaylistSummary,
    hasMiniPlayer: Boolean,
    onVideoClick: (VideoSummary, List<VideoSummary>) -> Unit,
    onPlaylistQueue: (com.m1xamm.mxtube.ui.player.PlaylistQueue) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf {
        list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } == true
    } }
    LaunchedEffect(nearEnd, state.playlistVideos.size, state.playlistHasMore, state.isLoadingMorePlaylist, state.playlistError) {
        if (nearEnd) onLoadMore()
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        ),
        contentPadding = PaddingValues(top = 72.dp, bottom = if (hasMiniPlayer) 160.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "playlist_header") {
            val placeholder = ColorPainter(clusterInnerColor())
            val first = state.playlistVideos.firstOrNull()
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = placeholder,
                    error = placeholder,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(24.dp)),
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val owner = playlist.uploader ?: state.header?.name
                    if (!owner.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.header?.avatarUrl?.let { avatar ->
                                AsyncImage(
                                    model = avatar,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    placeholder = placeholder,
                                    error = placeholder,
                                    modifier =
                                        Modifier.size(24.dp).clip(CircleShape),
                                )
                            }
                            Text(
                                owner,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        buildString {
                            append(stringResource(R.string.playlist_label))
                            if (playlist.videoCount >= 0) {
                                append(" • ")
                                append(
                                    stringResource(
                                        R.string.channel_video_count,
                                        compactCount(playlist.videoCount),
                                    ),
                                )
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (first != null) {
                    Button(
                        onClick = {
                            onPlaylistQueue(
                                com.m1xamm.mxtube.ui.player.PlaylistQueue(
                                    title = playlist.name,
                                    videos = state.playlistVideos,
                                    index = 0,
                                ),
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_arrow_rounded),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.playlist_play_all),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        if (state.isLoadingPlaylist) {
            item(key = "playlist_loading_space") { Spacer(Modifier.height(8.dp)) }
        }
        if (state.playlistError && state.playlistVideos.isEmpty()) {
            item(key = "playlist_error") { ErrorItem(stringResource(R.string.channel_playlist_error), onRetry) }
        }
        itemsIndexed(state.playlistVideos, key = { _, video -> video.url }) { index, video ->
            VideoRow(
                video = video,
                onClick = {
                    onPlaylistQueue(
                        com.m1xamm.mxtube.ui.player.PlaylistQueue(
                            title = playlist.name,
                            videos = state.playlistVideos,
                            index = index,
                        ),
                    )
                },
                topCorner = if (index == 0) 28.dp else 4.dp,
                bottomCorner = if (index == state.playlistVideos.lastIndex) 28.dp else 4.dp,
                showUploader = false,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (!state.isLoadingPlaylist && !state.playlistError && state.playlistVideos.isEmpty()) {
            item(key = "playlist_empty") { EmptyItem(stringResource(R.string.subscriptions_no_videos)) }
        }
        if (state.isLoadingMorePlaylist) {
            item(key = "playlist_loading_more") { LoadingItem(compact = true) }
        } else if (state.playlistError && state.playlistVideos.isNotEmpty()) {
            item(key = "playlist_more_error") { MoreErrorItem(onRetry) }
        }
    }
}

@Composable
private fun SectionTitle(title: String, showAll: Boolean, onShowAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (showAll) TextButton(onClick = onShowAll) { Text(stringResource(R.string.channel_show_all)) }
    }
}

@Composable
private fun OverviewCard(video: VideoSummary, startCorner: Dp, endCorner: Dp, onClick: () -> Unit) {
    val cardColor = clusterCardColor()
    val placeholder = ColorPainter(clusterInnerColor())
    val duration = formatDuration(video.durationSeconds)
    val meta = listOfNotNull(
        video.viewCount.takeIf { it >= 0 }?.let { compactCount(it) },
        video.uploadedAtEpochMillis?.let { formatRelativeDate(it) },
    ).joinToString(" • ")
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val animatedStartCorner by
        animateDpAsState(
            targetValue = if (pressed) 28.dp else startCorner,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "overview-start-corner",
        )
    val animatedEndCorner by
        animateDpAsState(
            targetValue = if (pressed) 28.dp else endCorner,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "overview-end-corner",
        )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(topStart = animatedStartCorner, bottomStart = animatedStartCorner, topEnd = animatedEndCorner, bottomEnd = animatedEndCorner),
        color = cardColor,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(clusterInnerColor()).heroImage(video.url)) {
                AsyncImage(video.thumbnailUrl, contentDescription = video.title, contentScale = ContentScale.Crop, placeholder = placeholder, error = placeholder, modifier = Modifier.fillMaxSize())
                if (duration.isNotBlank()) {
                    Text(
                        text = duration,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.76f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, topCorner: Dp, bottomCorner: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val placeholder = ColorPainter(clusterInnerColor())
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
            label = "playlist-top-corner",
        )
    val animatedBottomCorner by
        animateDpAsState(
            targetValue = if (pressed) 28.dp else bottomCorner,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "playlist-bottom-corner",
        )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = animatedTopCorner, topEnd = animatedTopCorner, bottomStart = animatedBottomCorner, bottomEnd = animatedBottomCorner),
        color = clusterCardColor(),
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
                modifier = Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(clusterInnerColor()),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (playlist.videoCount >= 0) {
                    Text(stringResource(R.string.channel_video_count, compactCount(playlist.videoCount)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LoadingItem(compact: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(if (compact) 16.dp else 32.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator(if (compact) Modifier.size(32.dp) else Modifier)
    }
}

@Composable
private fun EmptyItem(text: String) {
    Text(text, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ErrorItem(text: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun MoreErrorItem(onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.load_more_error), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}


@Composable
private fun GlassSnackbar(data: SnackbarData, haze: HazeState) {
    val hasAction = data.visuals.actionLabel != null
    Box(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .hazeBlur(input = HazeInput.Sources(haze), style = HazeMaterials.ultraThin())
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.32f)),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(if (hasAction) R.drawable.ic_block_rounded else R.drawable.ic_info_rounded),
                null,
                Modifier.size(24.dp),
            )
            Text(
                data.visuals.message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasAction) {
                TextButton(onClick = { data.performAction() }) {
                    Text(data.visuals.actionLabel.orEmpty())
                }
            }
        }
    }
}

