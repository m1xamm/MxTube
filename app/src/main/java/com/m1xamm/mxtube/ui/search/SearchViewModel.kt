package com.m1xamm.mxtube.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.data.youtube.YouTubeRepository
import com.m1xamm.mxtube.data.youtube.YouTubeSearchSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchPhase {
    Idle,
    Loading,
    Results,
    Empty,
    Error,
}

data class SearchUiState(
    val input: String = "",
    val query: String = "",
    val phase: SearchPhase = SearchPhase.Idle,
    val videos: List<VideoSummary> = emptyList(),
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: Boolean = false,
)

class SearchViewModel(
    private val repository: YouTubeRepository = YouTubeRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var session: YouTubeSearchSession? = null
    private var lastQuery: String = ""
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            clear()
            return
        }
        if (trimmed == lastQuery && _uiState.value.phase == SearchPhase.Loading) return

        lastQuery = trimmed
        session = null
        searchJob?.cancel()
        loadMoreJob?.cancel()
        _uiState.value =
            SearchUiState(
                input = query,
                query = trimmed,
                phase = SearchPhase.Loading,
            )

        searchJob =
            viewModelScope.launch {
                try {
                    val newSession = repository.search(trimmed)
                    val batch = newSession.first()
                    if (lastQuery != trimmed) return@launch

                    session = newSession
                    _uiState.update {
                        it.copy(
                            query = trimmed,
                            phase = if (batch.videos.isEmpty()) SearchPhase.Empty else SearchPhase.Results,
                            videos = batch.videos,
                            hasMore = batch.hasMore,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Exception) {
                    Log.e(TAG, "search failed", throwable)
                    if (lastQuery == trimmed) {
                        _uiState.update { it.copy(phase = SearchPhase.Error) }
                    }
                }
            }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun retry() {
        if (lastQuery.isNotBlank()) search(lastQuery)
    }

    fun clear() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        session = null
        lastQuery = ""
        _uiState.value = SearchUiState()
    }

    fun loadMore() {
        val currentSession = session ?: return
        val state = _uiState.value
        if (state.phase != SearchPhase.Results || !state.hasMore || state.isLoadingMore) return

        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = false) }
        loadMoreJob =
            viewModelScope.launch {
                try {
                    val batch = currentSession.loadMore()
                    if (session !== currentSession) return@launch

                    _uiState.update {
                        it.copy(
                            videos = (it.videos + batch.videos).distinctBy { video -> video.url },
                            hasMore = batch.hasMore,
                            isLoadingMore = false,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (session === currentSession) {
                        _uiState.update { it.copy(isLoadingMore = false, loadMoreError = true) }
                    }
                }
            }
    }

    fun retryLoadMore() {
        _uiState.update { it.copy(loadMoreError = false) }
        loadMore()
    }

    private companion object {
        const val TAG = "MxTube"
    }
}
