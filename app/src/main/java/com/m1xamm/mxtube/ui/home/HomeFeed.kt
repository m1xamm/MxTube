@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.m1xamm.mxtube.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.m1xamm.mxtube.R
import com.m1xamm.mxtube.data.home.HomeFeedItem
import com.m1xamm.mxtube.data.home.HomeTopic

@Composable
fun HomeFeed(
    state: HomeUiState,
    listState: LazyListState,
    onTopicSelected: (String) -> Unit,
    onVideoClick: (HomeFeedItem) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HomeTopicChips(
            topics = state.topics,
            selectedTopicId = state.selectedTopicId,
            onTopicSelected = onTopicSelected,
        )

        Box(Modifier.weight(1f)) {
            when {
                state.isLoading && state.items.isEmpty() -> HomeSkeleton()
                state.error && state.items.isEmpty() ->
                    HomeMessage(
                        title = stringResource(R.string.home_error_title),
                        message = stringResource(R.string.home_error_message),
                        actionLabel = stringResource(R.string.retry),
                        onAction = onRetry,
                    )

                state.items.isEmpty() ->
                    HomeMessage(
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_message),
                        actionLabel = stringResource(R.string.retry),
                        onAction = onRetry,
                    )

                else ->
                    HomeVideoList(
                        state = state,
                        listState = listState,
                        onVideoClick = onVideoClick,
                        onLoadMore = onLoadMore,
                        onRetryLoadMore = onRetryLoadMore,
                    )
            }
        }
    }
}

@Composable
private fun HomeTopicChips(
    topics: List<HomeTopic>,
    selectedTopicId: String,
    onTopicSelected: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(items = topics, key = { _, topic -> topic.id }) { index, topic ->
            TopicChip(
                topic = topic,
                selected = topic.id == selectedTopicId,
                isFirst = index == 0,
                isLast = index == topics.lastIndex,
                onClick = { onTopicSelected(topic.id) },
            )
        }
    }
}

@Composable
private fun TopicChip(
    topic: HomeTopic,
    selected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val restStart = if (selected || isFirst) 20.dp else 4.dp
    val restEnd = if (selected || isLast) 20.dp else 4.dp
    val startCorner by
        animateDpAsState(
            targetValue = if (pressed) 20.dp else restStart,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "home-topic-start-corner",
        )
    val endCorner by
        animateDpAsState(
            targetValue = if (pressed) 20.dp else restEnd,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "home-topic-end-corner",
        )
    val scale by
        animateFloatAsState(
            targetValue = if (pressed) 0.95f else 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            label = "home-topic-scale",
        )
    val container by
        animateColorAsState(
            targetValue =
                when {
                    selected -> MaterialTheme.colorScheme.primary
                    pressed -> MaterialTheme.colorScheme.surfaceContainerHighest
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
            animationSpec = tween(160),
            label = "home-topic-container",
        )
    val content by
        animateColorAsState(
            targetValue =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            animationSpec = tween(160),
            label = "home-topic-content",
        )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier =
            Modifier
                .height(42.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .semantics { this.selected = selected },
        shape =
            RoundedCornerShape(
                topStart = startCorner,
                topEnd = endCorner,
                bottomEnd = endCorner,
                bottomStart = startCorner,
            ),
        color = container,
        contentColor = content,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = topic.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HomeVideoList(
    state: HomeUiState,
    listState: LazyListState,
    onVideoClick: (HomeFeedItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
) {
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(
        shouldLoadMore,
        state.items.size,
        state.hasMore,
        state.isLoadingMore,
        state.loadMoreError,
    ) {
        if (shouldLoadMore && state.hasMore && !state.isLoadingMore && !state.loadMoreError) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(
            items = state.items,
            key = { _, item -> item.video.url },
        ) { index, item ->
            HomeVideoCard(
                video = item.video,
                onClick = { onVideoClick(item) },
                topCorner = if (index == 0) 28.dp else 4.dp,
                bottomCorner = if (index == state.items.lastIndex) 28.dp else 4.dp,
                modifier = Modifier.animateItem(),
            )
        }

        if (state.isLoadingMore) {
            item(key = "home_loading_more") {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else if (state.loadMoreError) {
            item(key = "home_load_more_error") {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.load_more_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetryLoadMore) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSkeleton() {
    val transition = rememberInfiniteTransition(label = "home-skeleton")
    val alpha by
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.8f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(900),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "home-skeleton-alpha",
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(List(4) { it }) { index, _ ->
            HomeVideoCardSkeleton(
                alpha = alpha,
                topCorner = if (index == 0) 28.dp else 4.dp,
                bottomCorner = if (index == 3) 28.dp else 4.dp,
            )
        }
    }
}

@Composable
private fun HomeMessage(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onAction,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(actionLabel)
        }
    }
}
