package com.m1xamm.mxtube.data.youtube

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal object ViewCountCorrector {
    private const val MAX_BODY_LENGTH = 4_000_000
    private const val MAX_ENTRIES = 5_000
    private val counts = ConcurrentHashMap<String, Long>()
    private val subscribers = ConcurrentHashMap<String, Long>()
    private val handles = ConcurrentHashMap<String, String>()
    private val channelVideos = ConcurrentHashMap<String, Long>()
    private val viewsLabel = Regex("views|просмотр|перегляд", RegexOption.IGNORE_CASE)
    private val subscribersLabel = Regex("subscriber|подписчик|підписник", RegexOption.IGNORE_CASE)
    private val videosLabel = Regex("videos?|видео|відео", RegexOption.IGNORE_CASE)
    private val videoIdRegex = Regex("""(?:v=|youtu\.be/)([A-Za-z0-9_-]{11})""")
    private val numberRegex = Regex("""(\d[\d \u00A0\u202F]*(?:[.,]\d+)?)""")

    private val avatars = ConcurrentHashMap<String, String>()
    private val avatarRegexes =
        listOf(
            Regex("\"decoratedAvatarViewModel\".{0,600}?\"url\":\"([^\"]+)\""),
            Regex("\"channelThumbnailWithLinkRenderer\".{0,600}?\"url\":\"([^\"]+)\""),
        )
    private val renderedIdRegex = Regex("\"(?:videoId|contentId)\":\"([A-Za-z0-9_-]{11})\"")

    fun record(body: String) {
        if (body.length > MAX_BODY_LENGTH) return
        recordAvatars(body)
        if (!body.contains("\"videoRenderer\"") &&
            !body.contains("\"compactVideoRenderer\"") &&
            !body.contains("\"gridVideoRenderer\"") &&
            !body.contains("\"lockupViewModel\"") &&
            !body.contains("\"channelMetadataRenderer\"")
        ) {
            return
        }
        if (counts.size > MAX_ENTRIES || subscribers.size > MAX_ENTRIES ||
            handles.size > MAX_ENTRIES || channelVideos.size > MAX_ENTRIES ||
            avatars.size > MAX_ENTRIES
        ) {
            counts.clear()
            subscribers.clear()
            handles.clear()
            channelVideos.clear()
            avatars.clear()
        }
        val jsonStart = body.indexOf('{')
        if (jsonStart < 0) return
        val root = runCatching { JSONObject(body.substring(jsonStart)) }.getOrNull() ?: return
        runCatching {
            walk(root, 0)
            val metadata = root.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
            val id = metadata?.optString("externalId").orEmpty()
            if (ChannelIdentity.isChannelId(id)) {
                recordChannelMetadata(root.optJSONObject("header"), id, 0)
                val vanityUrl = metadata?.optString("vanityChannelUrl").orEmpty()
                if (ChannelIdentity.from(vanityUrl)?.key?.startsWith("@") == true) {
                    handles[id] = URI(vanityUrl).path.trim('/')
                }
            }
        }
    }

    fun correct(
        url: String,
        fallback: Long,
    ): Long {
        val id = videoIdRegex.find(url)?.groupValues?.getOrNull(1) ?: return fallback
        val corrected = counts[id] ?: return fallback
        return maxOf(fallback, corrected)
    }

    fun avatar(url: String): String? =
        videoIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { avatars[it] }

    private fun recordAvatars(body: String) {
        if (!body.contains("\"decoratedAvatarViewModel\"") &&
            !body.contains("\"channelThumbnailWithLinkRenderer\"")
        ) {
            return
        }
        val ids = renderedIdRegex.findAll(body).toList()
        if (ids.isEmpty()) return
        for (regex in avatarRegexes) {
            for (match in regex.findAll(body)) {
                val url = match.groupValues[1]
                if (!url.startsWith("http")) continue
                val owner = ids.lastOrNull { it.range.first < match.range.first } ?: continue
                if (avatars.size >= MAX_ENTRIES) return
                avatars[owner.groupValues[1]] = url
            }
        }
    }

    fun correctSubscribers(channelId: String, fallback: Long): Long =
        maxOf(fallback, subscribers[channelId] ?: fallback)

    fun channelHandle(channelId: String): String? = handles[channelId]

    fun channelVideoCount(channelId: String): Long = channelVideos[channelId] ?: -1L

    internal fun parseViewCount(text: String): Long? {
        val normalized =
            text.lowercase(Locale.ROOT)
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .trim()
        val match = numberRegex.find(normalized) ?: return null
        val value =
            match.groupValues[1]
                .replace(" ", "")
                .replace("\u00A0", "")
                .replace("\u202F", "")
                .replace(",", ".")
                .toDoubleOrNull() ?: return null
        val suffix = normalized.substring(match.range.last + 1).trimStart()
        val multiplier =
            when {
                suffix.startsWith("млрд") ||
                    suffix.startsWith("mrd") ||
                    suffix.startsWith("billion") ||
                    suffix.startsWith("b") -> 1_000_000_000.0
                suffix.startsWith("млн") ||
                    suffix.startsWith("mio") ||
                    suffix.startsWith("million") ||
                    suffix.startsWith("m") -> 1_000_000.0
                suffix.startsWith("тыс") ||
                    suffix.startsWith("тис") ||
                    suffix.startsWith("tsd") ||
                    suffix.startsWith("tys") ||
                    suffix.startsWith("k") -> 1_000.0
                else -> 1.0
            }
        if (multiplier == 1.0 && value < 1_000) return value.toLong()
        return (value * multiplier).toLong()
    }

    private fun walk(
        value: Any?,
        depth: Int,
    ) {
        if (depth > 40) return
        when (value) {
            is JSONObject -> {
                for (key in value.keys()) {
                    if (key == "videoRenderer" || key == "compactVideoRenderer" || key == "gridVideoRenderer") {
                        value.optJSONObject(key)?.let(::recordRenderer)
                    }
                    if (key == "lockupViewModel") {
                        value.optJSONObject(key)?.let(::recordLockup)
                    }
                    walk(value.opt(key), depth + 1)
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    walk(value.opt(index), depth + 1)
                }
            }
        }
    }

    private fun recordRenderer(renderer: JSONObject) {
        val id = renderer.optString("videoId")
        if (id.isBlank()) return
        val text =
            textOf(renderer.optJSONObject("viewCountText"))
                ?: textOf(renderer.optJSONObject("shortViewCountText"))
                ?: return
        val parsed = parseViewCount(text) ?: return
        counts.merge(id, parsed, ::maxOf)
    }

    private fun recordLockup(viewModel: JSONObject) {
        val type = viewModel.optString("contentType")
        if (type.isNotBlank() && type != "LOCKUP_CONTENT_TYPE_VIDEO") return
        val id = viewModel.optString("contentId")
        if (id.isBlank()) return
        val rows =
            viewModel.optJSONObject("metadata")
                ?.optJSONObject("lockupMetadataViewModel")
                ?.optJSONObject("metadata")
                ?.optJSONObject("contentMetadataViewModel")
                ?.optJSONArray("metadataRows")
                ?: return
        for (rowIndex in 0 until rows.length()) {
            val parts = rows.optJSONObject(rowIndex)?.optJSONArray("metadataParts") ?: continue
            for (partIndex in 0 until parts.length()) {
                val part = parts.optJSONObject(partIndex) ?: continue
                val text = part.optJSONObject("text")?.optString("content").orEmpty()
                if (part.optJSONObject("leadingIcon") == null && !viewsLabel.containsMatchIn(text)) continue
                val parsed = parseViewCount(text) ?: continue
                counts.merge(id, parsed, ::maxOf)
                return
            }
        }
    }

    private fun recordChannelMetadata(value: Any?, channelId: String, depth: Int) {
        if (depth > 24) return
        when (value) {
            is JSONObject -> {
                textOf(value.optJSONObject("subscriberCountText"))?.let(::parseViewCount)?.let {
                    subscribers.merge(channelId, it, ::maxOf)
                }
                textOf(value.optJSONObject("videosCountText"))?.let(::parseViewCount)?.let {
                    channelVideos[channelId] = it
                }
                val text = textOf(value) ?: value.optString("content")
                if (subscribersLabel.containsMatchIn(text)) {
                    parseViewCount(text)?.let { subscribers.merge(channelId, it, ::maxOf) }
                }
                val rows = value.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
                if (rows != null) {
                    for (rowIndex in 0 until rows.length()) {
                        val parts = rows.optJSONObject(rowIndex)?.optJSONArray("metadataParts") ?: continue
                        for (partIndex in 0 until parts.length()) {
                            val content = parts.optJSONObject(partIndex)?.optJSONObject("text")?.optString("content").orEmpty().trim()
                            if (content.startsWith("@") && !content.any(Char::isWhitespace) && !content.contains('/')) {
                                handles[channelId] = content
                            }
                            if (videosLabel.containsMatchIn(content) && !subscribersLabel.containsMatchIn(content)) {
                                parseViewCount(content)?.let { channelVideos[channelId] = it }
                            }
                        }
                    }
                }
                for (key in value.keys()) recordChannelMetadata(value.opt(key), channelId, depth + 1)
            }
            is JSONArray -> for (index in 0 until value.length()) recordChannelMetadata(value.opt(index), channelId, depth + 1)
        }
    }

    private fun textOf(value: JSONObject?): String? {
        if (value == null) return null
        value.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = value.optJSONArray("runs") ?: return null
        return buildString {
            for (index in 0 until runs.length()) {
                append(runs.optJSONObject(index)?.optString("text").orEmpty())
            }
        }.takeIf { it.isNotBlank() }
    }
}
