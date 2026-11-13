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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.m1xamm.mxtube.ui.common.clusterCardColor
import com.m1xamm.mxtube.ui.common.clusterInnerColor
import com.m1xamm.mxtube.ui.common.compactCount
import com.m1xamm.mxtube.ui.common.formatDuration
import com.m1xamm.mxtube.ui.common.formatRelativeDate
import com.m1xamm.mxtube.ui.player.SubscriptionMenuSheet
import com.m1xamm.mxtube.ui.player.subscriptionNotificationIcon
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
    miniPlayer: VideoSummary?,
    onReturnToPlayer: () -> Unit,
    onClosePlayer: () -> Unit,
    modifier: Modifier = Modifier,
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
    val pinnedTabRowState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
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
            overflowOpen -> overflowOpen = false
            state.selectedPlaylist != null -> viewModel.closePlaylist()
            else -> onBack()
        }
    }
    LaunchedEffect(state.selectedTab, state.videos.size, state.playlists.size, state.hasMore, state.hasMorePlaylists, state.videosError, state.playlistsError) {
        if (state.selectedTab == ChannelProfileTab.Home &&
            ((state.videos.isEmpty() && state.hasMore) || (state.playlists.isEmpty() && state.hasMorePlaylists))
        ) viewModel.loadMore()
    }
    val activeList = when (state.selectedTab) {
        ChannelProfileTab.Home -> homeList
        ChannelProfileTab.Videos -> videosList
        ChannelProfileTab.Playlists -> playlistsList
        ChannelProfileTab.Shorts -> shortsList
        ChannelProfileTab.Lives -> livesList
        ChannelProfileTab.Posts -> postsList
    }
    val tabsIndex = (if (header?.bannerUrl?.isNotBlank() == true) 1 else 0) + 1
    val scrolledPastHeader by remember(activeList, tabsIndex) {
        derivedStateOf { activeList.firstVisibleItemIndex >= tabsIndex }
    }
    val tabsVisible = state.selectedPlaylist == null && state.availableTabs.size > 1
    val pinnedTabs = tabsVisible && header != null && scrolledPastHeader
    LaunchedEffect(pinnedTabs) {
        if (pinnedTabs) {
            pinnedTabRowState.scrollToItem(tabRowState.firstVisibleItemIndex, tabRowState.firstVisibleItemScrollOffset)
        } else {
            tabRowState.scrollToItem(pinnedTabRowState.firstVisibleItemIndex, pinnedTabRowState.firstVisibleItemScrollOffset)
        }
    }
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
        PullToRefreshBox(
            isRefreshing = state.isRefreshing || state.isLoadingPlaylist,
            onRefresh = viewModel::refresh,
            state = refreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState,
                    isRefreshing = state.isRefreshing || state.isLoadingPlaylist,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        .padding(top = 64.dp),
                )
            },
        ) {
            if (selectedPlaylist != null) {
                key(selectedPlaylist.url) {
                    PlaylistDetailList(
                        state = state,
                        playlist = selectedPlaylist,
                        haze = haze,
                        hasMiniPlayer = miniPlayer != null,
                        onVideoClick = onVideoClick,
                        onRetry = viewModel::retryPlaylist,
                        onLoadMore = viewModel::loadMore,
                    )
                }
            } else {
                when (state.selectedTab) {
                    ChannelProfileTab.Home -> ChannelList(homeList, state, haze, miniPlayer != null, viewModel::loadMore, viewModel::load, { aboutOpen = true }, onSubscribe, viewModel::selectTab, tabRowState) {
                        homeContent(state, onVideoClick, viewModel::openPlaylist, viewModel::selectTab, viewModel::retryVideos, viewModel::retryPlaylists)
                    }
                    ChannelProfileTab.Videos -> ChannelList(videosList, state, haze, miniPlayer != null, viewModel::loadMore, viewModel::load, { aboutOpen = true }, onSubscribe, viewModel::selectTab, tabRowState) {
                        videosContent(state, onVideoClick, viewModel::retryVideos)
                    }
                    ChannelProfileTab.Playlists -> ChannelList(playlistsList, state, haze, miniPlayer != null, viewModel::loadMore, viewModel::load, { aboutOpen = true }, onSubscribe, viewModel::selectTab, tabRowState) {
                        playlistsContent(state, viewModel::openPlaylist, viewModel::retryPlaylists)
                    }
                    ChannelProfileTab.Shorts -> ChannelList(shortsList, state, haze, miniPlayer != null, viewModel::loadMore, viewModel::load, { aboutOpen = true }, onSubscribe, viewModel::selectTab, tabRowState) {
                        item(key = "shorts_soon") { EmptyItem(stringResource(R.string.section_soon)) }
                    }
                    ChannelProfileTab.Lives -> ChannelList(livesList, state, haze, miniPlayer != null, viewModel::loadMore, viewModel::load, { aboutOpen = true }, onSubscribe, viewModel::selectTab, tabRowState) {
                        item(key = "lives_soon") { EmptyItem(stringResource(R.string.section_soon)) }
                    }
                    ChannelProfileTab.Posts -> ChannelList(postsList, state, haze, miniPlayer != null, viewModel::loadMore, viewModel::load, { aboutOpen = true }, onSubscribe, viewModel::selectTab, tabRowState) {
                        item(key = "posts_soon") { EmptyItem(stringResource(R.string.section_soon)) }
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
        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            ChannelActionToolbar(
                title = selectedPlaylist?.name ?: if (scrolledPastHeader) header?.name.orEmpty() else "",
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
            if (pinnedTabs) {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    ChannelTabGroup(state, viewModel::selectTab, pinnedTabRowState)
                }
            }
        }
        miniPlayer?.let { video ->
            ChannelMiniPlayer(video, haze, onReturnToPlayer, onClosePlayer, Modifier.align(Alignment.BottomCenter))
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(bottom = if (miniPlayer != null) 72.dp else 0.dp),
        )
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
        if (aboutOpen && header != null) {
            GlassBottomSheet(hazeState = haze, title = stringResource(R.string.channel_about), onDismiss = { aboutOpen = false }) { _ ->
                AboutContent(header.name, header.handle, header.description)
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
    Column(
        Modifier.padding(8.dp).fillMaxWidth().clip(RoundedCornerShape(corner))
            .hazeBlur(input = HazeInput.Sources(haze), style = HazeMaterials.ultraThin())
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.32f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back_rounded), stringResource(R.string.player_back), Modifier.size(24.dp))
            }
            Text(
                title,
                modifier = Modifier.weight(1f),
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
            start = androidx.compose.ui.geometry.Offset((12f - 7f * p) * unit, (6f - p) * unit),
            end = androidx.compose.ui.geometry.Offset((12f + 7f * p) * unit, (6f + 13f * p) * unit),
            strokeWidth = 3f * unit,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset((12f - 7f * p) * unit, (18f + p) * unit),
            end = androidx.compose.ui.geometry.Offset((12f + 7f * p) * unit, (18f - 13f * p) * unit),
            strokeWidth = 3f * unit,
            cap = StrokeCap.Round,
        )
        drawCircle(color, radius = 1.5f * unit * (1f - p), center = center)
    }
}

@Composable
private fun ChannelTabGroup(state: ChannelUiState, onSelectTab: (ChannelProfileTab) -> Unit, listState: androidx.compose.foundation.lazy.LazyListState) {
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
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
    haze: HazeState,
    hasMiniPlayer: Boolean,
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
    val tabsVisible = state.selectedPlaylist == null && state.availableTabs.size > 1
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().hazeSource(haze).windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        ),
        contentPadding = PaddingValues(top = 72.dp, bottom = if (hasMiniPlayer) 96.dp else 32.dp),
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
        if (tabsVisible) {
            item(key = "tabs") { ChannelTabGroup(state, onSelectTab, tabRowState) }
        }
        if (state.isLoading) {
            item(key = "metadata_loading") { LoadingItem() }
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
        SubscribeButton(state, onSubscribe)
    }
}

@Composable
private fun SubscribeButton(state: ChannelUiState, onSubscribe: () -> Unit) {
    Button(
        onClick = onSubscribe,
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        colors = if (state.isSubscribed) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
    ) {
        if (state.isSubscribed) {
            Icon(painterResource(subscriptionNotificationIcon(state.notificationMode)), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(if (state.isSubscribed) R.string.subscribed else R.string.subscribe))
        if (state.isSubscribed) {
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_expand_more_rounded), contentDescription = null, modifier = Modifier.size(24.dp))
        }
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
    if (state.isLoadingVideos || state.isLoadingPlaylists) {
        item(key = "home_loading") { LoadingItem() }
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
    if (state.isLoadingVideos) item(key = "videos_loading") { LoadingItem() }
    if (state.videosError && state.videos.isEmpty()) {
        item(key = "videos_error") { ErrorItem(stringResource(R.string.channel_videos_error), onRetry) }
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
    if (state.isLoadingPlaylists) item(key = "playlists_loading") { LoadingItem() }
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
    haze: HazeState,
    hasMiniPlayer: Boolean,
    onVideoClick: (VideoSummary, List<VideoSummary>) -> Unit,
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
        modifier = Modifier.fillMaxSize().hazeSource(haze).windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        ),
        contentPadding = PaddingValues(top = 72.dp, bottom = if (hasMiniPlayer) 96.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "playlist_header") {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val placeholder = ColorPainter(clusterInnerColor())
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = placeholder,
                    error = placeholder,
                    modifier = Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (playlist.videoCount >= 0) {
                        Text(stringResource(R.string.channel_video_count, compactCount(playlist.videoCount)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (state.isLoadingPlaylist) item(key = "playlist_loading") { LoadingItem() }
        if (state.playlistError && state.playlistVideos.isEmpty()) {
            item(key = "playlist_error") { ErrorItem(stringResource(R.string.channel_playlist_error), onRetry) }
        }
        itemsIndexed(state.playlistVideos, key = { _, video -> video.url }) { index, video ->
            VideoRow(
                video = video,
                onClick = { onVideoClick(video, state.playlistVideos.filterNot { it.url == video.url }.take(16)) },
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
    Surface(
        onClick = onClick,
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(topStart = startCorner, bottomStart = startCorner, topEnd = endCorner, bottomEnd = endCorner),
        color = cardColor,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(clusterInnerColor())) {
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
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = topCorner, topEnd = topCorner, bottomStart = bottomCorner, bottomEnd = bottomCorner),
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
private fun AboutContent(name: String, handle: String?, description: String) {
    val primary = MaterialTheme.colorScheme.primary
    val annotated = remember(description, primary) {
        buildAnnotatedString {
            var last = 0
            Regex("""https?://[^\s<>"']+""").findAll(description).forEach { match ->
                append(description.substring(last, match.range.first))
                val url = match.value
                withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)))) { append(url) }
                last = match.range.last + 1
            }
            append(description.substring(last))
        }
    }
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge)
        handle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        SelectionContainer { Text(annotated, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun ChannelMiniPlayer(
    video: VideoSummary,
    haze: HazeState,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = PlaybackHolder.player(LocalContext.current)
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var title by remember(player, video.url) { mutableStateOf(player.mediaMetadata.title?.toString() ?: video.title) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { title = mediaMetadata.title?.toString() ?: video.title }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    Surface(
        onClick = onExpand,
        modifier = modifier.navigationBarsPadding().padding(8.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .hazeBlur(input = HazeInput.Sources(haze), style = HazeMaterials.ultraThin())
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.32f)),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
    ) {
        Row(Modifier.heightIn(min = 56.dp).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AsyncImage(video.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = {
                if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) player.pause()
                else {
                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                    player.play()
                }
            }) {
                Icon(painterResource(if (playing) R.drawable.ic_pause_rounded else R.drawable.ic_play_arrow_rounded), stringResource(if (playing) R.string.player_pause else R.string.player_play), Modifier.size(24.dp))
            }
            IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close_rounded), stringResource(R.string.close), Modifier.size(24.dp)) }
        }
    }
}