package com.m1xamm.mxtube.data.youtube

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal object ViewCountCorrector {
    private const val MAX_BODY_LENGTH = 4_000_000
    private const val MAX_ENTRIES = 5_000
    private val counts = ConcurrentHashMap<String, Long>()
    private val videoIdRegex = Regex("""(?:v=|youtu\.be/)([A-Za-z0-9_-]{11})""")
    private val numberRegex = Regex("""(\d[\d \u00A0\u202F]*(?:[.,]\d+)?)""")

    fun record(body: String) {
        if (body.length > MAX_BODY_LENGTH) return
        if (!body.contains("\"videoRenderer\"") &&
            !body.contains("\"compactVideoRenderer\"") &&
            !body.contains("\"lockupViewModel\"")
        ) {
            return
        }
        if (counts.size > MAX_ENTRIES) counts.clear()
        val jsonStart = body.indexOf('{')
        if (jsonStart < 0) return
        val root = runCatching { JSONObject(body.substring(jsonStart)) }.getOrNull() ?: return
        runCatching { walk(root, 0) }
    }

    fun correct(
        url: String,
        fallback: Long,
    ): Long {
        val id = videoIdRegex.find(url)?.groupValues?.getOrNull(1) ?: return fallback
        val corrected = counts[id] ?: return fallback
        return maxOf(fallback, corrected)
    }

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
                    if (key == "videoRenderer" || key == "compactVideoRenderer") {
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
                if (part.optJSONObject("leadingIcon") == null) continue
                val text = part.optJSONObject("text")?.optString("content").orEmpty()
                val parsed = parseViewCount(text) ?: continue
                counts.merge(id, parsed, ::maxOf)
                return
            }
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
