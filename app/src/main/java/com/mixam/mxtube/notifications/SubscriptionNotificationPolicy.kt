package com.mixam.mxtube.notifications

import com.mixam.mxtube.data.local.SubscriptionEntry
import com.mixam.mxtube.data.local.SubscriptionNotificationMode
import com.mixam.mxtube.data.local.WatchHistoryEntry
import com.mixam.mxtube.data.youtube.ChannelIdentity
import com.mixam.mxtube.data.youtube.VideoSummary
import java.net.URI

internal object UploadVideoIdentity {
    private val watchUrl = Regex("^https://www\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})$")
    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")

    fun fromUrl(url: String?): String? = url?.let { watchUrl.matchEntire(it)?.groupValues?.get(1) }

    fun watchedId(url: String): String? {
        fromUrl(url)?.let { return it }
        return runCatching {
            val uri = URI(url)
            if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1) return null
            val id = when (uri.host) {
                "youtu.be" -> uri.path.removePrefix("/")
                "www.youtube.com", "youtube.com", "m.youtube.com" -> when {
                    uri.path == "/watch" -> uri.rawQuery.orEmpty().split('&')
                        .singleOrNull { it.startsWith("v=") }?.removePrefix("v=")
                    uri.path.startsWith("/shorts/") -> uri.path.removePrefix("/shorts/")
                    else -> null
                }
                else -> null
            }
            id?.takeIf { videoId.matches(it) }
        }.getOrNull()
    }

    fun url(id: String): String = "https://www.youtube.com/watch?v=$id"
}

data class ChannelUploadNotificationState(
    val addedAtEpochMillis: Long,
    val channelUrl: String,
    val seeded: Boolean = false,
    val seenVideoIds: List<String> = emptyList(),
    val lastPersonalizedAtEpochMillis: Long = 0L,
    val lastCheckedAtEpochMillis: Long = 0L,
    val resolvedChannelUrl: String? = null,
    val retryPending: Boolean = false,
)

data class SubscriptionNotificationPlan(
    val accepted: Boolean,
    val nextState: ChannelUploadNotificationState?,
    val videos: List<VideoSummary> = emptyList(),
)

object SubscriptionNotificationPolicy {
    const val MAX_SEEN_IDS = 128
    const val DAY_MILLIS = 86_400_000L
    const val HISTORY_WINDOW_MILLIS = 30 * DAY_MILLIS

    fun sameSubscription(requested: SubscriptionEntry, current: SubscriptionEntry?): Boolean =
        current != null && current.key == requested.key && current.url == requested.url &&
            current.addedAtEpochMillis == requested.addedAtEpochMillis &&
            current.notificationsEnabledAtEpochMillis == requested.notificationsEnabledAtEpochMillis &&
            current.notificationMode == requested.notificationMode &&
            current.notificationMode != SubscriptionNotificationMode.None

    fun plan(
        requested: SubscriptionEntry,
        current: SubscriptionEntry?,
        previous: ChannelUploadNotificationState?,
        uploads: List<VideoSummary>,
        history: List<WatchHistoryEntry>,
        now: Long,
        deliveryAllowed: Boolean,
        resolvedChannelUrl: String? = null,
    ): SubscriptionNotificationPlan {
        if (!sameSubscription(requested, current)) {
            return SubscriptionNotificationPlan(false, previous)
        }
        val channel = current ?: return SubscriptionNotificationPlan(false, previous)
        val state = previous?.takeIf {
            it.addedAtEpochMillis == channel.addedAtEpochMillis && it.channelUrl == channel.url
        } ?: ChannelUploadNotificationState(channel.addedAtEpochMillis, channel.url)
        val feed = uploads.filter { UploadVideoIdentity.fromUrl(it.url) != null }.distinctBy { it.url }
        val ids = feed.filter { video ->
            val published = video.uploadedAtEpochMillis
            published == null || published <= now
        }.mapNotNull { UploadVideoIdentity.fromUrl(it.url) }
        val next = state.copy(
            seeded = true,
            seenVideoIds = (ids + state.seenVideoIds).distinct().take(MAX_SEEN_IDS),
            lastCheckedAtEpochMillis = now,
            resolvedChannelUrl = resolvedChannelUrl ?: state.resolvedChannelUrl,
            retryPending = false,
        )
        if (!state.seeded || !deliveryAllowed) return SubscriptionNotificationPlan(true, next)

        val cutoff = maxOf(channel.addedAtEpochMillis, channel.notificationsEnabledAtEpochMillis)
        val seen = state.seenVideoIds.toHashSet()
        val eligible = feed.filter { video ->
            val published = video.uploadedAtEpochMillis
            UploadVideoIdentity.fromUrl(video.url) !in seen && published != null &&
                published >= cutoff && published <= now
        }.sortedWith(compareByDescending<VideoSummary> { it.uploadedAtEpochMillis }.thenBy { it.url })
        if (channel.notificationMode == SubscriptionNotificationMode.All) {
            return SubscriptionNotificationPlan(true, next, eligible)
        }
        if (state.lastPersonalizedAtEpochMillis > 0L &&
            (now < state.lastPersonalizedAtEpochMillis || now - state.lastPersonalizedAtEpochMillis < DAY_MILLIS)
        ) return SubscriptionNotificationPlan(true, next)

        val channelKeys = listOfNotNull(
            ChannelIdentity.keyOf(channel.url),
            ChannelIdentity.keyOf(resolvedChannelUrl ?: state.resolvedChannelUrl),
        ).toSet()
        val interested = history.any { watch ->
            watch.watchedAtEpochMillis in (now - HISTORY_WINDOW_MILLIS)..now &&
                if (!watch.uploaderUrl.isNullOrBlank()) {
                    ChannelIdentity.keyOf(watch.uploaderUrl)?.let { it in channelKeys } == true
                } else {
                    channel.name.isNotBlank() && watch.uploader == channel.name
                }
        }
        if (!interested) return SubscriptionNotificationPlan(true, next)
        val watchedIds = history.mapNotNull { UploadVideoIdentity.watchedId(it.url) }.toHashSet()
        val latest = eligible.firstOrNull { UploadVideoIdentity.fromUrl(it.url) !in watchedIds }
        return SubscriptionNotificationPlan(true, next, listOfNotNull(latest))
    }

    fun acknowledge(
        plan: SubscriptionNotificationPlan,
        mode: SubscriptionNotificationMode,
        deliveredVideoIds: Set<String>,
        now: Long,
    ): ChannelUploadNotificationState? {
        val next = plan.nextState ?: return null
        return if (plan.accepted && mode == SubscriptionNotificationMode.Personalized &&
            plan.videos.any { UploadVideoIdentity.fromUrl(it.url) in deliveredVideoIds }
        ) next.copy(lastPersonalizedAtEpochMillis = now) else next
    }
}
