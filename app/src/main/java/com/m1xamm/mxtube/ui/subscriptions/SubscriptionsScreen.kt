@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.m1xamm.mxtube.ui.subscriptions

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.m1xamm.mxtube.R
import com.m1xamm.mxtube.data.local.SubscriptionEntry
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.ui.home.HomeVideoCard
import com.m1xamm.mxtube.ui.home.HomeVideoCardSkeleton

@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel,
    listState: LazyListState,
    onVideoClick: (VideoSummary, List<VideoSummary>) -> Unit,
    onChannelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onVisible() }
    SubscriptionsContent(
        state = state,
        listState = listState,
        onVideoClick = onVideoClick,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onChannelSelected = viewModel::selectChannel,
        onChannelClick = onChannelClick,
        modifier = modifier,
    )
}

@Composable
private fun SubscriptionsContent(
    state: SubscriptionsUiState,
    listState: LazyListState,
    onVideoClick: (VideoSummary, List<VideoSummary>) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onChannelSelected: (String?) -> Unit,
    onChannelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nearEnd by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            layout.totalItemsCount > 0 &&
                (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 3
        }
    }
    val hasFilterError = state.error || state.loadMoreError || state.failedChannels.isNotEmpty()
    LaunchedEffect(
        nearEnd, state.selectedChannelKey, state.videos.size, state.videos.lastOrNull()?.url,
        state.hasMore, state.isLoading, state.isRefreshing, state.isLoadingMore, hasFilterError,
    ) {
        if ((nearEnd || state.videos.isEmpty()) && state.hasMore &&
            !state.isLoading && !state.isRefreshing && !state.isLoadingMore && !hasFilterError
        ) onLoadMore()
    }
    Column(
        modifier = modifier.fillMaxSize().windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nav_subscriptions),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, enabled = state.channels.isNotEmpty() && !state.isRefreshing) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh_rounded),
                    contentDescription = stringResource(R.string.subscriptions_refresh),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        ChannelsStrip(state.channels, state.selectedChannelKey, onChannelSelected)
        state.channels.firstOrNull { it.key == state.selectedChannelKey }?.let { channel ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(channel.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { onChannelClick(channel.url) }) {
                    Text(stringResource(R.string.channel_open))
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            SubscriptionsList(state, listState, onVideoClick, onRetry)
        }
    }
}

@Composable
private fun ChannelsStrip(
    channels: List<SubscriptionEntry>,
    selectedKey: String?,
    onSelected: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "subscriptions_all") {
            ChannelControl(
                label = stringResource(R.string.subscriptions_all),
                avatarUrl = null,
                selected = selectedKey == null,
                onClick = { onSelected(null) },
                all = true,
            )
        }
        items(channels, key = { "channel:${it.key}" }) { channel ->
            ChannelControl(
                label = channel.name.ifBlank { channel.key },
                avatarUrl = channel.avatarUrl,
                selected = channel.key == selectedKey,
                onClick = { onSelected(channel.key) },
            )
        }
    }
}

@Composable
private fun ChannelControl(
    label: String,
    avatarUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
    all: Boolean = false,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.width(72.dp).clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(2.dp, if (selected) color else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (all) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_subscriptions),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Text(
                    text = label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SubscriptionsList(
    state: SubscriptionsUiState,
    listState: LazyListState,
    onVideoClick: (VideoSummary, List<VideoSummary>) -> Unit,
    onRetry: () -> Unit,
) {
    val bottomPadding = 96.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isBusy = state.isLoading || state.isRefreshing || state.isLoadingMore
    val hasFilterError = state.error || state.loadMoreError || state.failedChannels.isNotEmpty()
    val showSkeleton = state.videos.isEmpty() && (isBusy || (state.hasMore && !hasFilterError))
    val alpha = if (showSkeleton) skeletonAlpha() else 0.6f
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when {
            showSkeleton -> items(4, key = { "skeleton:$it" }) { index ->
                HomeVideoCardSkeleton(
                    alpha = alpha,
                    topCorner = if (index == 0) 28.dp else 4.dp,
                    bottomCorner = if (index == 3) 28.dp else 4.dp,
                )
            }
            state.channels.isEmpty() -> item(key = "empty_subscriptions") {
                FeedMessage(
                    title = stringResource(R.string.subscriptions_empty_title),
                    message = stringResource(R.string.subscriptions_empty_message),
                    modifier = Modifier.fillParentMaxHeight(0.8f),
                )
            }
            hasFilterError && state.videos.isEmpty() -> item(key = "subscriptions_error") {
                FeedMessage(
                    title = stringResource(if (state.loadMoreError) R.string.load_more_error else R.string.subscriptions_error),
                    message = stringResource(R.string.home_error_message),
                    onRetry = onRetry,
                    retryEnabled = !isBusy,
                    modifier = Modifier.fillParentMaxHeight(0.8f),
                )
            }
            state.videos.isEmpty() && !state.hasMore -> item(key = "no_videos") {
                FeedMessage(
                    title = stringResource(R.string.subscriptions_no_videos),
                    modifier = Modifier.fillParentMaxHeight(0.8f),
                )
            }
            else -> {
                if (state.failedChannels.isNotEmpty()) {
                    item(key = "partial_error") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(
                                text = stringResource(R.string.subscriptions_partial_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = state.failedChannels.joinToString(", ") { it.name.ifBlank { it.key } },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = onRetry, enabled = !isBusy) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                itemsIndexed(state.videos, key = { _, video -> "video:${video.url}" }) { index, video ->
                    HomeVideoCard(
                        video = video,
                        onClick = {
                            val nearby = state.videos.subList(
                                (index - 5).coerceAtLeast(0),
                                (index + 16).coerceAtMost(state.videos.size),
                            ).filterNot { it.url == video.url }
                            onVideoClick(video, nearby)
                        },
                        topCorner = if (index == 0) 28.dp else 4.dp,
                        bottomCorner = if (index == state.videos.lastIndex) 28.dp else 4.dp,
                        modifier = Modifier.animateItem(),
                    )
                }
                if (state.isLoadingMore) {
                    item(key = "loading_more") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LoadingIndicator(modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.subscriptions_loading_more),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                } else if (state.loadMoreError) {
                    item(key = "load_more_error") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.load_more_error),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRetry, enabled = !isBusy) { Text(stringResource(R.string.retry)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedMessage(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    retryEnabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry, enabled = retryEnabled) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun skeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "subscriptions-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "subscriptions-skeleton-alpha",
    )
    return alpha
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SubscriptionsScreenPreview() {
    MaterialTheme {
        SubscriptionsContent(
            state = SubscriptionsUiState(isLoading = false),
            listState = rememberLazyListState(),
            onVideoClick = { _, _ -> },
            onRefresh = {},
            onRetry = {},
            onLoadMore = {},
            onChannelSelected = {},
            onChannelClick = {},
        )
    }
}
