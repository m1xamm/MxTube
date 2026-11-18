package com.m1xamm.mxtube.data.youtube

import android.util.Log
import com.m1xamm.mxtube.data.network.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal object InvidiousFallback {
    private const val LOG_TAG = "MxInvidious"
    private const val DISCOVERY_URL = "https://api.invidious.io/instances.json?sort_by=health"
    private const val FIELDS =
        "title,author,authorId,authorUrl,authorThumbnails,lengthSeconds,viewCount,published," +
            "adaptiveFormats,formatStreams,recommendedVideos"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val DISCOVERY_TTL_MS = 30 * 60 * 1000L
    private const val MAX_INSTANCES = 3
    private const val MAX_HEIGHT = 1080

    private val seeds = listOf("https://yt.chocolatemoo53.com")

    @Volatile private var discoveredAt = 0L

    @Volatile private var discovered: List<String> = emptyList()

    @Volatile private var lastWorking: String? = null

    private val client: OkHttpClient by lazy {
        Http.client.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    }

    private val videoIdRegex =
        Regex("""(?:[?&]v=|youtu\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})""")

    suspend fun resolve(url: String, audioLabel: String): PlaybackInfo? {
        val id = videoIdRegex.find(url)?.groupValues?.getOrNull(1) ?: return null
        for (instance in candidates()) {
            val info =
                try {
                    fetch(instance, id, audioLabel)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(LOG_TAG, "instance failed: $instance", error)
                    null
                }
            if (info != null) {
                lastWorking = instance
                Log.d(LOG_TAG, "resolved $id via $instance")
                return info
            }
        }
        return null
    }

    private suspend fun candidates(): List<String> {
        if (System.currentTimeMillis() - discoveredAt > DISCOVERY_TTL_MS) {
            refreshDiscovery()
        }
        return buildList {
            lastWorking?.let(::add)
            addAll(discovered)
            addAll(seeds)
        }.filter { it.isNotBlank() }.distinct().take(MAX_INSTANCES)
    }

    private suspend fun refreshDiscovery() {
        val body = runCatching { get(DISCOVERY_URL) }.getOrNull() ?: return
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return
        val hosts = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONArray(index) ?: continue
            val info = entry.optJSONObject(1) ?: continue
            if (!info.optBoolean("api")) continue
            if (info.optString("type") != "https") continue
            if (info.optJSONObject("monitor")?.optBoolean("down") == true) continue
            val uri = info.optString("uri").trimEnd('/')
            if (uri.startsWith("https://")) hosts.add(uri)
        }
        discovered = hosts
        discoveredAt = System.currentTimeMillis()
    }

    private suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body.string()
            }
        }

    private suspend fun fetch(instance: String, id: String, audioLabel: String): PlaybackInfo? {
        val body = get("$instance/api/v1/videos/$id?local=true&fields=$FIELDS")
        val root = JSONObject(body)
        val adaptive = root.optJSONArray("adaptiveFormats") ?: JSONArray()
        val muxed = root.optJSONArray("formatStreams") ?: JSONArray()
        val audio = bestAudio(adaptive, audioLabel)
        val adaptiveQualities = if (audio != null) videoQualities(adaptive) else emptyList()
        val qualities = adaptiveQualities.ifEmpty { muxedQualities(muxed) }
        if (qualities.isEmpty()) return null

        val authorUrl = root.optString("authorUrl")
        val authorId = root.optString("authorId")
        val uploaderUrl =
            when {
                authorUrl.startsWith("http") -> authorUrl
                authorUrl.startsWith("/") -> "https://www.youtube.com$authorUrl"
                authorId.isNotBlank() -> "https://www.youtube.com/channel/$authorId"
                else -> null
            }
        val published = root.optLong("published", 0L)

        return PlaybackInfo(
            title = root.optString("title"),
            uploader = root.optString("author"),
            viewCount = root.optLong("viewCount", 0L),
            uploadedAtEpochMillis = published.takeIf { it > 0 }?.times(1000L),
            thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            durationSeconds = root.optLong("lengthSeconds", 0L),
            qualities = qualities,
            defaultQualityIndex = defaultQualityIndex(qualities),
            audioTracks = audio?.let { listOf(it) }.orEmpty(),
            defaultAudioTrackIndex = if (audio == null) -1 else 0,
            subtitles = emptyList(),
            related = related(root.optJSONArray("recommendedVideos")),
            uploaderUrl = uploaderUrl,
            uploaderAvatarUrl = bestAvatar(root.optJSONArray("authorThumbnails")),
        )
    }

    private fun bestAudio(formats: JSONArray, label: String): PlaybackAudioTrack? {
        var best: JSONObject? = null
        var bestScore = -1.0
        for (index in 0 until formats.length()) {
            val format = formats.optJSONObject(index) ?: continue
            val mime = format.optString("type").substringBefore(';')
            if (!mime.startsWith("audio/")) continue
            val url = format.optString("url")
            if (url.isBlank()) continue
            val score =
                format.optLong("bitrate", 0L).toDouble() + if (mime.contains("mp4")) 1_000_000.0 else 0.0
            if (score > bestScore) {
                best = format
                bestScore = score
            }
        }
        val format = best ?: return null
        return PlaybackAudioTrack(
            id = "invidious",
            label = label,
            languageTag = null,
            isOriginal = true,
            url = format.optString("url"),
            mimeType = format.optString("type").substringBefore(';').ifBlank { "audio/mp4" },
        )
    }

    private data class Candidate(
        val quality: PlaybackQuality,
        val preferredCodec: Boolean,
        val bitrate: Long,
    )

    private fun videoQualities(formats: JSONArray): List<PlaybackQuality> {
        val candidates = mutableListOf<Candidate>()
        for (index in 0 until formats.length()) {
            val format = formats.optJSONObject(index) ?: continue
            val type = format.optString("type")
            val mime = type.substringBefore(';')
            if (!mime.startsWith("video/")) continue
            val url = format.optString("url")
            if (url.isBlank()) continue
            val height =
                format.optInt("height", 0).takeIf { it > 0 }
                    ?: heightFromLabel(format.optString("qualityLabel"))
                    ?: continue
            val fps = fpsFromLabel(format.optString("qualityLabel"))
            candidates.add(
                Candidate(
                    quality =
                        PlaybackQuality(
                            label = qualityLabel(height, fps),
                            height = height,
                            fps = fps,
                            videoUrl = url,
                            videoMimeType = mime,
                        ),
                    preferredCodec =
                        type.contains("avc1", ignoreCase = true) ||
                            type.contains("mp4a", ignoreCase = true),
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
            val url = format.optString("url")
            if (url.isBlank()) continue
            val height =
                heightFromLabel(format.optString("qualityLabel"))
                    ?: format.optString("resolution").substringAfter('x').toIntOrNull()
                    ?: continue
            candidates.add(
                Candidate(
                    quality =
                        PlaybackQuality(
                            label = qualityLabel(height, 30),
                            height = height,
                            fps = 30,
                            videoUrl = url,
                            videoMimeType =
                                format.optString("type").substringBefore(';').ifBlank { "video/mp4" },
                        ),
                    preferredCodec = true,
                    bitrate = format.optLong("bitrate", 0L),
                ),
            )
        }
        return pickPerHeight(candidates)
    }

    private fun pickPerHeight(candidates: List<Candidate>): List<PlaybackQuality> {
        if (candidates.isEmpty()) return emptyList()
        val capped =
            candidates.filter { it.quality.height <= MAX_HEIGHT }.ifEmpty { candidates }
        return capped
            .groupBy { it.quality.height to it.quality.fps }
            .mapNotNull { (_, list) ->
                list.maxWithOrNull(
                    compareBy<Candidate> { it.preferredCodec }.thenBy { it.bitrate },
                )
            }
            .map { it.quality }
            .sortedWith(compareByDescending<PlaybackQuality> { it.height }.thenByDescending { it.fps })
    }

    private fun related(items: JSONArray?): List<VideoSummary> {
        if (items == null) return emptyList()
        val result = mutableListOf<VideoSummary>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("videoId")
            if (id.length != 11) continue
            result.add(
                VideoSummary(
                    url = "https://www.youtube.com/watch?v=$id",
                    title = item.optString("title"),
                    uploader = item.optString("author"),
                    durationSeconds = item.optLong("lengthSeconds", 0L),
                    viewCount = item.optLongOrUnknown("viewCount"),
                    uploadedAtEpochMillis = null,
                    thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                ),
            )
        }
        return result
    }

    private fun JSONObject.optLongOrUnknown(name: String): Long {
        if (!has(name) || isNull(name)) return -1L
        return optLong(name, -1L)
    }

    private fun bestAvatar(items: JSONArray?): String? {
        if (items == null || items.length() == 0) return null
        return items.optJSONObject(items.length() - 1)?.optString("url")?.takeIf { it.isNotBlank() }
    }

    private fun heightFromLabel(label: String): Int? =
        Regex("""^(\d{3,4})p""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun fpsFromLabel(label: String): Int {
        val fps = Regex("""p(\d{2,3})$""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return fps ?: 30
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
