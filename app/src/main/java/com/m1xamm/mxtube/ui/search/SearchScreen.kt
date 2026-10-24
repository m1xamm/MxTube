package com.m1xamm.mxtube.ui.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m1xamm.mxtube.R
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.ui.common.BrandMark

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    listState: LazyListState,
    onVideoClick: (VideoSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    val submitSearch: (String) -> Unit = { query ->
        keyboard?.hide()
        viewModel.search(query)
    }

    LaunchedEffect(state.phase) {
        if (state.phase == SearchPhase.Loading) {
            runCatching { listState.scrollToItem(0) }
        }
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
            onClear = {
                keyboard?.hide()
                viewModel.clear()
            },
            onSearch = { submitSearch(state.input) },
        )

        if (state.phase == SearchPhase.Loading) {
            LinearProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        } else {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }

        when (state.phase) {
            SearchPhase.Idle -> Spacer(Modifier.weight(1f))

            SearchPhase.Loading -> Spacer(Modifier.weight(1f))

            SearchPhase.Error ->
                MessageState(
                    title = stringResource(R.string.search_error_title),
                    message = stringResource(R.string.search_error_message),
                    modifier = Modifier.weight(1f),
                    action = {
                        Button(
                            onClick = viewModel::retry,
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
                    modifier = Modifier.weight(1f),
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
                        viewModel.loadMore()
                    }
                }

                VideoList(
                    state = state,
                    listState = listState,
                    onVideoClick = { video ->
                        keyboard?.hide()
                        onVideoClick(video)
                    },
                    onLoadMoreRetry = viewModel::retryLoadMore,
                    modifier = Modifier.weight(1f),
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

        Surface(
            modifier =
                Modifier
                    .weight(1f)
                    .height(56.dp),
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
                                    imageVector = Icons.Filled.Close,
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
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = state.videos,
            key = { it.url },
        ) { video ->
            VideoRow(
                video = video,
                onClick = { onVideoClick(video) },
                modifier = Modifier.animateItem(),
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
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
