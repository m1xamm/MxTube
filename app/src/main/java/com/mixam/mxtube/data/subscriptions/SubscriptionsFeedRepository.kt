package com.mixam.mxtube.data.subscriptions

import com.mixam.mxtube.data.local.SubscriptionEntry
import com.mixam.mxtube.data.youtube.ChannelTabBatch
import com.mixam.mxtube.data.youtube.VideoSummary
import com.mixam.mxtube.data.youtube.YouTubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import java.io.IOException

internal const val MAX_SUBSCRIPTIONS_FEED_VIDEOS = 300
private const val PER_CHANNEL_FALLBACK = 8

internal fun mergeSubscriptionsVideos(
    videos: Iterable<List<VideoSummary>>,
    limit: Int = MAX_SUBSCRIPTIONS_FEED_VIDEOS,
): List<VideoSummary> =
    videos.asSequence()
        .flatten()
        .filter { it.url.isNotBlank() }
        .sortedWith(
            compareByDescending<VideoSummary> { it.uploadedAtEpochMillis ?: Long.MIN_VALUE }
                .thenBy { it.url },
        )
        .distinctBy { it.url }
        .take(limit.coerceAtLeast(0))
        .toList()

internal fun boundSubscriptionsVideos(
    videosByChannel: Map<String, List<VideoSummary>>,
): Map<String, List<VideoSummary>> {
    val channels = videosByChannel.entries.filter { it.key.isNotBlank() }.take(300)
    val ordered =
        channels.asSequence()
            .flatMap { (key, videos) -> videos.asSequence().map { key to it } }
            .filter { it.second.url.isNotBlank() }
            .sortedWith(
                compareByDescending<Pair<String, VideoSummary>> {
                    it.second.uploadedAtEpochMillis ?: Long.MIN_VALUE
                }.thenBy { it.second.url }.thenBy { it.first },
            )
            .distinctBy { it.first to it.second.url }
            .toList()
    val latest =
        ordered.take(MAX_SUBSCRIPTIONS_FEED_VIDEOS)
            .groupBy({ it.first }, { it.second })
            .toMutableMap()
    ordered.groupBy({ it.first }, { it.second }).forEach { (key, videos) ->
        if (latest[key].isNullOrEmpty()) latest[key] = videos.take(PER_CHANNEL_FALLBACK)
    }
    return channels.associate { it.key to latest[it.key].orEmpty() }
}

data class SubscriptionFeedFailure(
    val cause: Exception,
    val isLoadMore: Boolean,
)

data class SubscriptionsFeedSnapshot(
    val videosByChannel: Map<String, List<VideoSummary>>,
    val failures: Map<String, SubscriptionFeedFailure>,
    val successfulChannelKeys: Set<String>,
    val moreChannelKeys: Set<String>,
) {
    fun videos(channelKey: String? = null): List<VideoSummary> =
        mergeSubscriptionsVideos(
            if (channelKey == null) videosByChannel.values
            else listOf(videosByChannel[channelKey].orEmpty()),
        )

    fun hasMore(channelKey: String? = null): Boolean =
        videosByChannel.values.sumOf { it.size } < MAX_SUBSCRIPTIONS_FEED_VIDEOS &&
            if (channelKey == null) moreChannelKeys.isNotEmpty() else channelKey in moreChannelKeys
}

internal interface SubscriptionChannelSession {
    suspend fun first(): ChannelTabBatch
    suspend fun loadMore(): ChannelTabBatch
}

class SubscriptionsFeedRepository internal constructor(
    private val openChannel: suspend (SubscriptionEntry) -> SubscriptionChannelSession,
) {
    constructor(youtube: YouTubeRepository = YouTubeRepository()) : this(
        openChannel = { entry ->
            val page = youtube.channelPage(entry.url)
            val tab = page.tab(ChannelTabs.VIDEOS)
            object : SubscriptionChannelSession {
                override suspend fun first(): ChannelTabBatch =
                    (tab?.first() ?: ChannelTabBatch(hasMore = false)).withUploader()

                override suspend fun loadMore(): ChannelTabBatch =
                    (tab?.loadMore() ?: ChannelTabBatch(hasMore = false)).withUploader()

                private fun ChannelTabBatch.withUploader(): ChannelTabBatch =
                    copy(videos = videos.map { video ->
                        video.copy(uploader = video.uploader.ifBlank { page.header.name.ifBlank { entry.name } })
                    })
            }
        },
    )

    private val semaphore = Semaphore(4)

    fun open(
        channels: List<SubscriptionEntry>,
        cachedVideos: Map<String, List<VideoSummary>> = emptyMap(),
    ): SubscriptionsFeedSession = SubscriptionsFeedSession(channels, cachedVideos, openChannel, semaphore)
}

class SubscriptionsFeedSession internal constructor(
    channels: List<SubscriptionEntry>,
    cachedVideos: Map<String, List<VideoSummary>>,
    private val openChannel: suspend (SubscriptionEntry) -> SubscriptionChannelSession,
    private val semaphore: Semaphore,
) {
    private class Channel(val entry: SubscriptionEntry) {
        var session: SubscriptionChannelSession? = null
        var hasMore = false
        var failure: SubscriptionFeedFailure? = null
    }

    private val channels = channels.distinctBy { it.key }.take(300).associate { it.key to Channel(it) }
    private val operationMutex = Mutex()
    private val resultMutex = Mutex()
    private var videosByChannel =
        boundSubscriptionsVideos(this.channels.mapValues { (key, _) -> cachedVideos[key].orEmpty() })

    suspend fun refresh(): SubscriptionsFeedSnapshot = operationMutex.withLock {
        channels.values.forEach {
            it.session = null
            it.hasMore = false
            it.failure = null
        }
        fetch(channels.values.toList(), loadMore = false)
    }

    suspend fun retryFailed(): SubscriptionsFeedSnapshot = operationMutex.withLock {
        fetch(channels.values.filter { it.failure != null }, loadMore = null)
    }

    suspend fun loadMore(channelKey: String? = null): SubscriptionsFeedSnapshot = operationMutex.withLock {
        val targets =
            if (videosByChannel.values.sumOf { it.size } >= MAX_SUBSCRIPTIONS_FEED_VIDEOS) emptyList()
            else channels.values.filter {
                (channelKey == null || it.entry.key == channelKey) && it.session != null && it.hasMore
            }
        fetch(targets, loadMore = true)
    }

    suspend fun loadChannel(channelKey: String): SubscriptionsFeedSnapshot = operationMutex.withLock {
        val channel = channels[channelKey] ?: return@withLock snapshot()
        fetch(listOf(channel), loadMore = false)
    }

    private fun snapshot(): SubscriptionsFeedSnapshot =
        SubscriptionsFeedSnapshot(
            videosByChannel = videosByChannel,
            failures = channels.mapNotNull { (key, channel) -> channel.failure?.let { key to it } }.toMap(),
            successfulChannelKeys = emptySet(),
            moreChannelKeys = channels.filterValues { it.session != null && it.hasMore }.keys.toSet(),
        )

    private suspend fun fetch(targets: List<Channel>, loadMore: Boolean?): SubscriptionsFeedSnapshot {
        val accumulated = videosByChannel.toMutableMap()
        val successful = mutableSetOf<String>()
        coroutineScope {
            targets.map { channel ->
                async {
                    semaphore.withPermit {
                        val more = loadMore ?: channel.failure?.isLoadMore ?: false
                        try {
                            val batch = withTimeoutOrNull(30_000L) {
                                if (more) {
                                    val session = channel.session ?: throw IOException("Missing channel session")
                                    session.loadMore()
                                } else {
                                    val session = openChannel(channel.entry)
                                    val first = session.first()
                                    channel.session = session
                                    first
                                }
                            } ?: throw IOException("Channel feed timed out")
                            val videos = batch.videos.map { video ->
                                video.copy(
                                    uploaderUrl = video.uploaderUrl?.takeIf { it.isNotBlank() } ?: channel.entry.url,
                                    uploader = video.uploader.ifBlank { channel.entry.name },
                                )
                            }
                            resultMutex.withLock {
                                val existing = if (more) accumulated[channel.entry.key].orEmpty() else emptyList()
                                accumulated[channel.entry.key] = existing + videos
                                channel.hasMore = batch.hasMore
                                channel.failure = null
                                successful.add(channel.entry.key)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            resultMutex.withLock {
                                channel.failure = SubscriptionFeedFailure(error, isLoadMore = more)
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        videosByChannel = boundSubscriptionsVideos(accumulated)
        return SubscriptionsFeedSnapshot(
            videosByChannel = videosByChannel,
            failures = channels.mapNotNull { (key, channel) -> channel.failure?.let { key to it } }.toMap(),
            successfulChannelKeys = successful.toSet(),
            moreChannelKeys = channels.filterValues { it.session != null && it.hasMore }.keys.toSet(),
        )
    }
}
