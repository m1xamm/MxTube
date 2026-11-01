package com.m1xamm.mxtube.data.local

data class SubscriptionEntry(
    val key: String,
    val url: String,
    val name: String,
    val avatarUrl: String?,
    val addedAtEpochMillis: Long,
)

data class SubscriptionsState(
    val channels: List<SubscriptionEntry> = emptyList(),
    val lastSeenFeedAtEpochMillis: Long = 0L,
)
