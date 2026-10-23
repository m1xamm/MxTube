package com.m1xamm.mxtube.data.youtube

data class VideoSummary(
    val url: String,
    val title: String,
    val uploader: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadedAtEpochMillis: Long?,
    val thumbnailUrl: String?,
)

data class SearchBatch(
    val videos: List<VideoSummary>,
    val hasMore: Boolean,
)

data class PlaybackQuality(
    val label: String,
    val height: Int,
    val fps: Int,
    val videoUrl: String,
    val videoMimeType: String,
    val audioUrl: String?,
    val audioMimeType: String?,
)

data class PlaybackInfo(
    val title: String,
    val uploader: String,
    val viewCount: Long,
    val uploadedAtEpochMillis: Long?,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val qualities: List<PlaybackQuality>,
    val defaultQualityIndex: Int,
)
