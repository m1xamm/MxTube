@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.m1xamm.mxtube.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m1xamm.mxtube.R
import com.m1xamm.mxtube.data.home.HomeTopics
import com.m1xamm.mxtube.data.local.SearchHistoryEntry
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.ui.common.BrandMark
import com.m1xamm.mxtube.ui.common.clusterCardColor
import com.m1xamm.mxtube.ui.common.clusterInnerColor
import com.m1xamm.mxtube.ui.home.HomeFeed
import com.m1xamm.mxtube.ui.home.HomeViewModel

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    homeViewModel: HomeViewModel,
    searchListState: LazyListState,
    homeListState: LazyListState,
    onVideoClick: (VideoSummary, Set<String>, String?, List<VideoSummary>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val submitSearch: (String) -> Unit = { query ->
        keyboard?.hide()
        viewModel.search(query)
    }

    LaunchedEffect(state.phase) {
        if (state.phase == SearchPhase.Loading) {
            runCatching { searchListState.scrollToItem(0) }
        }
    }

    var lastHomeTopicId by rememberSaveable { mutableStateOf(homeState.selectedTopicId) }
    LaunchedEffect(homeState.selectedTopicId) {
        if (homeState.selectedTopicId != lastHomeTopicId) {
            lastHomeTopicId = homeState.selectedTopicId
            runCatching { homeListState.scrollToItem(0) }
        }
    }

    BackHandler(enabled = state.isSearchActive) {
        keyboard?.hide()
        focusManager.clearFocus()
        viewModel.exitSearch()
        homeViewModel.onHomeVisible()
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
    ) {
        SearchHeader(
            input = state.input,
            isLoading = state.phase == SearchPhase.Loading,
            onInputChange = viewModel::onQueryChange,
            onFocused = viewModel::activateSearch,
            onClear = viewModel::clear,
            onSearch = { submitSearch(state.input) },
        )

        if (!state.isSearchActive) {
            HomeFeed(
                state = homeState,
                listState = homeListState,
                onTopicSelected = homeViewModel::selectTopic,
                onVideoClick = { item ->
                    val selected = homeState.selectedTopic
                    onVideoClick(
                        item.video,
                        item.topics,
                        selected.query.takeIf { selected.isDynamic },
                        homeState.items.map { it.video },
                    )
                },
                onRetry = homeViewModel::retry,
                onLoadMore = homeViewModel::loadMore,
                onRetryLoadMore = homeViewModel::retryLoadMore,
                modifier = Modifier.weight(1f),
            )
        } else {
            SearchContent(
                state = state,
                history = searchHistory,
                listState = searchListState,
                onVideoClick = { video ->
                    onVideoClick(
                        video,
                        HomeTopics.topicsFor(null, video.title, video.uploader, state.query),
                        state.query,
                        state.videos,
                    )
                },
                onHistoryClick = { query ->
                    viewModel.onQueryChange(query)
                    submitSearch(query)
                },
                onRemoveHistory = viewModel::removeSearchHistory,
                onClearSearchHistory = viewModel::clearSearchHistory,
                onClearAllHistory = { showClearHistoryDialog = true },
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadMore,
                onRetryLoadMore = viewModel::retryLoadMore,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory { homeViewModel.onHistoryCleared() }
                        showClearHistoryDialog = false
                    },
                ) {
                    Text(stringResource(R.string.clear_history_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SearchContent(
    state: SearchUiState,
    history: List<SearchHistoryEntry>,
    listState: LazyListState,
    onVideoClick: (VideoSummary) -> Unit,
    onHistoryClick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    onClearAllHistory: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.phase) {
        SearchPhase.Idle ->
            SearchHistorySection(
                history = history,
                onHistoryClick = onHistoryClick,
                onRemoveHistory = onRemoveHistory,
                onClearSearchHistory = onClearSearchHistory,
                onClearAllHistory = onClearAllHistory,
                modifier = modifier,
            )

        SearchPhase.Loading -> SearchSkeleton(modifier = modifier)

        SearchPhase.Error ->
            MessageState(
                title = stringResource(R.string.search_error_title),
                message = stringResource(R.string.search_error_message),
                modifier = modifier,
                action = {
                    Button(
                        onClick = onRetry,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                },
            )

        SearchPhase.Empty ->
            MessageState(
                title = stringResource(R.string.search_empty_title),
                message = stringResource(R.string.search_empty_message),
                modifier = modifier,
            )

        SearchPhase.Results -> {
            val shouldLoadMore by remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    lastVisible >= layoutInfo.totalItemsCount - 3
                }
            }

            LaunchedEffect(
                shouldLoadMore,
                state.videos.size,
                state.hasMore,
                state.isLoadingMore,
                state.loadMoreError,
            ) {
                if (shouldLoadMore && state.hasMore && !state.isLoadingMore && !state.loadMoreError) {
                    onLoadMore()
                }
            }

            VideoList(
                state = state,
                listState = listState,
                onVideoClick = onVideoClick,
                onLoadMoreRetry = onRetryLoadMore,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun SearchHistorySection(
    history: List<SearchHistoryEntry>,
    onHistoryClick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    onClearAllHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (history.isEmpty()) {
            item(key = "history_empty") {
                SearchHistoryEmpty()
            }
        } else {
            item(key = "history_header") {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.search_history_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onClearSearchHistory,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.height(40.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.search_history_clear),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            itemsIndexed(
                items = history,
                key = { _, entry -> entry.query },
            ) { index, entry ->
                SearchHistoryRow(
                    query = entry.query,
                    topCorner = if (index == 0) 28.dp else 4.dp,
                    bottomCorner = if (index == history.lastIndex) 28.dp else 4.dp,
                    onClick = { onHistoryClick(entry.query) },
                    onRemove = { onRemoveHistory(entry.query) },
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "history_clear_all") {
                TextButton(
                    onClick = onClearAllHistory,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(
                        text = stringResource(R.string.clear_history),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryEmpty() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = clusterCardColor(),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .background(clusterInnerColor(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search_rounded),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.search_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchHistoryRow(
    query: String,
    topCorner: Dp,
    bottomCorner: Dp,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = clusterCardColor()
    val innerColor = clusterInnerColor()
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
            label = "history-cluster-top-corner",
        )
    val animatedBottomCorner by
        animateDpAsState(
            targetValue = if (pressed) 28.dp else bottomCorner,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "history-cluster-bottom-corner",
        )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(
                topStart = animatedTopCorner,
                topEnd = animatedTopCorner,
                bottomStart = animatedBottomCorner,
                bottomEnd = animatedBottomCorner,
            ),
        color = cardColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .background(innerColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search_rounded),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = query,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_rounded),
                    contentDescription = stringResource(R.string.search_history_remove),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchHeader(
    input: String,
    isLoading: Boolean,
    onInputChange: (String) -> Unit,
    onFocused: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark()

        Spacer(Modifier.width(10.dp))

        val interactionSource = remember { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        LaunchedEffect(focused) {
            if (focused) onFocused()
        }
        val clusterCorner by
            animateDpAsState(
                targetValue = if (focused) 20.dp else 28.dp,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                label = "search-cluster-corner",
            )
        val clusterColor by
            animateColorAsState(
                targetValue =
                    if (focused) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                animationSpec = tween(160),
                label = "search-cluster-color",
            )
        val buttonCorner by
            animateDpAsState(
                targetValue = if (focused) 16.dp else 24.dp,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                label = "search-button-corner",
            )

        Surface(
            modifier =
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .pointerInput(onFocused) {
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            onFocused()
                        }
                    },
            shape = RoundedCornerShape(clusterCorner),
            color = clusterColor,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(56.dp),
                    placeholder = {
                        Text(stringResource(R.string.search_hint))
                    },
                    trailingIcon = {
                        if (input.isNotEmpty()) {
                            IconButton(onClick = onClear) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close_rounded),
                                    contentDescription = stringResource(R.string.search_clear),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    interactionSource = interactionSource,
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                )

                FilledIconButton(
                    onClick = onSearch,
                    enabled = input.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(buttonCorner),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search_rounded),
                        contentDescription = stringResource(R.string.search_action),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoList(
    state: SearchUiState,
    listState: LazyListState,
    onVideoClick: (VideoSummary) -> Unit,
    onLoadMoreRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(
            items = state.videos,
            key = { _, video -> video.url },
        ) { index, video ->
            VideoRow(
                video = video,
                onClick = { onVideoClick(video) },
                modifier = Modifier.animateItem(),
                topCorner = if (index == 0) 28.dp else 4.dp,
                bottomCorner = if (index == state.videos.lastIndex) 28.dp else 4.dp,
            )
        }

        if (state.isLoadingMore) {
            item(key = "loading_more") {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else if (state.loadMoreError) {
            item(key = "load_more_error") {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.load_more_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onLoadMoreRetry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "search-skeleton")
    val alpha by
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.8f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(900),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "search-skeleton-alpha",
        )

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(List(12) { it }) { index, _ ->
            VideoRowSkeleton(
                alpha = alpha,
                topCorner = if (index == 0) 28.dp else 4.dp,
                bottomCorner = if (index == 11) 28.dp else 4.dp,
            )
        }
    }
}

@Composable
private fun MessageState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
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

        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
