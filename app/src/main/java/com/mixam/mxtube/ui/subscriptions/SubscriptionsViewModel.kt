package com.mixam.mxtube.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mixam.mxtube.data.local.SubscriptionEntry
import com.mixam.mxtube.data.local.SubscriptionsFeedCache
import com.mixam.mxtube.data.local.SubscriptionsStore
import com.mixam.mxtube.data.subscriptions.SubscriptionFeedFailure
import com.mixam.mxtube.data.subscriptions.SubscriptionsFeedRepository
import com.mixam.mxtube.data.subscriptions.SubscriptionsFeedSession
import com.mixam.mxtube.data.subscriptions.SubscriptionsFeedSnapshot
import com.mixam.mxtube.data.subscriptions.boundSubscriptionsVideos
import com.mixam.mxtube.data.youtube.VideoSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubscriptionsUiState(
    val channels: List<SubscriptionEntry> = emptyList(),
    val selectedChannelKey: String? = null,
    val videos: List<VideoSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: Boolean = false,
    val failedChannelKeys: Set<String> = emptySet(),
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: Boolean = false,
) {
    val failedChannels: List<SubscriptionEntry>
        get() = channels.filter {
            it.key in failedChannelKeys && (selectedChannelKey == null || it.key == selectedChannelKey)
        }
}

class SubscriptionsViewModel(
    private val subscriptions: SubscriptionsStore,
    private val cache: SubscriptionsFeedCache,
    private val repository: SubscriptionsFeedRepository = SubscriptionsFeedRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    private var initialized = false
    private var channels = emptyList<SubscriptionEntry>()
    private var snapshot = SubscriptionsFeedSnapshot(emptyMap(), emptyMap(), emptySet(), emptySet())
    private var session: SubscriptionsFeedSession? = null
    private var networkJob: Job? = null
    private var generation = 0L

    init {
        viewModelScope.launch {
            subscriptions.snapshot()
            subscriptions.state.collect { state -> updateChannels(state.channels) }
        }
    }

    fun refresh() {
        if (initialized) startRefresh(hydrate = false)
    }

    fun selectChannel(key: String?) {
        if (key != null && channels.none { it.key == key }) return
        if (_uiState.value.selectedChannelKey == key) return
        _uiState.update { it.copy(selectedChannelKey = key) }
        publish()
        val live = session
        if (key != null &&
            live != null &&
            snapshot.videosByChannel[key].isNullOrEmpty() &&
            snapshot.failures[key] == null &&
            !_uiState.value.isRefreshing
        ) {
            runOperation(isLoadMore = true) { live.loadChannel(key) }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMore) return
        val live = session ?: return
        runOperation(isLoadMore = true) { live.loadMore(state.selectedChannelKey) }
    }

    fun retry() {
        if (_uiState.value.isRefreshing || _uiState.value.isLoadingMore) return
        val live = session
        if (live == null || snapshot.failures.isEmpty()) refresh()
        else runOperation(isLoadMore = false) { live.retryFailed() }
    }

    fun onVisible() {
        viewModelScope.launch { subscriptions.markFeedSeen() }
    }

    private fun updateChannels(next: List<SubscriptionEntry>) {
        val first = !initialized
        val oldIdentities = channels.associate { it.key to (it.url to it.addedAtEpochMillis) }
        val nextIdentities = next.associate { it.key to (it.url to it.addedAtEpochMillis) }
        val identityChanged = oldIdentities != nextIdentities
        val profileChanged = channels.map { Profile(it.key, it.url, it.name, it.avatarUrl) } !=
            next.map { Profile(it.key, it.url, it.name, it.avatarUrl) }
        if (!first && !identityChanged && !profileChanged) return
        initialized = true
        channels = next
        _uiState.update { state ->
            state.copy(
                channels = next,
                selectedChannelKey = state.selectedChannelKey?.takeIf { key -> next.any { it.key == key } },
            )
        }
        if (first || identityChanged) {
            val retained = next.filter { oldIdentities[it.key] == nextIdentities[it.key] }.map { it.key }.toSet()
            snapshot = snapshot.copy(
                videosByChannel = snapshot.videosByChannel.filterKeys { it in retained },
                failures = snapshot.failures.filterKeys { it in retained },
                successfulChannelKeys = snapshot.successfulChannelKeys.intersect(retained),
                moreChannelKeys = emptySet(),
            )
            session = null
            publish()
            startRefresh(hydrate = first)
        } else {
            publish()
        }
    }

    private fun startRefresh(hydrate: Boolean) {
        networkJob?.cancel()
        val token = ++generation
        val entries = channels.toList()
        session = null
        _uiState.update {
            it.copy(
                isLoading = entries.isNotEmpty() && it.videos.isEmpty(),
                isRefreshing = entries.isNotEmpty(),
                isLoadingMore = false,
                hasMore = false,
                error = false,
                failedChannelKeys = emptySet(),
                loadMoreError = false,
            )
        }
        networkJob = viewModelScope.launch {
            if (entries.isEmpty()) {
                snapshot = SubscriptionsFeedSnapshot(emptyMap(), emptyMap(), emptySet(), emptySet())
                publish()
                cache.write(emptyMap(), emptySet())
                return@launch
            }
            try {
                val keys = entries.map { it.key }.toSet()
                if (hydrate) {
                    val cached = cache.read(keys)
                    if (token != generation) return@launch
                    snapshot = snapshot.copy(videosByChannel = boundSubscriptionsVideos(cached))
                    publish()
                }
                val live = repository.open(entries, snapshot.videosByChannel)
                session = live
                val result = live.refresh()
                if (token != generation) return@launch
                if (result.successfulChannelKeys.isNotEmpty()) cache.write(result.videosByChannel, keys)
                if (token == generation) complete(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token == generation) fail(error, isLoadMore = false)
            }
        }
    }

    private fun runOperation(
        isLoadMore: Boolean,
        operation: suspend () -> SubscriptionsFeedSnapshot,
    ) {
        val token = generation
        _uiState.update {
            it.copy(
                isLoading = !isLoadMore && it.videos.isEmpty(),
                isRefreshing = !isLoadMore,
                isLoadingMore = isLoadMore,
                loadMoreError = false,
            )
        }
        networkJob = viewModelScope.launch {
            try {
                val result = operation()
                if (token != generation) return@launch
                if (result.successfulChannelKeys.isNotEmpty()) {
                    cache.write(result.videosByChannel, channels.map { it.key }.toSet())
                }
                if (token == generation) complete(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token == generation) fail(error, isLoadMore)
            }
        }
    }

    private fun complete(result: SubscriptionsFeedSnapshot) {
        snapshot = result
        _uiState.update { it.copy(isLoading = false, isRefreshing = false, isLoadingMore = false) }
        publish()
    }

    private fun fail(error: Exception, isLoadMore: Boolean) {
        val selected = _uiState.value.selectedChannelKey
        snapshot = snapshot.copy(failures = channels.filter { selected == null || it.key == selected }
            .associate { it.key to SubscriptionFeedFailure(error, isLoadMore) })
        _uiState.update { it.copy(isLoading = false, isRefreshing = false, isLoadingMore = false) }
        publish()
    }

    private fun publish() {
        _uiState.update { state ->
            val selected = state.selectedChannelKey
            val videos = snapshot.videos(selected)
            val failures = snapshot.failures.filterKeys { selected == null || it == selected }
            state.copy(
                videos = videos,
                error = videos.isEmpty() && failures.values.any { !it.isLoadMore },
                failedChannelKeys = snapshot.failures.filterValues { !it.isLoadMore }.keys.toSet(),
                hasMore = !state.isRefreshing && snapshot.hasMore(selected),
                loadMoreError = failures.values.any { it.isLoadMore },
            )
        }
    }

    private data class Profile(val key: String, val url: String, val name: String, val avatarUrl: String?)
}
