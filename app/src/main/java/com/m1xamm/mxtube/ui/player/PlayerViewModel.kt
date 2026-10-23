package com.m1xamm.mxtube.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m1xamm.mxtube.data.youtube.PlaybackInfo
import com.m1xamm.mxtube.data.youtube.YouTubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val info: PlaybackInfo? = null,
    val selectedQualityIndex: Int = 0,
)

class PlayerViewModel(
    private val videoUrl: String,
    private val repository: YouTubeRepository = YouTubeRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _uiState.value = PlayerUiState()
        loadJob =
            viewModelScope.launch {
                try {
                    val info = repository.resolve(videoUrl)
                    _uiState.value =
                        PlayerUiState(
                            isLoading = false,
                            info = info,
                            selectedQualityIndex = info.defaultQualityIndex,
                        )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _uiState.value = PlayerUiState(isLoading = false, error = true)
                }
            }
    }
}
