package com.mixam.mxtube.data.youtube

import com.mixam.mxtube.AppLocale
import com.mixam.mxtube.data.network.Http
import org.schabi.newpipe.extractor.ServiceList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal object FastContentResolver {
    private const val ENDPOINT = "https://youtubei.googleapis.com/youtubei/v1/search?prettyPrint=false"
    private const val BROWSE_ENDPOINT = "https://youtubei.googleapis.com/youtubei/v1/browse?prettyPrint=false"
    private const val PLAYLIST_SEARCH_PARAMS = "EgIQAw%3D%3D"
    private const val USER_AGENT =
        "com.google.visionos.youtube/1.04(RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; GB)"

    private val client: OkHttpClient by lazy {
        Http.client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
    }

    suspend fun search(query: String, limit: Int = 24): List<VideoSummary> {
        if (query.isBlank()) return emptyList()
        val body = post(ENDPOINT, JSONObject().put("query", query))
        return parse(JSONObject(body), limit)
    }

    suspend fun searchPlaylists(query: String, limit: Int = 24): List<PlaylistSummary> {
        if (query.isBlank()) return emptyList()
        val body =
            post(
                ENDPOINT,
                JSONObject().put("query", query).put("params", PLAYLIST_SEARCH_PARAMS),
            )
        val sections =
            runCatching { JSONObject(body) }.getOrNull()
                ?.optJSONObject("contents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()
        val playlists = mutableListOf<PlaylistSummary>()
        for (sectionIndex in 0 until sections.length()) {
            val items =
                sections.optJSONObject(sectionIndex)
                    ?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents") ?: continue
            for (itemIndex in 0 until items.length()) {
                val item = items.optJSONObject(itemIndex) ?: continue
                val renderer =
                    item.optJSONObject("compactPlaylistRenderer")
                        ?: item.optJSONObject("playlistRenderer")
                        ?: continue
                val id = renderer.optString("playlistId").takeIf { it.isNotBlank() } ?: continue
                val name = text(renderer.optJSONObject("title")).takeIf { it.isNotBlank() } ?: continue
                val countText =
                    text(renderer.optJSONObject("videoCountText")).ifBlank {
                        text(renderer.optJSONObject("videoCountShortText"))
                    }
                val byline =
                    renderer.optJSONObject("longBylineText") ?: renderer.optJSONObject("shortBylineText")
                playlists +=
                    PlaylistSummary(
                        url = "https://www.youtube.com/playlist?list=$id",
                        name = name,
                        videoCount = Regex("""\d+""").find(countText)?.value?.toLongOrNull() ?: -1L,
                        thumbnailUrl = bestThumbnail(renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")),
                        uploader = text(byline).takeIf { it.isNotBlank() },
                    )
                if (playlists.size >= limit) return playlists
            }
        }
        return playlists
    }

    private fun videoFromLockup(lockup: JSONObject): VideoSummary? {
        if (!lockup.optString("contentType").contains("VIDEO", ignoreCase = true)) return null
        val id = lockup.optString("contentId").takeIf { it.length == 11 } ?: return null
        val metadata = lockup.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = metadata.optJSONObject("title")?.optString("content").takeUnless { it.isNullOrBlank() } ?: return null
        val rows = metadata.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
        val parts = rows?.optJSONObject(0)?.optJSONArray("metadataParts")
        val uploader = parts?.optJSONObject(0)?.optJSONObject("text")?.optString("content").takeUnless { it.isNullOrBlank() } ?: "MxTube"
        val stats = rows?.optJSONObject(1)?.optJSONArray("metadataParts")
        val statsText = (0 until (stats?.length() ?: 0)).mapNotNull { stats?.optJSONObject(it)?.optJSONObject("text")?.optString("content") }
        val image = lockup.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel") ?: lockup.optJSONObject("contentImage")
        val source = image?.optJSONArray("image")?.let { sources ->
            (sources.length() - 1 downTo 0).mapNotNull { sources.optJSONObject(it)?.optString("url")?.takeIf { url -> url.isNotBlank() } }.firstOrNull()
        }
        val overlays = image?.optJSONArray("overlays")
        var duration = ""
        if (overlays != null) {
            for (overlayIndex in 0 until overlays.length()) {
                val badges =
                    overlays.optJSONObject(overlayIndex)
                        ?.optJSONObject("thumbnailBottomOverlayViewModel")
                        ?.optJSONArray("badges") ?: continue
                for (badgeIndex in 0 until badges.length()) {
                    val label =
                        badges.optJSONObject(badgeIndex)
                            ?.optJSONObject("thumbnailBadgeViewModel")
                            ?.optString("text").orEmpty()
                    if (label.matches(Regex("""\d+:\d{2}(:\d{2})?"""))) {
                        duration = label
                        break
                    }
                }
                if (duration.isNotBlank()) break
            }
        }
        return VideoSummary(
            url = "https://www.youtube.com/watch?v=$id",
            title = title,
            uploader = uploader,
            durationSeconds = duration(duration),
            viewCount =
                ViewCountCorrector.correct(
                    "https://www.youtube.com/watch?v=$id",
                    statsText.firstNotNullOfOrNull { ViewCountCorrector.parseViewCount(it) } ?: -1L,
                ),
            uploadedAtEpochMillis = statsText.firstNotNullOfOrNull { relative(it) },
            thumbnailUrl = source ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        )
    }

    suspend fun playlistVideos(playlistId: String): List<VideoSummary> {
        if (playlistId.isBlank()) return emptyList()
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val body = post(BROWSE_ENDPOINT, JSONObject().put("browseId", browseId), "WEB", "2.20250312.01.00")
        val sections =
            runCatching { JSONObject(body) }.getOrNull()
                ?.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()
        val videos = mutableListOf<VideoSummary>()
        for (sectionIndex in 0 until sections.length()) {
            val items =
                sections.optJSONObject(sectionIndex)
                    ?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents") ?: continue
            for (itemIndex in 0 until items.length()) {
                val lockup = items.optJSONObject(itemIndex)?.optJSONObject("lockupViewModel") ?: continue
                val found = videoFromLockup(lockup) ?: continue
                if (found.url !in videos.map { it.url }) videos += found
            }
        }
        return videos
    }

    suspend fun channelVideos(channelId: String, limit: Int = 24): List<VideoSummary> {
        if (channelId.isBlank()) return emptyList()
        val body = post(BROWSE_ENDPOINT, JSONObject().put("browseId", channelId))
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val contents = root.optJSONObject("contents") ?: return emptyList()
        val tab =
            (contents.optJSONObject("singleColumnBrowseResultsRenderer") ?: contents.optJSONObject("twoColumnBrowseResultsRenderer"))
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
        val sections =
            tab?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()
        val videos = mutableListOf<VideoSummary>()
        for (sectionIndex in 0 until sections.length()) {
            val items =
                sections.optJSONObject(sectionIndex)
                    ?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents") ?: continue
            for (itemIndex in 0 until items.length()) {
                val item = items.optJSONObject(itemIndex) ?: continue
                val renderer =
                    item.optJSONObject("compactVideoRenderer") ?: item.optJSONObject("videoRenderer")
                val found =
                    if (renderer != null) {
                        video(renderer)
                    } else {
                        item.optJSONObject("lockupViewModel")?.let(::videoFromLockup)
                    } ?: continue
                if (found.url !in videos.map { it.url }) videos += found
                if (videos.size >= limit) return videos
            }
        }
        return videos
    }

    suspend fun channel(id: String): ChannelPage? {
        if (id.isBlank()) return null
        val body = post(BROWSE_ENDPOINT, JSONObject().put("browseId", id))
        val renderer =
            JSONObject(body).optJSONObject("header")?.optJSONObject("c4TabbedHeaderRenderer")
                ?: return null
        val name = renderer.optString("title").ifBlank { text(renderer.optJSONObject("title")) }
        if (name.isBlank()) return null
        val channelId = renderer.optString("channelId").takeIf { it.isNotBlank() } ?: id
        val header =
            ChannelHeader(
                key = channelId,
                url = "https://www.youtube.com/channel/$channelId",
                name = name,
                avatarUrl = bestThumbnail(renderer.optJSONObject("avatar")?.optJSONArray("thumbnails")),
                bannerUrl = bestThumbnail(renderer.optJSONObject("banner")?.optJSONArray("thumbnails")),
                subscriberCount =
                    ViewCountCorrector.parseViewCount(text(renderer.optJSONObject("subscriberCountText")))
                        ?: -1L,
                description = "",
                handle = text(renderer.optJSONObject("channelHandleText")).takeIf { it.isNotBlank() },
                videoCount =
                    renderer.optJSONArray("videosCountText")
                        ?.optJSONObject(0)
                        ?.optString("text")
                        ?.toLongOrNull() ?: -1L,
                isVerified = verified(renderer.optJSONArray("badges")),
            )
        return ChannelPage(header, ServiceList.YouTube, emptyList())
    }

    suspend fun albums(channelId: String): List<PlaylistSummary> {
        if (channelId.isBlank()) return emptyList()
        val body =
            post(BROWSE_ENDPOINT, JSONObject().put("browseId", channelId), "WEB", "2.20250312.01.00")
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val tabs =
            root.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs") ?: return emptyList()
        val result = mutableListOf<PlaylistSummary>()
        for (tabIndex in 0 until tabs.length()) {
            val sections =
                tabs.optJSONObject(tabIndex)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents") ?: continue
            for (sectionIndex in 0 until sections.length()) {
                val entries =
                    sections.optJSONObject(sectionIndex)
                        ?.optJSONObject("itemSectionRenderer")
                        ?.optJSONArray("contents") ?: continue
                for (entryIndex in 0 until entries.length()) {
                    val lockups =
                        entries.optJSONObject(entryIndex)
                            ?.optJSONObject("shelfRenderer")
                            ?.optJSONObject("content")
                            ?.optJSONObject("horizontalListRenderer")
                            ?.optJSONArray("items") ?: continue
                    for (lockupIndex in 0 until lockups.length()) {
                        val lockup = lockups.optJSONObject(lockupIndex)?.optJSONObject("lockupViewModel") ?: continue
                        if (!lockup.optString("contentType").contains("ALBUM", ignoreCase = true)) continue
                        val album = album(lockup)
                        if (album != null && album.url !in result.map { it.url }) result += album
                    }
                }
            }
        }
        return result
    }

    private fun album(lockup: JSONObject): PlaylistSummary? {
        val id = lockup.optString("contentId").takeIf { it.isNotBlank() } ?: return null
        val metadata = lockup.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val name = metadata?.optJSONObject("title")?.optString("content").takeUnless { it.isNullOrBlank() } ?: return null
        val rows = metadata?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
        val uploader =
            rows?.optJSONObject(0)
                ?.optJSONArray("metadataParts")
                ?.optJSONObject(0)
                ?.optJSONObject("text")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }
        val image =
            lockup.optJSONObject("contentImage")
                ?.optJSONObject("collectionThumbnailViewModel")
                ?.optJSONObject("primaryThumbnail")
                ?.optJSONObject("thumbnailViewModel")
                ?.optJSONObject("image")
                ?.optJSONArray("sources")
        val thumbnail = coverThumbnail(image)
        val badges =
            lockup.optJSONObject("contentImage")
                ?.optJSONObject("collectionThumbnailViewModel")
                ?.optJSONObject("primaryThumbnail")
                ?.optJSONObject("thumbnailViewModel")
                ?.optJSONArray("overlays")
        var count = -1L
        if (badges != null) {
            for (index in 0 until badges.length()) {
                val texts =
                    badges.optJSONObject(index)
                        ?.optJSONObject("thumbnailOverlayBadgeViewModel")
                        ?.optJSONArray("thumbnailBadges")
                if (texts == null) continue
                for (badgeIndex in 0 until texts.length()) {
                    val label =
                        texts.optJSONObject(badgeIndex)
                            ?.optJSONObject("thumbnailBadgeViewModel")
                            ?.optString("text").orEmpty()
                    val number = Regex("""\d+""").find(label)?.value?.toLongOrNull()
                    if (number != null) {
                        count = number
                        break
                    }
                }
                if (count >= 0) break
            }
        }
        return PlaylistSummary(
            url = "https://www.youtube.com/playlist?list=$id",
            name = name,
            videoCount = count,
            thumbnailUrl = thumbnail,
            uploader = uploader,
        )
    }

    private suspend fun post(
        endpoint: String,
        payload: JSONObject,
        clientName: String = "VISIONOS",
        clientVersion: String = "1.04",
    ): String {
        val locale = AppLocale.locale
        val body =
            payload
                .put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject()
                            .put("clientName", clientName)
                            .put("clientVersion", clientVersion)
                            .put("hl", locale.language)
                            .put("gl", country(locale.language)),
                    ),
                ).toString()
        val agent = if (clientName == "WEB") "Mozilla/5.0" else USER_AGENT
        return runInterruptible(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", agent)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val text = response.body.string()
                ViewCountCorrector.record(text)
                text
            }
        }
    }

    private fun coverThumbnail(items: JSONArray?): String? {
        if (items == null) return null
        val candidates =
            (0 until items.length()).mapNotNull { index ->
                val source = items.optJSONObject(index) ?: return@mapNotNull null
                val url = source.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                url to (source.optInt("width", 0).takeIf { it > 0 } ?: Int.MAX_VALUE)
            }
        return candidates.filter { it.second <= 640 }.maxByOrNull { it.second }?.first
            ?: candidates.minByOrNull { it.second }?.first
    }

    private fun bestThumbnail(items: JSONArray?): String? {
        if (items == null || items.length() == 0) return null
        return (items.length() - 1 downTo 0)
            .mapNotNull { items.optJSONObject(it)?.optString("url")?.takeIf { url -> url.isNotBlank() } }
            .firstOrNull()
    }

    private fun verified(badges: JSONArray?): Boolean {
        if (badges == null) return false
        for (index in 0 until badges.length()) {
            val style =
                badges.optJSONObject(index)
                    ?.optJSONObject("metadataBadgeRenderer")
                    ?.optString("style")
            if (!style.isNullOrBlank() && style.contains("VERIFIED", ignoreCase = true)) return true
        }
        return false
    }

    private fun country(language: String): String =
        when (language.lowercase()) {
            "ru" -> "RU"
            "uk" -> "UA"
            else -> "US"
        }

    private fun parse(root: JSONObject, limit: Int): List<VideoSummary> {
        val sections =
            root.optJSONObject("contents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()
        val videos = mutableListOf<VideoSummary>()
        for (index in 0 until sections.length()) {
            val items =
                sections.optJSONObject(index)
                    ?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents") ?: continue
            for (itemIndex in 0 until items.length()) {
                val item = items.optJSONObject(itemIndex) ?: continue
                val renderer =
                    item.optJSONObject("compactVideoRenderer")
                        ?: item.optJSONObject("videoRenderer")
                        ?: continue
                val video = video(renderer) ?: continue
                if (video.url !in videos.map { it.url }) videos += video
                if (videos.size >= limit) return videos
            }
        }
        return videos
    }

    private fun video(renderer: JSONObject): VideoSummary? {
        val id = renderer.optString("videoId").takeIf { it.length == 11 } ?: return null
        val byline = renderer.optJSONObject("longBylineText") ?: renderer.optJSONObject("ownerText")
        val browse =
            byline?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
        val channelId = browse?.optString("browseId").takeUnless { it.isNullOrBlank() }
        val canonical = browse?.optString("canonicalBaseUrl").takeUnless { it.isNullOrBlank() }
        val avatar =
            renderer.optJSONObject("channelThumbnail")
                ?.optJSONArray("thumbnails")
                ?.let { items ->
                    (items.length() - 1 downTo 0).mapNotNull { items.optJSONObject(it)?.optString("url")?.takeIf { url -> url.isNotBlank() } }.firstOrNull()
                }
        return VideoSummary(
            url = "https://www.youtube.com/watch?v=$id",
            title = text(renderer.optJSONObject("title")).takeIf { it.isNotBlank() } ?: "MxTube",
            uploader = text(byline).takeIf { it.isNotBlank() } ?: "MxTube",
            durationSeconds = duration(text(renderer.optJSONObject("lengthText"))),
            viewCount =
                ViewCountCorrector.correct(
                    "https://www.youtube.com/watch?v=$id",
                    ViewCountCorrector.parseViewCount(
                        text(renderer.optJSONObject("viewCountText")).ifBlank {
                            text(renderer.optJSONObject("shortViewCountText"))
                        },
                    ) ?: -1L,
                ),
            uploadedAtEpochMillis = relative(text(renderer.optJSONObject("publishedTimeText"))),
            thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            uploaderUrl =
                when {
                    channelId != null -> "https://www.youtube.com/channel/$channelId"
                    canonical?.startsWith("/") == true -> "https://www.youtube.com$canonical"
                    else -> null
                },
            uploaderAvatarUrl = avatar,
        )
    }

    private fun text(value: JSONObject?): String {
        if (value == null) return ""
        val simple = value.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = value.optJSONArray("runs") ?: return ""
        val builder = StringBuilder()
        for (index in 0 until runs.length()) {
            builder.append(runs.optJSONObject(index)?.optString("text").orEmpty())
        }
        return builder.toString()
    }

    private fun duration(value: String): Long {
        if (value.isBlank()) return 0L
        val parts = value.split(':').mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty()) return 0L
        return parts.fold(0L) { total, part -> total * 60 + part }
    }

    private fun relative(value: String): Long? {
        if (value.isBlank()) return null
        val amount = Regex("""\d+""").find(value)?.value?.toLongOrNull() ?: return null
        val lower = value.lowercase()
        val factor =
            when {
                lower.contains("second") || lower.contains("сек") -> 1_000L
                lower.contains("minute") || lower.contains("мин") -> 60_000L
                lower.contains("hour") || lower.contains("час") || lower.contains("ч.") || lower.contains("ч ") -> 3_600_000L
                lower.contains("week") || lower.contains("нед") -> 604_800_000L
                lower.contains("day") || lower.contains("день") || lower.contains("дн") -> 86_400_000L
                lower.contains("month") || lower.contains("мес") -> 2_592_000_000L
                lower.contains("year") || lower.contains("год") || lower.contains("г.") || lower.contains("г ") -> 31_536_000_000L
                else -> return null
            }
        return System.currentTimeMillis() - amount * factor
    }
}
