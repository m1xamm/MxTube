package com.m1xamm.mxtube.data.local

data class WatchHistoryEntry(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val watchedAtEpochMillis: Long,
    val progressMs: Long = 0L,
    val topics: Set<String> = emptySet(),
    val sourceQuery: String? = null,
)

data class SearchHistoryEntry(
    val query: String,
    val searchedAtEpochMillis: Long,
    val count: Int = 1,
)

data class HistoryState(
    val watches: List<WatchHistoryEntry> = emptyList(),
    val searches: List<SearchHistoryEntry> = emptyList(),
)
