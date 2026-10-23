package com.m1xamm.mxtube.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

class YouTubeRepository {
    fun search(query: String): YouTubeSearchSession =
        YouTubeSearchSession(ServiceList.YouTube.getSearchExtractor(query))

    suspend fun resolve(url: String): PlaybackInfo =
        withContext(Dispatchers.IO) {
            val extractor = ServiceList.YouTube.getStreamExtractor(url)
            extractor.fetchPage()

            val muxed = extractor.videoStreams.filter { !it.content.isNullOrBlank() }
            val videoOnly = extractor.videoOnlyStreams.filter { !it.content.isNullOrBlank() }
            val audio = extractor.audioStreams.filter { !it.content.isNullOrBlank() }
            val qualities = buildQualities(muxed, videoOnly, audio)

            check(qualities.isNotEmpty()) { "YouTube returned no playable streams" }

            PlaybackInfo(
                title = extractor.name.orEmpty(),
                uploader = extractor.uploaderName.orEmpty(),
                viewCount = extractor.viewCount,
                uploadedAtEpochMillis = extractor.uploadDate?.instant?.toEpochMilli(),
                thumbnailUrl = extractor.thumbnails.bestUrl(),
                durationSeconds = extractor.length,
                qualities = qualities,
                defaultQualityIndex = defaultQualityIndex(qualities),
            )
        }

    private fun buildQualities(
        muxed: List<VideoStream>,
        videoOnly: List<VideoStream>,
        audio: List<AudioStream>,
    ): List<PlaybackQuality> {
        val bestAudio =
            audio
                .maxWithOrNull(
                    compareBy<AudioStream> { it.format == MediaFormat.M4A }.thenBy { it.averageBitrate },
                )?.takeIf { !it.content.isNullOrBlank() }

        if (bestAudio != null) {
            val available = videoOnly.filter { it.height > 0 }
            val candidates = available.filter { it.height <= 1080 }.ifEmpty { available }

            val adaptive =
                candidates
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
                            audioUrl = bestAudio.content,
                            audioMimeType = bestAudio.format?.mimeType ?: "audio/mp4",
                        )
                    }
                    .sortedWith(compareByDescending<PlaybackQuality> { it.height }.thenByDescending { it.fps })

            if (adaptive.isNotEmpty()) return adaptive
        }

        return muxed
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
                    audioUrl = null,
                    audioMimeType = null,
                )
            }
            .sortedWith(compareByDescending<PlaybackQuality> { it.height }.thenByDescending { it.fps })
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

private fun InfoItemsPage<InfoItem>.videos(): List<VideoSummary> =
    items
        .filterIsInstance<StreamInfoItem>()
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

private fun List<org.schabi.newpipe.extractor.Image>.bestUrl(): String? =
    maxByOrNull { it.width.toLong() * it.height }?.url
