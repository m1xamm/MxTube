package com.mixam.mxtube.data.youtube

import android.util.Log
import com.mixam.mxtube.AppLocale
import com.mixam.mxtube.data.network.Http
import org.schabi.newpipe.extractor.ServiceList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal object InvidiousFallback {
    private const val LOG_TAG = "MxInvidious"
    private const val FIELDS =
        "title,author,authorId,authorUrl,authorThumbnails,lengthSeconds,viewCount,published," +
            "adaptiveFormats,formatStreams,recommendedVideos"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val MAX_INSTANCES = 3
    private const val MAX_HEIGHT = 1080
    private const val FAILURE_COOLDOWN_MS = 3_000L

    private val seeds = listOf("https://yt.chocolatemoo53.com")
    private val kioskTypes =
        mapOf(
            "trending_music" to "Music",
            "trending_gaming" to "Gaming",
            "trending_movies_and_shows" to "Movies",
        )
    private val failedUntil = ConcurrentHashMap<String, Long>()

    @Volatile private var lastWorking: String? = null

    private val client: OkHttpClient by lazy {
        Http.client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
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
                    markFailed(instance)
                    null
                }
            if (info != null) {
                markWorking(instance)
                return info
            }
        }
        return null
    }

    private fun candidates(): List<String> {
        val now = System.currentTimeMillis()
        return buildList {
            lastWorking?.let(::add)
            addAll(seeds)
        }
            .filter { (failedUntil[it] ?: 0L) <= now }
            .distinct()
            .take(MAX_INSTANCES)
    }

    private fun markFailed(instance: String) {
        failedUntil[instance] = System.currentTimeMillis() + FAILURE_COOLDOWN_MS
        if (lastWorking == instance) lastWorking = null
    }

    private fun markWorking(instance: String) {
        lastWorking = instance
        failedUntil.remove(instance)
    }

    private suspend fun get(url: String): String =
        runInterruptible(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val text = response.body.string()
                ViewCountCorrector.record(text)
                text
            }
        }

    private suspend fun fetch(instance: String, id: String, audioLabel: String): PlaybackInfo? {
        val body = get("$instance/api/v1/videos/$id?local=true&fields=$FIELDS")
        val root = JSONObject(body)
        val adaptive = root.optJSONArray("adaptiveFormats") ?: JSONArray()
        val muxed = root.optJSONArray("formatStreams") ?: JSONArray()
        val audio = audioTracks(adaptive, audioLabel)
        val adaptiveQualities = if (audio.isNotEmpty()) videoQualities(adaptive) else emptyList()
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
            audioTracks = audio,
            defaultAudioTrackIndex = originalIndex(audio),
            subtitles = emptyList(),
            related = related(root.optJSONArray("recommendedVideos")),
            uploaderUrl = uploaderUrl,
            uploaderAvatarUrl = bestAvatar(root.optJSONArray("authorThumbnails")),
        )
    }

    private fun originalIndex(tracks: List<PlaybackAudioTrack>): Int {
        if (tracks.isEmpty()) return -1
        val index = tracks.indexOfFirst { it.isOriginal }
        return if (index >= 0) index else 0
    }

    private fun audioTracks(formats: JSONArray, fallbackLabel: String): List<PlaybackAudioTrack> {
        val grouped = LinkedHashMap<String, MutableList<JSONObject>>()
        for (index in 0 until formats.length()) {
            val format = formats.optJSONObject(index) ?: continue
            val mime = format.optString("type").substringBefore(';')
            if (!mime.startsWith("audio/")) continue
            if (format.optString("url").isBlank()) continue
            val track = format.optJSONObject("audioTrack")
            val id =
                track?.optString("audioTrackId").takeUnless { it.isNullOrBlank() }
                    ?: listOf(
                        track?.optString("audioLocale").orEmpty(),
                        track?.optString("languageCode").orEmpty(),
                        track?.optString("audioTrackName").orEmpty(),
                        track?.optString("audioTrackType").orEmpty(),
                        format.optString("languageCode"),
                        format.optString("audioTrackId"),
                    ).joinToString("|")
            grouped.getOrPut(id) { mutableListOf() }.add(format)
        }
        val tracks =
            grouped.mapNotNull { (_, list) ->
                val format = list.maxWithOrNull(compareBy<JSONObject> { it.optLong("bitrate", 0L) }) ?: return@mapNotNull null
                val track = format.optJSONObject("audioTrack")
                val language =
                    track?.optString("audioLocale").takeUnless { it.isNullOrBlank() }
                        ?: track?.optString("languageCode").takeUnless { it.isNullOrBlank() }
                        ?: format.optString("languageCode").takeIf { it.isNotBlank() }
                val name =
                    track?.optString("audioTrackName").takeUnless { it.isNullOrBlank() }
                        ?: format.optString("audioTrackName").takeIf { it.isNotBlank() }
                val type =
                    track?.optString("audioTrackType").takeUnless { it.isNullOrBlank() }
                        ?: format.optString("audioTrackType")
                PlaybackAudioTrack(
                    id = track?.optString("audioTrackId")?.takeIf { it.isNotBlank() } ?: language.orEmpty(),
                    label = localizedLabel(language, name) ?: fallbackLabel,
                    languageTag = language?.takeIf { it.isNotBlank() },
                    isOriginal = type.equals("ORIGINAL", ignoreCase = true) || (grouped.size == 1 && name.isNullOrBlank()),
                    url = format.optString("url"),
                    mimeType = format.optString("type").substringBefore(';').ifBlank { "audio/mp4" },
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
            ?.let(::directAvatar)
    }

    private fun avatarUrl(item: JSONObject): String? {
        val thumbnails = item.optJSONArray("authorThumbnails") ?: return null
        for (index in thumbnails.length() - 1 downTo 0) {
            val url = thumbnails.optJSONObject(index)?.optString("url")?.takeIf { it.isNotBlank() }
            if (url != null) return directAvatar(url)
        }
        return null
    }

    private fun directAvatar(url: String): String =
        url.replace(Regex("^https?://[^/]+/ggpht/"), "https://yt3.ggpht.com/")

    private fun bestBanner(items: JSONArray?): String? {
        if (items == null || items.length() == 0) return null
        return items.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
    }

    private fun heightFromLabel(label: String): Int? =
        Regex("""^(\d{3,4})p""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun fpsFromLabel(label: String): Int {
        val fps = Regex("""p(\d{2,3})$""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return fps ?: 30
    }

    private fun qualityLabel(height: Int, fps: Int): String =
        if (fps > 30) "${height}p$fps" else "${height}p"

    suspend fun searchVideos(query: String, limit: Int = 24): List<VideoSummary> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        for (instance in candidates()) {
            val videos =
                try {
                    parseSearchItems(get("$instance/api/v1/search?q=$encodedQuery&type=video"))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(LOG_TAG, "search failed: $instance", error)
                    markFailed(instance)
                    continue
                }
            if (videos.isNotEmpty()) {
                markWorking(instance)
                return videos.take(limit)
            }
        }
        return emptyList()
    }

    suspend fun kiosk(kioskId: String, limit: Int = 24): List<VideoSummary> {
        val type = kioskTypes[kioskId] ?: return emptyList()
        for (instance in candidates()) {
            val videos =
                try {
                    parseSearchItems(get("$instance/api/v1/trending?type=$type"))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(LOG_TAG, "kiosk failed: $instance", error)
                    continue
                }
            if (videos.isNotEmpty()) {
                markWorking(instance)
                return videos.take(limit)
            }
        }
        return emptyList()
    }

    private fun parseSearchItems(body: String): List<VideoSummary> {
        val root = JSONArray(body)
        val videos = mutableListOf<VideoSummary>()
        for (index in 0 until root.length()) {
            val item = root.optJSONObject(index) ?: continue
            val id = item.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            videos += videoSummary(item, id)
        }
        return videos
    }

    private fun videoSummary(item: JSONObject, id: String): VideoSummary =
        VideoSummary(
            url = "https://www.youtube.com/watch?v=$id",
            title = item.optString("title").takeIf { it.isNotBlank() } ?: "MxTube",
            uploader = item.optString("author").ifBlank { "MxTube" },
            durationSeconds = item.optLong("lengthSeconds", 0L),
            viewCount = item.optLong("viewCount", -1L),
            uploadedAtEpochMillis = item.optLong("published").takeIf { it > 0 }?.times(1000L),
            thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            uploaderUrl = authorUrl(item),
            uploaderAvatarUrl = avatarUrl(item),
        )

    private fun authorUrl(item: JSONObject): String? {
        val url = item.optString("authorUrl")
        val id = item.optString("authorId")
        return when {
            url.startsWith("http") -> url
            url.startsWith("/") -> "https://www.youtube.com$url"
            id.isNotBlank() -> "https://www.youtube.com/channel/$id"
            else -> null
        }
    }

    private fun defaultQualityIndex(qualities: List<PlaybackQuality>): Int {
        for (height in listOf(720, 1080, 480, 360)) {
            val index = qualities.indexOfFirst { it.height == height }
            if (index >= 0) return index
        }
        return 0
    }

    suspend fun channelPage(url: String): ChannelPage? {
        val id = resolveChannelId(url) ?: return null
        for (instance in candidates()) {
            try {
                val root = JSONObject(get("$instance/api/v1/channels/$id"))
                val name = root.optString("author").ifBlank { root.optString("name") }
                if (name.isBlank()) continue
                val header =
                    ChannelHeader(
                        key = root.optString("authorId").takeIf { it.isNotBlank() } ?: id,
                        url = authorUrl(root) ?: "https://www.youtube.com/channel/$id",
                        name = name,
                        avatarUrl = bestAvatar(root.optJSONArray("authorThumbnails")),
                        bannerUrl = bestBanner(root.optJSONArray("authorBanners")),
                        subscriberCount = root.optLong("subCount", -1L),
                        description = root.optString("description"),
                        handle = root.optString("channelHandle").ifBlank { id.takeIf { it.startsWith("@") } },
                        videoCount = root.optLong("videoCount", -1L),
                        isVerified = root.optBoolean("authorVerified", false),
                    )
                markWorking(instance)
                return ChannelPage(header, ServiceList.YouTube, emptyList())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(LOG_TAG, "channel page failed: $instance", error)
                markFailed(instance)
            }
        }
        return channelBySearch(id)
    }

    private suspend fun channelBySearch(id: String): ChannelPage? {
        val query = java.net.URLEncoder.encode(id, "UTF-8")
        for (instance in candidates()) {
            val array =
                try {
                    JSONArray(get("$instance/api/v1/search?q=$query&type=channel"))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(LOG_TAG, "channel search failed: $instance", error)
                    markFailed(instance)
                    continue
                }
            val item =
                (0 until array.length())
                    .mapNotNull { array.optJSONObject(it) }
                    .firstOrNull { found ->
                        found.optString("authorId").equals(id, ignoreCase = true) ||
                            found.optString("channelHandle").removePrefix("@").equals(id.removePrefix("@"), ignoreCase = true)
                    }
                    ?: array.optJSONObject(0)
            if (item == null) continue
            val name = item.optString("author").ifBlank { item.optString("name") }
            if (name.isBlank()) continue
            markWorking(instance)
            val header =
                ChannelHeader(
                    key = item.optString("authorId").takeIf { it.isNotBlank() } ?: id,
                    url = authorUrl(item) ?: "https://www.youtube.com/channel/$id",
                    name = name,
                    avatarUrl = avatarUrl(item),
                    bannerUrl = null,
                    subscriberCount = item.optLong("subCount", -1L),
                    description = item.optString("description"),
                    handle = item.optString("channelHandle").ifBlank { id.takeIf { it.startsWith("@") } },
                    videoCount = item.optLong("videoCount", -1L),
                    isVerified = item.optBoolean("authorVerified", false),
                )
            return ChannelPage(header, ServiceList.YouTube, emptyList())
        }
        return null
    }

    private suspend fun resolveChannelId(url: String): String? {
        channelIdOf(url).takeIf { it.isNotBlank() && !it.startsWith("@") }?.let { return it }
        val handle =
            channelIdOf(url).removePrefix("@").takeIf { it.isNotBlank() } ?: return null
        for (instance in candidates()) {
            val body =
                try {
                    get("$instance/api/v1/search?q=${java.net.URLEncoder.encode("@$handle", "UTF-8")}&type=channel")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(LOG_TAG, "handle lookup failed: $instance", error)
                    markFailed(instance)
                    continue
                }
            val array = runCatching { JSONArray(body) }.getOrNull() ?: continue
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val candidate = item.optString("channelHandle").removePrefix("@")
                if (candidate.equals(handle, ignoreCase = true)) {
                    return item.optString("authorId").takeIf { it.isNotBlank() } ?: "@$handle"
                }
            }
            val first = array.optJSONObject(0)
            val firstHandle = first?.optString("channelHandle").orEmpty().removePrefix("@")
            if (firstHandle.equals(handle, ignoreCase = true)) {
                return first?.optString("authorId")?.takeIf { it.isNotBlank() } ?: "@$handle"
            }
        }
        return "@$handle"
    }

    suspend fun channelVideos(channelId: String): List<VideoSummary> {
        for (instance in candidates()) {
            try {
                val body = get("$instance/api/v1/channels/$channelId/videos")
                val videos = parseChannelVideos(body)
                if (videos.isNotEmpty()) {
                    markWorking(instance)
                    return videos.take(50)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(LOG_TAG, "channel videos failed: $instance", error)
                markFailed(instance)
            }
        }
        return emptyList()
    }

    suspend fun channelPlaylists(channelId: String): List<PlaylistSummary> {
        for (instance in candidates()) {
            try {
                val root = JSONObject(get("$instance/api/v1/channels/$channelId/playlists"))
                val array = root.optJSONArray("playlists") ?: root.optJSONArray("items") ?: JSONArray()
                val playlists = mutableListOf<PlaylistSummary>()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("playlistId").ifBlank { item.optString("id") }.ifBlank { item.optString("uuid") }
                    if (id.isBlank()) continue
                    playlists += PlaylistSummary(
                        url = "https://www.youtube.com/playlist?list=$id",
                        name = item.optString("title"),
                        videoCount = item.optLong("videoCount", 0L),
                        thumbnailUrl = null,
                        uploader = item.optString("author").takeIf { it.isNotBlank() },
                    )
                }
                if (playlists.isNotEmpty()) {
                    markWorking(instance)
                    return playlists.take(20)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(LOG_TAG, "channel playlists failed: $instance", error)
                markFailed(instance)
            }
        }
        return emptyList()
    }

    suspend fun relatedVideos(url: String): List<VideoSummary> {
        val id = videoIdRegex.find(url)?.groupValues?.getOrNull(1) ?: return emptyList()
        for (instance in candidates()) {
            try {
                val root = JSONObject(get("$instance/api/v1/videos/$id?local=true&fields=title,author,lengthSeconds,viewCount,published,recommendedVideos"))
                val videos = related(root.optJSONArray("recommendedVideos")).take(8)
                if (videos.isNotEmpty()) {
                    markWorking(instance)
                    return videos
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(LOG_TAG, "related failed: $instance", error)
                markFailed(instance)
            }
        }
        return emptyList()
    }

    private fun parseChannelVideos(body: String): List<VideoSummary> {
        val root = runCatching { JSONObject(body) }.getOrNull()
        val array =
            root?.optJSONArray("videos")
                ?: root?.optJSONArray("items")
                ?: runCatching { JSONArray(body) }.getOrNull()
                ?: return emptyList()
        val videos = mutableListOf<VideoSummary>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            videos += videoSummary(item, id)
        }
        return videos
    }

    private fun channelIdOf(url: String): String {
        val direct = Regex("/channel/([A-Za-z0-9_-]{2,})").find(url)?.groupValues?.getOrNull(1)
        if (direct != null) return direct
        val handle = Regex("/@([A-Za-z0-9_-]+)").find(url)?.groupValues?.getOrNull(1)
        return handle?.let { "@$it" } ?: ""
    }
}
