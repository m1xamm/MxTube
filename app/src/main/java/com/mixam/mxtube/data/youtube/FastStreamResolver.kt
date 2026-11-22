package com.mixam.mxtube.data.youtube

import android.util.Log
import com.mixam.mxtube.AppLocale
import com.mixam.mxtube.data.network.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

internal object FastStreamResolver {
    private const val LOG_TAG = "MxFast"
    private const val ENDPOINT = "https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false"
    private const val USER_AGENT =
        "com.google.visionos.youtube/1.04(RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; GB)"
    private const val MAX_HEIGHT = 1080
    private const val MAX_QUALITIES = 8

    private val client: OkHttpClient by lazy {
        Http.client
            .newBuilder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    private val videoIdRegex =
        Regex("""(?:[?&]v=|youtu\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})""")

    suspend fun resolve(url: String, audioLabel: String, hl: String, gl: String): PlaybackInfo? {
        val id = videoIdRegex.find(url)?.groupValues?.getOrNull(1) ?: return null
        val body = runCatching { fetch(id, hl, gl) }.getOrNull() ?: return null
        return runCatching { parse(body, id, audioLabel) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrElse {
                Log.w(LOG_TAG, "parse failed for $id", it)
                null
            }
    }

    private suspend fun fetch(videoId: String, hl: String, gl: String): JSONObject {
        val payload =
            """
            {"context":{"client":{"clientName":"VISIONOS","clientVersion":"1.04",
            "clientScreen":"WATCH","platform":"MOBILE","deviceMake":"Apple",
            "deviceModel":"RealityDevice17,1","osName":"visionOS",
            "osVersion":"26.6.0.23O770","hl":"$hl","gl":"$gl"}},
            "videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true}
            """.trimIndent().replace("\n", "")
        val request =
            Request.Builder()
                .url("$ENDPOINT&t=x&id=$videoId")
                .header("User-Agent", USER_AGENT)
                .header("X-Goog-Api-Format-Version", "2")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                JSONObject(response.body.string())
            }
        }
    }

    private fun parse(root: JSONObject, videoId: String, audioLabel: String): PlaybackInfo? {
        val status = root.optJSONObject("playabilityStatus")?.optString("status")
        if (status != "OK") return null
        val formats =
            root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return null
        val details = root.optJSONObject("videoDetails")
        val audio = audioTracks(formats, audioLabel)
        val videoQualities = videoQualities(formats)
        val qualities = videoQualities.ifEmpty { muxedQualities(formats) }
        if (qualities.isEmpty()) return null

        return PlaybackInfo(
            title = details?.optString("title").orEmpty(),
            uploader = details?.optString("author").orEmpty(),
            viewCount = details?.optString("viewCount")?.toLongOrNull() ?: 0L,
            uploadedAtEpochMillis = null,
            thumbnailUrl = bestThumbnail(details?.optJSONObject("thumbnail")?.optJSONArray("thumbnails"))
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            durationSeconds = details?.optString("lengthSeconds")?.toLongOrNull() ?: 0L,
            qualities = qualities,
            defaultQualityIndex = defaultQualityIndex(qualities),
            audioTracks = audio,
            defaultAudioTrackIndex =
                if (audio.isEmpty()) -1 else audio.indexOfFirst { it.isOriginal }.let { if (it >= 0) it else 0 },
            subtitles = subtitles(root.optJSONObject("captions")),
            related = emptyList(),
            uploaderUrl =
                details?.optString("channelId")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "https://www.youtube.com/channel/$it" },
            uploaderAvatarUrl = null,
        )
    }

    private fun audioTracks(formats: JSONArray, fallbackLabel: String): List<PlaybackAudioTrack> {
        val grouped = LinkedHashMap<String, MutableList<JSONObject>>()
        for (index in 0 until formats.length()) {
            val format = formats.optJSONObject(index) ?: continue
            val mime = format.optString("mimeType").substringBefore(';')
            if (!mime.startsWith("audio/")) continue
            if (format.optString("url").isBlank()) continue
            val track = format.optJSONObject("audioTrack")
            val id =
                track?.optString("audioTrackId").takeUnless { it.isNullOrBlank() }
                    ?: listOf(
                        track?.optString("audioLocale").orEmpty(),
                        track?.optString("audioTrackName").orEmpty(),
                        track?.optString("audioTrackType").orEmpty(),
                    ).joinToString("|")
            grouped.getOrPut(id) { mutableListOf() }.add(format)
        }
        val tracks =
            grouped.mapNotNull { (_, list) ->
                val format =
                    list.maxWithOrNull(
                        compareBy<JSONObject> { it.optLong("bitrate", 0L) }
                            .thenBy { it.optString("mimeType").contains("mp4", ignoreCase = true) },
                    ) ?: return@mapNotNull null
                val track = format.optJSONObject("audioTrack")
                val language = track?.optString("audioLocale").takeUnless { it.isNullOrBlank() }
                val name = track?.optString("audioTrackName").takeUnless { it.isNullOrBlank() }
                val type = track?.optString("audioTrackType").orEmpty()
                val label =
                    localizedLabel(language, name)
                        ?: fallbackLabel.takeIf { grouped.size == 1 }
                        ?: name
                        ?: language
                        ?: fallbackLabel
                PlaybackAudioTrack(
                    id = track?.optString("audioTrackId")?.takeIf { it.isNotBlank() } ?: language.orEmpty(),
                    label = label,
                    languageTag = language?.takeIf { it.isNotBlank() },
                    isOriginal =
                        type.equals("ORIGINAL", ignoreCase = true) ||
                            (grouped.size == 1 && type.isBlank()),
                    url = format.optString("url"),
                    mimeType = format.optString("mimeType").substringBefore(';').ifBlank { "audio/mp4" },
                )
            }
        val original = tracks.firstOrNull { it.isOriginal }
        return if (original == null) tracks else listOf(original) + tracks.filter { it !== original }
    }

    private fun localizedLabel(language: String?, name: String?): String? {
        if (!name.isNullOrBlank()) return name
        val tag = language?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Locale.forLanguageTag(tag).getDisplayLanguage(AppLocale.locale) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && !it.equals(tag, ignoreCase = true) }
    }

    private fun videoQualities(formats: JSONArray): List<PlaybackQuality> {
        val candidates = mutableListOf<Candidate>()
        for (index in 0 until formats.length()) {
            val format = formats.optJSONObject(index) ?: continue
            val type = format.optString("mimeType")
            if (!type.startsWith("video/")) continue
            if (format.optString("url").isBlank()) continue
            val height = format.optInt("height", 0)
            if (height <= 0) continue
            val fps = format.optInt("fps", 0).takeIf { it > 0 } ?: 30
            candidates.add(
                Candidate(
                    quality =
                        PlaybackQuality(
                            label = qualityLabel(height, fps),
                            height = height,
                            fps = fps,
                            videoUrl = format.optString("url"),
                            videoMimeType = type.substringBefore(';'),
                        ),
                    preferredCodec = type.contains("avc1", ignoreCase = true),
                    bitrate = format.optLong("bitrate", 0L),
                ),
            )
        }
        return pickPerHeight(candidates)
    }

    private fun muxedQualities(formats: JSONArray): List<PlaybackQuality> {
        val candidates = mutableListOf<Candidate>()
        for (index in 0 until formats.length()) {
            val format = formats.optJSONObject(index) ?: continue
            val type = format.optString("mimeType")
            if (!type.startsWith("video/")) continue
            if (format.optString("url").isBlank()) continue
            val height = format.optInt("height", 0)
            if (height <= 0) continue
            candidates.add(
                Candidate(
                    quality =
                        PlaybackQuality(
                            label = qualityLabel(height, 30),
                            height = height,
                            fps = 30,
                            videoUrl = format.optString("url"),
                            videoMimeType = type.substringBefore(';'),
                        ),
                    preferredCodec = type.contains("avc1", ignoreCase = true),
                    bitrate = format.optLong("bitrate", 0L),
                ),
            )
        }
        return pickPerHeight(candidates)
    }

    private data class Candidate(
        val quality: PlaybackQuality,
        val preferredCodec: Boolean,
        val bitrate: Long,
    )

    private fun pickPerHeight(candidates: List<Candidate>): List<PlaybackQuality> {
        if (candidates.isEmpty()) return emptyList()
        val capped = candidates.filter { it.quality.height <= MAX_HEIGHT }.ifEmpty { candidates }
        return capped
            .groupBy { it.quality.height to it.quality.fps }
            .mapNotNull { (_, list) ->
                list.maxWithOrNull(compareBy<Candidate> { it.preferredCodec }.thenBy { it.bitrate })
            }
            .map { it.quality }
            .sortedWith(compareByDescending<PlaybackQuality> { it.height }.thenByDescending { it.fps })
            .take(MAX_QUALITIES)
    }

    private fun subtitles(captions: JSONObject?): List<PlaybackSubtitle> {
        val tracks =
            captions
                ?.optJSONObject("playerCaptionsTracklistRenderer")
                ?.optJSONArray("captionTracks")
                ?: return emptyList()
        val result = mutableListOf<PlaybackSubtitle>()
        for (index in 0 until tracks.length()) {
            val track = tracks.optJSONObject(index) ?: continue
            val base = track.optString("baseUrl")
            if (base.isBlank()) continue
            val language = track.optString("languageCode")
            if (language.isBlank()) continue
            val auto = track.optString("kind") == "asr"
            val label =
                track.optJSONObject("name")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.takeIf { it.isNotBlank() }
                    ?: track.optString("name").takeIf { it.isNotBlank() && !it.startsWith("{") }
                    ?: language
            result.add(
                PlaybackSubtitle(
                    id = "fast:$language:${if (auto) "asr" else "manual"}",
                    label = label,
                    languageTag = language,
                    isAutoGenerated = auto,
                    url = if (base.contains("fmt=")) base else "$base&fmt=vtt",
                    mimeType = "text/vtt",
                ),
            )
        }
        return result
    }

    private fun bestThumbnail(items: JSONArray?): String? {
        if (items == null || items.length() == 0) return null
        var best: JSONObject? = null
        var bestArea = -1
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val area = item.optInt("width", 0) * item.optInt("height", 0)
            if (area > bestArea) {
                best = item
                bestArea = area
            }
        }
        return best?.optString("url")?.takeIf { it.isNotBlank() }
    }

    private fun qualityLabel(height: Int, fps: Int): String =
        if (fps > 30) "${height}p$fps" else "${height}p"

    private fun defaultQualityIndex(qualities: List<PlaybackQuality>): Int {
        for (height in listOf(720, 1080, 480, 360)) {
            val index = qualities.indexOfFirst { it.height == height }
            if (index >= 0) return index
        }
        return 0
    }
}
