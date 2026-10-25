package com.m1xamm.mxtube.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale

private val UI_LOCALE = Locale.forLanguageTag("ru")

class YouTubeRepository {
    fun search(query: String): YouTubeSearchSession =
        YouTubeSearchSession(ServiceList.YouTube.getSearchExtractor(query))

    fun kiosk(kioskId: String): YouTubeKioskSession =
        YouTubeKioskSession(ServiceList.YouTube.kioskList.getExtractorById(kioskId, null))

    suspend fun related(url: String): List<VideoSummary> =
        withContext(Dispatchers.IO) {
            val extractor = ServiceList.YouTube.getStreamExtractor(url)
            extractor.fetchPage()
            extractor.relatedItems?.items?.videos().orEmpty()
        }

    suspend fun resolve(url: String): PlaybackInfo =
        withContext(Dispatchers.IO) {
            val extractor = ServiceList.YouTube.getStreamExtractor(url)
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
                            ?: "Аудиодорожка"
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
                            .replaceFirstChar { it.titlecase(UI_LOCALE) },
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
        val language = locale.getDisplayLanguage(UI_LOCALE)
        val region =
            when (locale.country.uppercase(Locale.ROOT)) {
                "US" -> "США"
                "GB" -> "Великобритания"
                else -> locale.getDisplayCountry(UI_LOCALE)
            }
        val base =
            when {
                language.isBlank() -> ""
                region.isBlank() -> language
                else -> "$language ($region)"
            }
        return base.replaceFirstChar { it.titlecase(UI_LOCALE) }
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

    suspend fun first(): SearchBatch =
        withContext(Dispatchers.IO) {
            extractor.fetchPage()
            val page = extractor.initialPage
            nextPage = if (page.hasNextPage()) page.nextPage else null
            SearchBatch(videos = page.videos(), hasMore = hasMore)
        }

    suspend fun loadMore(): SearchBatch =
        withContext(Dispatchers.IO) {
            val page = nextPage ?: return@withContext SearchBatch(emptyList(), false)
            val result = extractor.getPage(page)
            nextPage = if (result.hasNextPage()) result.nextPage else null
            SearchBatch(videos = result.videos(), hasMore = hasMore)
        }
}

class YouTubeKioskSession internal constructor(
    private val extractor: KioskExtractor<*>,
) {
    private var nextPage: Page? = null

    val hasMore: Boolean
        get() = nextPage != null

    suspend fun first(): SearchBatch =
        withContext(Dispatchers.IO) {
            extractor.fetchPage()
            val page = extractor.initialPage
            nextPage = if (page.hasNextPage()) page.nextPage else null
            SearchBatch(videos = page.items.videos(), hasMore = hasMore)
        }

    suspend fun loadMore(): SearchBatch =
        withContext(Dispatchers.IO) {
            val page = nextPage ?: return@withContext SearchBatch(emptyList(), false)
            val result = extractor.getPage(page)
            nextPage = if (result.hasNextPage()) result.nextPage else null
            SearchBatch(videos = result.items.videos(), hasMore = hasMore)
        }
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
                viewCount = item.viewCount,
                uploadedAtEpochMillis = item.uploadDate?.instant?.toEpochMilli(),
                thumbnailUrl = item.thumbnails.bestUrl(),
            )
        }
        .distinctBy { it.url }

private fun InfoItemsPage<InfoItem>.videos(): List<VideoSummary> = items.videos()

private fun List<org.schabi.newpipe.extractor.Image>.bestUrl(): String? =
    maxByOrNull { it.width.toLong() * it.height }?.url
