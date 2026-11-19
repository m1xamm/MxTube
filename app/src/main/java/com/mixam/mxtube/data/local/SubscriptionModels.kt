package com.mixam.mxtube.data.local

enum class SubscriptionNotificationMode(val storageValue: String) {
    All("all"),
    Personalized("personalized"),
    None("none"),
}

data class SubscriptionEntry(
    val key: String,
    val url: String,
    val name: String,
    val avatarUrl: String?,
    val addedAtEpochMillis: Long,
    val notificationMode: SubscriptionNotificationMode = SubscriptionNotificationMode.Personalized,
    val notificationsEnabledAtEpochMillis: Long = addedAtEpochMillis,
)

data class SubscriptionsState(
    val channels: List<SubscriptionEntry> = emptyList(),
    val lastSeenFeedAtEpochMillis: Long = 0L,
)
