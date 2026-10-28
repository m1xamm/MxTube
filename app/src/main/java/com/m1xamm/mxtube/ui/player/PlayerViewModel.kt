package com.m1xamm.mxtube.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m1xamm.mxtube.data.youtube.PlaybackInfo
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.data.youtube.YouTubeRepository
import com.m1xamm.mxtube.data.local.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val info: PlaybackInfo? = null,
    val selectedQualityIndex: Int = 0,
    val selectedAudioTrackIndex: Int = -1,
    val selectedSubtitleIndex: Int = -1,
    val related: List<VideoSummary> = emptyList(),
    val isLoadingRelated: Boolean = false,
    val hasMoreRelated: Boolean = true,
    val relatedError: Boolean = false,
)

class PlayerViewModel(
    private val videoUrl: String,
    initialRelated: List<VideoSummary> = emptyList(),
    private val repository: YouTubeRepository = YouTubeRepository(),
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            PlayerUiState(
                related = initialRelated.distinctBy { it.url },
            ),
        )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var relatedJob: Job? = null
    private val relatedSeeds = ArrayDeque<String>()
    private val expandedSeeds = mutableSetOf(videoUrl)

    init {
        initialRelated.forEach { seed ->
            if (seed.url != videoUrl) relatedSeeds.addLast(seed.url)
        }
        load()
    }

    fun load() {
        loadJob?.cancel()
        val current = _uiState.value
        _uiState.value =
            PlayerUiState(
                related = current.related,
                hasMoreRelated = current.hasMoreRelated,
            )
        loadJob =
            viewModelScope.launch {
                try {
                    val info = repository.resolve(videoUrl)
                    info.related.forEach { video ->
                        if (video.url != videoUrl &&
                            video.url !in expandedSeeds &&
                            !relatedSeeds.contains(video.url)
                        ) {
                            relatedSeeds.addLast(video.url)
                        }
                    }
                    val related =
                        mergeRelated(info.related, _uiState.value.related)
                    val savedQuality = UserPreferences.qualityLabel
                    val qualityIndex =
                        savedQuality
                            ?.let { label -> info.qualities.indexOfFirst { it.label == label } }
                            ?.takeIf { it >= 0 }
                            ?: info.defaultQualityIndex
                    val savedAudio = UserPreferences.audioLabel
                    val audioIndex =
                        savedAudio
                            ?.let { label -> info.audioTracks.indexOfFirst { it.label == label } }
                            ?.takeIf { it >= 0 }
                            ?: info.defaultAudioTrackIndex
                    val savedSubtitle = UserPreferences.subtitleLabel
                    val subtitleIndex =
                        savedSubtitle
                            ?.let { label -> info.subtitles.indexOfFirst { it.label == label } }
                            ?.takeIf { it >= 0 }
                            ?: -1
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            info = info,
                            selectedQualityIndex = qualityIndex,
                            selectedAudioTrackIndex = audioIndex,
                            selectedSubtitleIndex = subtitleIndex,
                            related = related,
                            isLoadingRelated = false,
                            hasMoreRelated =
                                related.size < MAX_RELATED && relatedSeeds.isNotEmpty(),
                            relatedError = false,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(LOG_TAG, "resolve failed: $videoUrl", error)
                    _uiState.update { it.copy(isLoading = false, error = true) }
                }
            }
    }

    fun loadMoreRelated() {
        val state = _uiState.value
        if (state.isLoadingRelated || !state.hasMoreRelated || state.related.size >= MAX_RELATED) {
            return
        }
        loadRelated()
    }

    fun retryRelated() {
        val state = _uiState.value
        if (state.info == null || state.error) {
            load()
        } else {
            loadRelated()
        }
    }

    fun selectQuality(index: Int) {
        val info = _uiState.value.info ?: return
        if (index !in info.qualities.indices) return
        UserPreferences.qualityLabel = info.qualities[index].label
        _uiState.update { it.copy(selectedQualityIndex = index) }
    }

    fun selectAudioTrack(index: Int) {
        val info = _uiState.value.info ?: return
        if (index !in info.audioTracks.indices) return
        UserPreferences.audioLabel = info.audioTracks[index].label
        _uiState.update { it.copy(selectedAudioTrackIndex = index) }
    }

    fun selectSubtitle(index: Int) {
        val info = _uiState.value.info ?: return
        if (index != -1 && index !in info.subtitles.indices) return
        UserPreferences.subtitleLabel = info.subtitles.getOrNull(index)?.label
        _uiState.update { it.copy(selectedSubtitleIndex = index) }
    }

    private fun loadRelated() {
        relatedJob?.cancel()
        relatedJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingRelated = true, relatedError = false) }
                try {
                    val seed = nextSeed()
                    if (seed == null) {
                        _uiState.update {
                            it.copy(isLoadingRelated = false, hasMoreRelated = false)
                        }
                        return@launch
                    }
                    val fetched = repository.related(seed)
                    val current = _uiState.value.related
                    val merged = mergeRelated(current, fetched)
                    fetched.forEach { video ->
                        if (video.url != videoUrl &&
                            video.url !in expandedSeeds &&
                            !relatedSeeds.contains(video.url)
                        ) {
                            relatedSeeds.addLast(video.url)
                        }
                    }
                    _uiState.update {
                        it.copy(
                            related = merged,
                            isLoadingRelated = false,
                            hasMoreRelated =
                                merged.size < MAX_RELATED && relatedSeeds.isNotEmpty(),
                            relatedError = false,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _uiState.update { it.copy(isLoadingRelated = false, relatedError = true) }
                }
            }
    }

    private fun nextSeed(): String? {
        while (relatedSeeds.isNotEmpty()) {
            val seed = relatedSeeds.removeFirst()
            if (seed != videoUrl && expandedSeeds.add(seed)) return seed
        }
        return null
    }

    private fun mergeRelated(
        primary: List<VideoSummary>,
        secondary: List<VideoSummary>,
    ): List<VideoSummary> {
        val seen = HashSet<String>()
        return buildList {
            primary.forEach { video ->
                if (seen.add(video.url)) add(video)
            }
            secondary.forEach { video ->
                if (seen.add(video.url)) add(video)
            }
        }
    }

    private companion object {
        const val MAX_RELATED = 240
        const val LOG_TAG = "MxTubePlayer"
    }
}
