package com.mixam.mxtube.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mixam.mxtube.data.local.BlockedChannelsStore
import com.mixam.mxtube.data.local.SubscriptionNotificationMode
import com.mixam.mxtube.data.local.SubscriptionsStore
import com.mixam.mxtube.data.local.UserPreferences
import com.mixam.mxtube.data.youtube.ChannelIdentity
import com.mixam.mxtube.data.youtube.ChannelRef
import com.mixam.mxtube.data.youtube.PlaybackInfo
import com.mixam.mxtube.data.youtube.VideoSummary
import com.mixam.mxtube.data.youtube.YouTubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
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
    val uploaderKey: String? = null,
    val isSubscribed: Boolean = false,
    val isSubscribePending: Boolean = false,
    val notificationMode: SubscriptionNotificationMode = SubscriptionNotificationMode.Personalized,
    val subscriptionError: Boolean = false,
) {
    internal fun withoutBlockedChannels(blockedKeys: Set<String>): PlayerUiState =
        copy(related = related.filterNot { ChannelIdentity.keyOf(it.uploaderUrl) in blockedKeys })
}

class PlayerViewModel(
    private val videoUrl: String,
    initialRelated: List<VideoSummary> = emptyList(),
    private val repository: YouTubeRepository = YouTubeRepository(),
    private val subscriptions: SubscriptionsStore,
    private val blockedChannels: BlockedChannelsStore,
    private val initialUploaderUrl: String? = null,
    private val initialUploaderName: String = "",
    private val resolveVideo: suspend (String) -> PlaybackInfo = repository::resolve,
    private val enrichVideo: suspend (String) -> PlaybackInfo? = { repository.resolveSlow(it) },
    private val fetchRelated: suspend (String) -> List<VideoSummary> = repository::related,
) : ViewModel() {
    private val relatedSeeds = ArrayDeque<String>()
    private val expandedSeeds = mutableSetOf(videoUrl)
    private val _uiState =
        MutableStateFlow(
            PlayerUiState(
                related = initialRelated.distinctBy { it.url },
            ),
        )
    val uiState: StateFlow<PlayerUiState> =
        combine(
            _uiState,
            blockedChannels.state.onStart { blockedChannels.snapshot() },
        ) { state, blocked ->
            state.withoutBlockedChannels(blocked.keys).copy(
                hasMoreRelated = canLoadMoreRelated(state.related, blocked.keys),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUiState())

    private var loadJob: Job? = null
    private var enrichJob: Job? = null
    private var relatedJob: Job? = null
    private val uploaderKey = MutableStateFlow<String?>(null)

    init {
        initialRelated.forEach { seed ->
            if (seed.url != videoUrl) relatedSeeds.addLast(seed.url)
        }
        uploaderKey.value = ChannelIdentity.keyOf(initialUploaderUrl)
        load()
        viewModelScope.launch {
            uiState.collect {
                while (true) {
                    val visible = _uiState.value.withoutBlockedChannels(blockedChannels.state.value.keys)
                    if (visible.related.isNotEmpty() || visible.isLoading || visible.info == null ||
                        visible.error || visible.isLoadingRelated || visible.relatedError ||
                        !canLoadMoreRelated(_uiState.value.related)
                    ) break
                    loadMoreRelated()
                    relatedJob?.join()
                }
            }
        }
        viewModelScope.launch {
            combine(subscriptions.state, uploaderKey) { state, key ->
                state.channels.firstOrNull { it.key == key }
            }.collect { entry ->
                _uiState.update {
                    it.copy(
                        isSubscribed = entry != null,
                        notificationMode = entry?.notificationMode ?: SubscriptionNotificationMode.Personalized,
                    )
                }
            }
        }
    }

    fun subscribe() {
        val info = _uiState.value.info
        val uploaderUrl = info?.uploaderUrl ?: initialUploaderUrl ?: return
        val uploaderName = info?.uploader ?: initialUploaderName
        val ref = ChannelIdentity.from(uploaderUrl) ?: return
        if (_uiState.value.isSubscribePending || _uiState.value.isSubscribed) return
        _uiState.update { it.copy(isSubscribePending = true, subscriptionError = false) }
        viewModelScope.launch {
            try {
                val canonical =
                    if (ChannelIdentity.isChannelId(ref.key)) {
                        ref
                    } else {
                        val header = repository.channel(ref.url)
                        ChannelRef(header.key, header.url)
                    }
                uploaderKey.value = canonical.key
                _uiState.update { it.copy(uploaderKey = canonical.key) }
                subscriptions.subscribe(canonical, uploaderName, info?.uploaderAvatarUrl)
                viewModelScope.launch {
                    try {
                        val header = repository.channel(canonical.url)
                        subscriptions.updateProfile(canonical.key, header.name, header.avatarUrl)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.e(LOG_TAG, "channel profile failed: ${canonical.url}", error)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(LOG_TAG, "subscribe failed: ${ref.url}", error)
                _uiState.update { it.copy(subscriptionError = true) }
            } finally {
                _uiState.update { it.copy(isSubscribePending = false) }
            }
        }
    }

    fun unsubscribe() {
        val key = uploaderKey.value ?: return
        viewModelScope.launch { subscriptions.unsubscribe(key) }
    }

    fun setNotificationMode(mode: SubscriptionNotificationMode) {
        val key = uploaderKey.value ?: return
        viewModelScope.launch { subscriptions.setNotificationMode(key, mode) }
    }

    fun load() {
        loadJob?.cancel()
        relatedJob?.cancel()
        enrichJob?.cancel()
        val current = _uiState.value
        _uiState.value =
            PlayerUiState(
                related = current.related,
                hasMoreRelated = current.hasMoreRelated,
                uploaderKey = current.uploaderKey,
                isSubscribed = current.isSubscribed,
                notificationMode = current.notificationMode,
            )
        loadJob =
            viewModelScope.launch {
                try {
                    blockedChannels.snapshot()
                    val info = resolveVideo(videoUrl)
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
                    uploaderKey.value = ChannelIdentity.keyOf(info.uploaderUrl)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            info = info,
                            uploaderKey = uploaderKey.value,
                            selectedQualityIndex = qualityIndex,
                            selectedAudioTrackIndex = audioIndex,
                            selectedSubtitleIndex = subtitleIndex,
                            related = related,
                            isLoadingRelated = false,
                            hasMoreRelated = canLoadMoreRelated(related),
                            relatedError = false,
                        )
                    }
                    if (info.uploaderAvatarUrl == null ||
                        info.related.isEmpty() ||
                        info.uploadedAtEpochMillis == null
                    ) {
                        enrich()
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
        if (_uiState.value.relatedError) return
        loadRelated()
    }

    private fun enrich() {
        enrichJob?.cancel()
        enrichJob =
            viewModelScope.launch {
                delay(1400)
                val full = runCatching { enrichVideo(videoUrl) }.getOrNull() ?: return@launch
                val merged = mergeRelated(full.related, _uiState.value.related)
                full.related.forEach { video ->
                    if (video.url != videoUrl &&
                        video.url !in expandedSeeds &&
                        !relatedSeeds.contains(video.url)
                    ) {
                        relatedSeeds.addLast(video.url)
                    }
                }
                _uiState.update { state ->
                    val info = state.info ?: return@update state
                    state.copy(
                        info =
                            info.copy(
                                uploaderAvatarUrl =
                                    info.uploaderAvatarUrl ?: full.uploaderAvatarUrl,
                                uploadedAtEpochMillis =
                                    info.uploadedAtEpochMillis ?: full.uploadedAtEpochMillis,
                                viewCount = info.viewCount.takeIf { it > 0 } ?: full.viewCount,
                                subtitles = info.subtitles.ifEmpty { full.subtitles },
                                uploaderUrl = info.uploaderUrl ?: full.uploaderUrl,
                            ),
                        related = merged,
                        hasMoreRelated = canLoadMoreRelated(merged),
                    )
                }
            }
    }    fun retryRelated() {
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
        val state = _uiState.value
        if (state.isLoading || state.info == null || state.error || state.isLoadingRelated ||
            !canLoadMoreRelated(state.related)
        ) return
        _uiState.update { it.copy(isLoadingRelated = true, relatedError = false) }
        relatedJob =
            viewModelScope.launch {
                try {
                    val seed = nextSeed()
                    if (seed == null) {
                        _uiState.update {
                            it.copy(isLoadingRelated = false, hasMoreRelated = false)
                        }
                        return@launch
                    }
                    val fetched = fetchRelated(seed)
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
                            hasMoreRelated = canLoadMoreRelated(merged),
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

    private fun canLoadMoreRelated(
        related: List<VideoSummary>,
        blockedKeys: Set<String> = blockedChannels.state.value.keys,
    ): Boolean =
        related.count { ChannelIdentity.keyOf(it.uploaderUrl) !in blockedKeys } < MAX_RELATED &&
            relatedSeeds.any { it != videoUrl && it !in expandedSeeds }

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
