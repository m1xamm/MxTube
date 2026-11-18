package com.m1xamm.mxtube.data.youtube

import com.m1xamm.mxtube.AppLocale
import com.m1xamm.mxtube.R
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.schabi.newpipe.extractor.Extractor
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabExtractor
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale

class YouTubeRepository(
    private val string: (Int) -> String = { AppLocale.string(it) },
    private val locale: () -> Locale = { AppLocale.locale },
) {
    private companion object {
        const val LOG_TAG = "MxRepository"
    }

    fun search(query: String): YouTubeSearchSession =
        YouTubeSearchSession(
            ServiceList.YouTube.getSearchExtractor(query).withAppLocale(),
        )

    fun kiosk(kioskId: String): YouTubeKioskSession =
        YouTubeKioskSession(
            ServiceList.YouTube.kioskList.getExtractorById(kioskId, null).withAppLocale(),
        )

    fun playlist(url: String): YouTubePlaylistSession =
        YouTubePlaylistSession(ServiceList.YouTube.getPlaylistExtractor(url).withAppLocale())

    suspend fun channel(url: String): ChannelHeader = channelPage(url).header

    suspend fun channelAbout(url: String): ChannelAbout =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val target = url.trimEnd('/') + "/about"
                val localizedTarget =
                    target.toHttpUrlOrNull()
                        ?.newBuilder()
                        ?.addQueryParameter("hl", NewPipe.getPreferredLocalization().languageCode)
                        ?.addQueryParameter("gl", NewPipe.getPreferredContentCountry().countryCode)
                        ?.build()
                        ?: target.toHttpUrl()
                val request = okhttp3.Request.Builder().url(localizedTarget).build()
                val html = com.m1xamm.mxtube.data.network.Http.client.newCall(request).execute().use {
                    it.body.string()
                }
                val country =
                    Regex("\"country\":\\{\"simpleText\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                        ?: Regex("\"country\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                val joined =
                    Regex("\"joinedDateText\":\\{\"content\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                        ?.let { raw ->
                            raw.substringAfter(": ", raw)
                                .removePrefix("Канал створено ")
                                .removePrefix("Channel created ")
                                .removePrefix("Joined ")
                        }
                val views =
                    Regex("\"viewCountText\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                        ?.let { ViewCountCorrector.parseViewCount(it)?.toString() }
                val links =
                    Regex("\"title\":\\{\"content\":\"([^\"]+)\"\\},\"link\":\\{\"content\":\"([^\"]+)\"")
                        .findAll(html)
                        .map { ChannelLink(it.groupValues[1], it.groupValues[2]) }
                        .distinctBy { it.url }
                        .toList()
                ChannelAbout(country, joined, views, links)
            }.getOrDefault(ChannelAbout())
        }

    /**
     * The tab handlers have to come from the channel response: a tab url built by hand makes
     * YouTube answer the browse request with INVALID_ARGUMENT.
     */
    suspend fun channelPage(url: String): ChannelPage =
        withExtractorContext {
            val extractor = ServiceList.YouTube.getChannelExtractor(url).withAppLocale()
            extractor.fetchPage()

            val fallback = ChannelIdentity.from(extractor.url) ?: ChannelIdentity.from(url)
            val channelId =
                runCatching { ChannelIdentity.idFromFeedUrl(extractor.feedUrl) }
                    .getOrNull()
                    ?: fallback?.key?.takeIf(ChannelIdentity::isChannelId)

            val header =
                ChannelHeader(
                    key = channelId ?: fallback?.key.orEmpty(),
                    url = channelId?.let(ChannelIdentity::canonicalUrl) ?: fallback?.url.orEmpty(),
                    name = extractor.name.orEmpty(),
                    avatarUrl = runCatching { extractor.avatars.bestUrl() }.getOrNull(),
                    bannerUrl = runCatching { extractor.banners.bestUrl() }.getOrNull(),
                    subscriberCount = ViewCountCorrector.correctSubscribers(
                        channelId ?: fallback?.key.orEmpty(),
                        runCatching { extractor.subscriberCount }.getOrDefault(ChannelExtractor.UNKNOWN_SUBSCRIBER_COUNT),
                    ),
                    description = runCatching { extractor.description.orEmpty() }.getOrDefault(""),
                    handle = ViewCountCorrector.channelHandle(channelId ?: fallback?.key.orEmpty()),
                    videoCount = ViewCountCorrector.channelVideoCount(channelId ?: fallback?.key.orEmpty()),
                    isVerified = runCatching { extractor.isVerified }.getOrDefault(false),
                )

            ChannelPage(header, extractor.service, extractor.tabs)
        }

    suspend fun related(url: String): List<VideoSummary> =
        withExtractorContext {
            val extractor = ServiceList.YouTube.getStreamExtractor(url).withAppLocale()
            extractor.fetchPage()
            extractor.relatedItems?.items?.videos().orEmpty()
        }

    suspend fun resolve(url: String): PlaybackInfo {
        try {
            return newPipeResolve(url)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(LOG_TAG, "newpipe resolve failed, trying invidious: $url", error)
            return InvidiousFallback.resolve(url, string(R.string.player_audio_original))
                ?: throw error
        }
    }

    private suspend fun newPipeResolve(url: String): PlaybackInfo =
        withExtractorContext {
            val extractor = ServiceList.YouTube.getStreamExtractor(url).withAppLocale()
            extractor.fetchPage()

            val muxed = extractor.videoStreams.filter { !it.content.isNullOrBlank() }
            val videoOnly = extractor.videoOnlyStreams.filter { !it.content.isNullOrBlank() }
            val audio = extractor.audioStreams.filter { !it.content.isNullOrBlank() }
            val audioTracks = buildAudioTracks(audio)
            val adaptive = buildAdaptiveQualities(videoOnly, audioTracks.isNotEmpty())
            val qualities = adaptive.ifEmpty { buildMuxedQualities(muxed) }

            check(qualities.isNotEmpty()) { "YouTube returned no playable streams" }

            val selectableAudio = if (adaptive.isNotEmpty()) audioTracks else emptyList()

            PlaybackInfo(
                title = extractor.name.orEmpty(),
                uploader = extractor.uploaderName.orEmpty(),
                viewCount = extractor.viewCount,
                uploadedAtEpochMillis = extractor.uploadDate?.instant?.toEpochMilli(),
                thumbnailUrl = extractor.thumbnails.bestUrl(),
                durationSeconds = extractor.length,
                qualities = qualities,
                defaultQualityIndex = defaultQualityIndex(qualities),
                audioTracks = selectableAudio,
                defaultAudioTrackIndex = if (selectableAudio.isEmpty()) -1 else 0,
                subtitles = buildSubtitles(extractor),
                related =
                    runCatching { extractor.relatedItems?.items?.videos().orEmpty() }
                        .getOrDefault(emptyList()),
                uploaderUrl = extractor.uploaderUrl,
                uploaderAvatarUrl = runCatching { extractor.uploaderAvatars.bestUrl() }.getOrNull(),
            )
        }

    private fun buildAdaptiveQualities(
        videoOnly: List<VideoStream>,
        hasAudioTracks: Boolean,
    ): List<PlaybackQuality> {
        if (!hasAudioTracks) return emptyList()

        val available = videoOnly.filter { it.height > 0 }
        val candidates = available.filter { it.height <= 1080 }.ifEmpty { available }

        return candidates
            .groupBy { it.height to it.fps }
            .mapNotNull { (_, streams) ->
                val video =
                    streams.maxWithOrNull(
                        compareBy<VideoStream> { it.codec.contains("avc", ignoreCase = true) }
                            .thenBy { it.bitrate },
                    ) ?: return@mapNotNull null

                PlaybackQuality(
                    label = qualityLabel(video.height, video.fps),
                    height = video.height,
                    fps = video.fps,
                    videoUrl = video.content.orEmpty(),
                    videoMimeType = video.format?.mimeType ?: "video/mp4",
                )
            }
            .sortedWith(compareByDescending<PlaybackQuality> { it.height }.thenByDescending { it.fps })
    }

    private fun buildMuxedQualities(muxed: List<VideoStream>): List<PlaybackQuality> =
        muxed
            .filter { it.height > 0 }
            .groupBy { it.height to it.fps }
            .mapNotNull { (_, streams) ->
                val video = streams.maxByOrNull { it.bitrate } ?: return@mapNotNull null
                PlaybackQuality(
                    label = qualityLabel(video.height, video.fps),
                    height = video.height,
                    fps = video.fps,
                    videoUrl = video.content.orEmpty(),
                    videoMimeType = video.format?.mimeType ?: "video/mp4",
                )
            }
            .sortedWith(compareByDescending<PlaybackQuality> { it.height }.thenByDescending { it.fps })

    private fun buildAudioTracks(audio: List<AudioStream>): List<PlaybackAudioTrack> {
        val tracks =
            audio
                .groupBy { stream ->
                    stream.audioTrackId?.takeIf { it.isNotBlank() }
                        ?: listOf(
                            stream.audioTrackName.orEmpty(),
                            stream.audioLocale?.toLanguageTag().orEmpty(),
                            stream.audioTrackType?.name.orEmpty(),
                        ).joinToString("|")
                }
                .mapNotNull { (id, streams) ->
                    val best =
                        streams.maxWithOrNull(
                            compareBy<AudioStream> { it.format == MediaFormat.M4A }
                                .thenBy { it.averageBitrate },
                        ) ?: return@mapNotNull null
                    val locale = streams.firstNotNullOfOrNull { it.audioLocale }
                    val name = streams.firstNotNullOfOrNull { it.audioTrackName?.takeIf(String::isNotBlank) }
                    val label =
                        locale?.let(::localeLabel)?.takeIf { it.isNotBlank() }
                            ?: name
                            ?: string(R.string.audio_track_fallback)
                    PlaybackAudioTrack(
                        id = id,
                        label = label,
                        languageTag = locale?.toLanguageTag(),
                        isOriginal = streams.any { it.audioTrackType == AudioTrackType.ORIGINAL },
                        url = best.content.orEmpty(),
                        mimeType = best.format?.mimeType ?: "audio/mp4",
                    )
                }

        val labelCounts = mutableMapOf<String, Int>()
        val labeledTracks =
            tracks.map { track ->
                val count = labelCounts[track.label] ?: 0
                labelCounts[track.label] = count + 1
                if (count == 0) track else track.copy(label = "${track.label} · ${count + 1}")
            }
        val original = labeledTracks.firstOrNull { it.isOriginal }
        return if (original == null) {
            labeledTracks
        } else {
            listOf(original) + labeledTracks.filter { it !== original }
        }
    }

    private fun buildSubtitles(extractor: StreamExtractor): List<PlaybackSubtitle> =
        runCatching { extractor.getSubtitles(MediaFormat.VTT) }
            .getOrDefault(emptyList())
            .mapNotNull { stream ->
                val languageTag =
                    stream.languageTag?.trim()?.takeIf { it.isNotBlank() && it != "live_chat" }
                        ?: return@mapNotNull null
                val url = stream.content.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val localeLabel =
                    Locale.forLanguageTag(languageTag)
                        .let(::localeLabel)
                        .takeIf { it.isNotBlank() }
                PlaybackSubtitle(
                    id = "caption:$languageTag",
                    label =
                        (localeLabel ?: stream.displayLanguageName.takeIf { it.isNotBlank() }
                            ?: languageTag)
                            .replaceFirstChar { it.titlecase(locale()) },
                    languageTag = languageTag,
                    isAutoGenerated = stream.isAutoGenerated,
                    url = url,
                    mimeType = "text/vtt",
                )
            }
            .groupBy { it.languageTag.lowercase(Locale.ROOT) }
            .mapNotNull { (_, options) ->
                options.firstOrNull { !it.isAutoGenerated } ?: options.firstOrNull()
            }
            .sortedBy { it.isAutoGenerated }

    private fun localeLabel(locale: Locale): String {
        val appLocale = this.locale()
        val language = locale.getDisplayLanguage(appLocale)
        val region = locale.getDisplayCountry(appLocale)
        val base =
            when {
                language.isBlank() -> ""
                region.isBlank() -> language
                else -> "$language ($region)"
            }
        return base.replaceFirstChar { it.titlecase(appLocale) }
    }

    private fun defaultQualityIndex(qualities: List<PlaybackQuality>): Int {
        for (height in listOf(720, 1080, 480, 360)) {
            val index = qualities.indexOfFirst { it.height == height }
            if (index >= 0) return index
        }
        return 0
    }

    private fun qualityLabel(height: Int, fps: Int): String = if (fps > 30) "${height}p$fps" else "${height}p"
}

class YouTubeSearchSession internal constructor(
    private val extractor: SearchExtractor,
) {
    private var nextPage: Page? = null

    val hasMore: Boolean
        get() = nextPage != null

    suspend fun first(): SearchBatch {
        val (batch, next) = withExtractorContext {
            extractor.fetchPage()
            val page = extractor.initialPage
            val next = if (page.hasNextPage()) page.nextPage else null
            SearchBatch(videos = page.videos(), hasMore = next != null) to next
        }
        nextPage = next
        return batch
    }

    suspend fun loadMore(): SearchBatch {
        val page = nextPage ?: return SearchBatch(emptyList(), false)
        val (batch, next) = withExtractorContext {
            val result = extractor.getPage(page)
            val next = if (result.hasNextPage()) result.nextPage else null
            SearchBatch(videos = result.videos(), hasMore = next != null) to next
        }
        nextPage = next
        return batch
    }
}

class YouTubeKioskSession internal constructor(
    private val extractor: KioskExtractor<*>,
) {
    private var nextPage: Page? = null

    val hasMore: Boolean
        get() = nextPage != null

    suspend fun first(): SearchBatch {
        val (batch, next) = withExtractorContext {
            extractor.fetchPage()
            val page = extractor.initialPage
            val next = if (page.hasNextPage()) page.nextPage else null
            SearchBatch(videos = page.items.videos(), hasMore = next != null) to next
        }
        nextPage = next
        return batch
    }

    suspend fun loadMore(): SearchBatch {
        val page = nextPage ?: return SearchBatch(emptyList(), false)
        val (batch, next) = withExtractorContext {
            val result = extractor.getPage(page)
            val next = if (result.hasNextPage()) result.nextPage else null
            SearchBatch(videos = result.items.videos(), hasMore = next != null) to next
        }
        nextPage = next
        return batch
    }
}

class YouTubeChannelTabSession internal constructor(
    private val extractor: ChannelTabExtractor,
) {
    private var nextPage: Page? = null

    val hasMore: Boolean
        get() = nextPage != null

    suspend fun first(): ChannelTabBatch {
        val (batch, next) = withExtractorContext {
            extractor.fetchPage()
            val page = extractor.initialPage
            val next = if (page.hasNextPage()) page.nextPage else null
            page.toBatch(next != null) to next
        }
        nextPage = next
        return batch
    }

    suspend fun loadMore(): ChannelTabBatch {
        val page = nextPage ?: return ChannelTabBatch(hasMore = false)
        val (batch, next) = withExtractorContext {
            val result = extractor.getPage(page)
            val next = if (result.hasNextPage()) result.nextPage else null
            result.toBatch(next != null) to next
        }
        nextPage = next
        return batch
    }

    private fun InfoItemsPage<InfoItem>.toBatch(hasMore: Boolean): ChannelTabBatch =
        ChannelTabBatch(
            videos = items.videos(),
            playlists = items.playlists(),
            hasMore = hasMore,
        )
}

class YouTubePlaylistSession internal constructor(
    private val extractor: PlaylistExtractor,
) {
    private var nextPage: Page? = null

    suspend fun first(): SearchBatch {
        val (batch, next) = withExtractorContext {
            extractor.fetchPage()
            val page = extractor.initialPage
            val next = if (page.hasNextPage()) page.nextPage else null
            SearchBatch(page.items.videos(), next != null) to next
        }
        nextPage = next
        return batch
    }

    suspend fun loadMore(): SearchBatch {
        val page = nextPage ?: return SearchBatch(emptyList(), false)
        val (batch, next) = withExtractorContext {
            val result = extractor.getPage(page)
            val next = if (result.hasNextPage()) result.nextPage else null
            SearchBatch(result.items.videos(), next != null) to next
        }
        nextPage = next
        return batch
    }
}

class ChannelPage internal constructor(
    val header: ChannelHeader,
    private val service: StreamingService,
    private val tabHandlers: List<ListLinkHandler>,
) {
    val availableTabs: List<String> = tabHandlers.mapNotNull { it.contentFilters.firstOrNull() }

    fun tab(tab: String): YouTubeChannelTabSession? =
        tabHandlers
            .firstOrNull { handler ->
                handler.contentFilters.firstOrNull() == tab || handler.url.endsWith("/$tab")
            }
            ?.let { handler -> YouTubeChannelTabSession(service.getChannelTabExtractor(handler).withAppLocale()) }
}

internal fun List<InfoItem>.videos(): List<VideoSummary> =
    filterIsInstance<StreamInfoItem>()
        .filter { it.streamType == StreamType.VIDEO_STREAM && !it.isShortFormContent }
        .map { item ->
            VideoSummary(
                url = item.url,
                title = item.name.orEmpty(),
                uploader = item.uploaderName.orEmpty(),
                durationSeconds = item.duration,
                viewCount = ViewCountCorrector.correct(item.url, item.viewCount),
                uploadedAtEpochMillis = item.uploadDate?.instant?.toEpochMilli(),
                thumbnailUrl = item.thumbnails.bestUrl(),
                uploaderUrl = item.uploaderUrl,
                uploaderAvatarUrl = runCatching { item.uploaderAvatars.bestUrl() }.getOrNull()
                    ?: ViewCountCorrector.avatar(item.url),
            )
        }
        .distinctBy { it.url }

internal fun List<InfoItem>.playlists(): List<PlaylistSummary> =
    filterIsInstance<PlaylistInfoItem>()
        .map { item ->
            PlaylistSummary(
                url = item.url,
                name = item.name.orEmpty(),
                videoCount = item.streamCount,
                thumbnailUrl = item.thumbnails.bestUrl(),
                uploader = item.uploaderName.orEmpty(),
            )
        }
        .distinctBy { it.url }

private fun InfoItemsPage<InfoItem>.videos(): List<VideoSummary> = items.videos()

private fun List<org.schabi.newpipe.extractor.Image>.bestUrl(): String? =
    maxByOrNull { it.width.toLong() * it.height }?.url

private fun <T : Extractor> T.withAppLocale(): T {
    forceLocalization(NewPipe.getPreferredLocalization())
    forceContentCountry(NewPipe.getPreferredContentCountry())
    return this
}
