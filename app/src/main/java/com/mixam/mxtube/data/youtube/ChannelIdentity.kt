package com.mixam.mxtube.data.youtube

data class ChannelRef(
    val key: String,
    val url: String,
)

/**
 * Collapses the shapes a YouTube channel link comes in (/channel/UC…, /@handle, /c/name, /user/name)
 * into one key, so subscribing from a search row, the player or a channel page hits the same entry.
 */
object ChannelIdentity {
    private const val CANONICAL_BASE = "https://www.youtube.com"
    private val CHANNEL_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
    private val HANDLE = Regex("^@[A-Za-z0-9._-]{2,70}$")
    private val NAME = Regex("^[A-Za-z0-9._-]{2,80}$")
    private val CHANNEL_ID = Regex("^UC[A-Za-z0-9_-]{20,26}$")

    fun from(rawUrl: String?): ChannelRef? {
        val raw = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        if (CHANNEL_ID.matches(raw)) {
            return ChannelRef(raw, "$CANONICAL_BASE/channel/$raw")
        }

        val withoutScheme = raw.substringAfter("://", raw)
        val host =
            withoutScheme
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .lowercase()
        if (host !in CHANNEL_HOSTS) return null

        val segments =
            withoutScheme
                .substringAfter('/', "")
                .substringBefore('?')
                .substringBefore('#')
                .split('/', '\\')
                .filter { it.isNotBlank() }

        val first = segments.firstOrNull() ?: return null

        if (first.startsWith('@')) {
            if (!HANDLE.matches(first)) return null
            val key = first.lowercase()
            return ChannelRef(key, "$CANONICAL_BASE/$key")
        }

        val second = segments.getOrNull(1) ?: return null
        val prefix = first.lowercase()
        return when (prefix) {
            "channel" ->
                if (CHANNEL_ID.matches(second) || NAME.matches(second)) {
                    ChannelRef(second, "$CANONICAL_BASE/channel/$second")
                } else {
                    null
                }

            "c", "user" ->
                if (NAME.matches(second)) {
                    ChannelRef("$prefix/${second.lowercase()}", "$CANONICAL_BASE/$prefix/$second")
                } else {
                    null
                }

            else -> null
        }
    }

    fun keyOf(rawUrl: String?): String? = from(rawUrl)?.key

    /** The feed URL is the only place YouTube always spells the channel out as its UC id. */
    fun idFromFeedUrl(feedUrl: String?): String? {
        val query = feedUrl?.substringAfter("?", "")?.substringBefore("#").orEmpty()
        if (query.isEmpty()) return null
        return query.split('&')
            .asSequence()
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "channel_id" }
            ?.get(1)
            ?.takeIf { CHANNEL_ID.matches(it) }
    }

    fun canonicalUrl(channelId: String): String = "$CANONICAL_BASE/channel/$channelId"

    fun isChannelId(value: String?): Boolean = value != null && CHANNEL_ID.matches(value)
}
