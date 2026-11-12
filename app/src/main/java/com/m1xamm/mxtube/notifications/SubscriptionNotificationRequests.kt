package com.m1xamm.mxtube.notifications

import com.m1xamm.mxtube.data.local.SubscriptionEntry
import com.m1xamm.mxtube.data.local.SubscriptionNotificationMode
import kotlinx.coroutines.sync.Semaphore

internal class SubscriptionNotificationRequests {
    private data class Fingerprint(
        val url: String,
        val addedAt: Long,
        val enabledAt: Long,
        val mode: SubscriptionNotificationMode,
    )

    private val fingerprints = mutableMapOf<String, Fingerprint>()
    private val generations = mutableMapOf<String, Long>()
    private var sequence = 0L

    fun update(channels: List<SubscriptionEntry>): Boolean {
        val next = channels.associate { it.key to fingerprint(it) }
        var newlyEnabled = false
        (fingerprints.keys + next.keys).forEach { key ->
            val previous = fingerprints[key]
            val current = next[key]
            if (previous != current) {
                if (current == null) {
                    generations.remove(key)
                } else {
                    generations[key] = ++sequence
                    if (current.mode != SubscriptionNotificationMode.None) newlyEnabled = true
                }
            }
        }
        fingerprints.clear()
        fingerprints.putAll(next)
        return newlyEnabled
    }

    fun capture(channel: SubscriptionEntry): Long? =
        generations[channel.key]?.takeIf { fingerprints[channel.key] == fingerprint(channel) }

    fun isCurrent(channel: SubscriptionEntry, generation: Long, current: SubscriptionEntry?): Boolean =
        generations[channel.key] == generation &&
            SubscriptionNotificationPolicy.sameSubscription(channel, current)

    private fun fingerprint(channel: SubscriptionEntry) = Fingerprint(
        channel.url, channel.addedAtEpochMillis, channel.notificationsEnabledAtEpochMillis, channel.notificationMode,
    )
}

internal object SubscriptionNotificationRuntime {
    val guard = Any()
    val requests = SubscriptionNotificationRequests()
    val feedSemaphore = Semaphore(4)
}
