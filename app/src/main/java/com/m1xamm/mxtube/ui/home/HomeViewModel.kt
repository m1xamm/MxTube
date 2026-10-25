package com.m1xamm.mxtube.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m1xamm.mxtube.data.home.HomeFeedItem
import com.m1xamm.mxtube.data.home.HomeFeedRepository
import com.m1xamm.mxtube.data.home.HomeFeedSession
import com.m1xamm.mxtube.data.home.HomeTopic
import com.m1xamm.mxtube.data.home.HomeTopicKind
import com.m1xamm.mxtube.data.home.HomeTopics
import com.m1xamm.mxtube.data.local.HistoryState
import com.m1xamm.mxtube.data.local.HomeFeedCache
import com.m1xamm.mxtube.data.local.LocalHistoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class HomeUiState(
    val topics: List<HomeTopic> = HomeTopics.initialTopics(),
    val selectedTopicId: String = HomeTopics.all.id,
    val items: List<HomeFeedItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: Boolean = false,
) {
    val selectedTopic: HomeTopic
        get() = topics.firstOrNull { it.id == selectedTopicId } ?: HomeTopics.all
}

class HomeViewModel(
    private val historyStore: LocalHistoryStore,
    private val feedCache: HomeFeedCache,
    private val repository: HomeFeedRepository = HomeFeedRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val cache = mutableMapOf<String, CachedFeed>()
    private var feedJob: Job? = null
    private var loadMoreJob: Job? = null
    private var latestHistory = HistoryState()
    private var personalizedStale = false
    private var lastTopWatchUrl: String? = null

    init {
        viewModelScope.launch {
            val cachedItems = feedCache.read()
            if (cachedItems.isNotEmpty()) {
                _uiState.update { it.copy(items = cachedItems, isLoading = false) }
            }

            var initial = historyStore.snapshot()
            latestHistory = initial
            lastTopWatchUrl = initial.watches.firstOrNull()?.url
            val topics = HomeTopics.ordered(initial)
            val selected = HomeTopics.all.id
            _uiState.update {
                it.copy(
                    topics = topics,
                    selectedTopicId = selected,
                )
            }
            loadTopic(HomeTopics.all, initial, force = true)

            historyStore.state.collect { history ->
                if (history == initial) return@collect
                val previousTop = lastTopWatchUrl
                latestHistory = history
                val nextTopics = HomeTopics.ordered(history)
                val currentSelected = _uiState.value.selectedTopicId
                val selectedStillExists = nextTopics.any { it.id == currentSelected }
                val nextSelected = if (selectedStillExists) currentSelected else HomeTopics.all.id
                val topWatch = history.watches.firstOrNull()?.url
                if (topWatch != previousTop) {
                    personalizedStale = true
                    cache.remove(HomeTopics.all.id)
                }
                lastTopWatchUrl = topWatch
                _uiState.update {
                    it.copy(
                        topics = nextTopics,
                        selectedTopicId = nextSelected,
                    )
                }
                if (nextSelected != currentSelected) {
                    loadTopic(
                        nextTopics.firstOrNull { it.id == nextSelected } ?: HomeTopics.all,
                        history,
                        force = true,
                    )
                }
                initial = history
            }
        }
    }

    fun selectTopic(id: String) {
        val state = _uiState.value
        if (state.selectedTopicId == id) return
        val topic = state.topics.firstOrNull { it.id == id } ?: return
        val cached = cache[id]
        val stalePersonalized =
            topic.kind == HomeTopicKind.Personalized && personalizedStale
        if (cached != null && !stalePersonalized && !isStale(topic, cached)) {
            feedJob?.cancel()
            loadMoreJob?.cancel()
            _uiState.update {
                it.copy(
                    selectedTopicId = id,
                    items = cached.items,
                    isLoading = false,
                    error = false,
                    hasMore = cached.session.hasMore,
                    isLoadingMore = false,
                    loadMoreError = false,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                selectedTopicId = id,
                items = emptyList(),
                isLoading = true,
                error = false,
                hasMore = false,
                isLoadingMore = false,
                loadMoreError = false,
            )
        }
        loadTopic(topic, latestHistory, force = true)
    }

    fun refresh() {
        val topic = _uiState.value.selectedTopic
        cache.remove(topic.id)
        _uiState.update { it.copy(isLoading = it.items.isEmpty(), error = false) }
        loadTopic(topic, latestHistory, force = true)
    }

    fun retry() {
        val topic = _uiState.value.selectedTopic
        _uiState.update { it.copy(error = false, isLoading = true) }
        loadTopic(topic, latestHistory, force = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.error || !state.hasMore) return
        val topicId = state.selectedTopicId
        val cached = cache[topicId] ?: return

        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = false) }
        loadMoreJob =
            viewModelScope.launch {
                try {
                    val more = cached.session.loadMore()
                    if (_uiState.value.selectedTopicId != topicId) return@launch
                    val existing = _uiState.value.items
                    val merged =
                        (existing + more)
                            .distinctBy { item -> item.video.url }
                    cache[topicId] =
                        cached.copy(
                            items = merged,
                            loadedAt = System.currentTimeMillis(),
                        )
                    _uiState.update {
                        it.copy(
                            items = merged,
                            hasMore = cached.session.hasMore,
                            isLoadingMore = false,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (_uiState.value.selectedTopicId == topicId) {
                        _uiState.update { it.copy(isLoadingMore = false, loadMoreError = true) }
                    }
                }
            }
    }

    fun retryLoadMore() {
        _uiState.update { it.copy(loadMoreError = false) }
        loadMore()
    }

    fun onHomeVisible() {
        val state = _uiState.value
        val topic = state.selectedTopic
        val cached = cache[topic.id]
        if (topic.kind == HomeTopicKind.Personalized &&
            (personalizedStale || cached == null || isStale(topic, cached))
        ) {
            personalizedStale = false
            loadTopic(topic, latestHistory, force = true)
        }
    }

    fun onWatchRecorded() {
        personalizedStale = true
        cache.remove(HomeTopics.all.id)
        if (_uiState.value.selectedTopic.kind == HomeTopicKind.Personalized) {
            personalizedStale = false
            loadTopic(HomeTopics.all, latestHistory, force = true)
        }
    }

    fun onHistoryCleared() {
        viewModelScope.launch {
            feedCache.clear()
            cache.remove(HomeTopics.all.id)
            val history = historyStore.snapshot()
            latestHistory = history
            val topics = HomeTopics.ordered(history)
            personalizedStale = false
            _uiState.update {
                it.copy(
                    topics = topics,
                    selectedTopicId = HomeTopics.all.id,
                    items = emptyList(),
                    isLoading = true,
                    error = false,
                    hasMore = false,
                    isLoadingMore = false,
                    loadMoreError = false,
                )
            }
            loadTopic(HomeTopics.all, history, force = true)
        }
    }

    private fun loadTopic(
        topic: HomeTopic,
        history: HistoryState,
        force: Boolean,
    ) {
        feedJob?.cancel()
        loadMoreJob?.cancel()
        feedJob =
            viewModelScope.launch {
                val cached = cache[topic.id]
                if (!force && cached != null && !isStale(topic, cached)) {
                    _uiState.update {
                        it.copy(
                            selectedTopicId = topic.id,
                            items = cached.items,
                            isLoading = false,
                            hasMore = cached.session.hasMore,
                            error = false,
                        )
                    }
                    return@launch
                }

                try {
                    val effectiveHistory =
                        if (topic.kind == HomeTopicKind.Personalized) {
                            historyStore.snapshot().also { latestHistory = it }
                        } else {
                            history
                        }
                    val session =
                        withTimeoutOrNull(FEED_TIMEOUT_MS) {
                            repository.open(topic, effectiveHistory)
                        } ?: throw IllegalStateException("feed timeout")
                    if (_uiState.value.selectedTopicId != topic.id) return@launch
                    if (session.initialItems.isEmpty()) {
                        throw IllegalStateException("feed empty")
                    }
                    cache[topic.id] =
                        CachedFeed(
                            session = session,
                            items = session.initialItems,
                            loadedAt = System.currentTimeMillis(),
                        )
                    _uiState.update {
                        it.copy(
                            selectedTopicId = topic.id,
                            items = session.initialItems,
                            isLoading = false,
                            error = false,
                            hasMore = session.hasMore,
                            isLoadingMore = false,
                            loadMoreError = false,
                        )
                    }
                    if (topic.kind == HomeTopicKind.Personalized) {
                        personalizedStale = false
                        viewModelScope.launch { feedCache.write(session.initialItems) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (_uiState.value.selectedTopicId == topic.id) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = it.items.isEmpty(),
                            )
                        }
                    }
                }
            }
    }

    private fun isStale(
        topic: HomeTopic,
        cached: CachedFeed,
    ): Boolean {
        val ttl =
            if (topic.kind == HomeTopicKind.Personalized) {
                PERSONALIZED_TTL_MS
            } else {
                TOPIC_TTL_MS
            }
        return System.currentTimeMillis() - cached.loadedAt > ttl
    }

    private data class CachedFeed(
        val session: HomeFeedSession,
        val items: List<HomeFeedItem>,
        val loadedAt: Long,
    )

    private companion object {
        const val FEED_TIMEOUT_MS = 35_000L
        const val PERSONALIZED_TTL_MS = 2 * 60_000L
        const val TOPIC_TTL_MS = 15 * 60_000L
    }
}
