package com.m1xamm.mxtube.data.home

import com.m1xamm.mxtube.data.local.HistoryState
import com.m1xamm.mxtube.data.youtube.SearchBatch
import com.m1xamm.mxtube.data.youtube.VideoSummary
import com.m1xamm.mxtube.data.youtube.YouTubeRepository
import com.m1xamm.mxtube.data.youtube.YouTubeSearchSession
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

interface HomeFeedSession {
    val initialItems: List<HomeFeedItem>
    val hasMore: Boolean

    suspend fun loadMore(): List<HomeFeedItem>
}

private fun interleave(lists: List<List<HomeFeedItem>>): List<HomeFeedItem> {
    val result = mutableListOf<HomeFeedItem>()
    val seen = mutableSetOf<String>()
    val maxSize = lists.maxOfOrNull { it.size } ?: 0
    for (index in 0 until maxSize) {
        lists.forEach { list ->
            list.getOrNull(index)?.let { item ->
                if (seen.add(item.video.url)) {
                    result += item
                }
            }
        }
    }
    return result
}

class HomeFeedRepository(
    private val youtube: YouTubeRepository = YouTubeRepository(),
) {
    suspend fun open(
        topic: HomeTopic,
        history: HistoryState,
    ): HomeFeedSession =
        when (topic.kind) {
            HomeTopicKind.Personalized -> openPersonalized(history)
            HomeTopicKind.Kiosk -> openKiosk(topic)
            HomeTopicKind.Search -> openSearch(topic)
        }

    private suspend fun openPersonalized(history: HistoryState): HomeFeedSession =
        coroutineScope {
            val recent = history.watches.take(5)
            val kioskTopics = HomeTopics.base.filter { it.kioskId != null }
            val discoverySearch = runCatching { youtube.search(DISCOVERY_QUERY) }.getOrNull()

            val searchDeferred =
                discoverySearch?.let { session -> async { runCatching { session.first() } } }
            val kioskDeferred =
                kioskTopics.map { topic ->
                    async {
                        topic to
                            runCatching {
                                youtube.kiosk(topic.kioskId!!).first()
                            }
                    }
                }
            val relatedDeferred =
                recent.map { seed ->
                    async {
                        seed.url to
                            runCatching { youtube.related(seed.url) }.getOrDefault(emptyList())
                    }
                }

            val searchBatch =
                searchDeferred?.await()?.getOrElse { SearchBatch(emptyList(), false) }
                    ?: SearchBatch(emptyList(), false)
            val kioskResults = kioskDeferred.awaitAll()
            val related = relatedDeferred.awaitAll().toMap()

            val discoveryLists =
                buildList {
                    kioskResults.forEach { (topic, result) ->
                        result.getOrNull()?.videos?.take(12)?.let { videos ->
                            add(
                                videos.map { video ->
                                    HomeFeedItem(
                                        video = video,
                                        topics =
                                            setOf(topic.id) +
                                                HomeTopics.inferBaseTopics(
                                                    "${video.title} ${video.uploader}",
                                                ),
                                    )
                                },
                            )
                        }
                    }
                    if (searchBatch.videos.isNotEmpty()) {
                        add(
                            searchBatch.videos.take(12).map { video ->
                                HomeFeedItem(
                                    video = video,
                                    topics =
                                        HomeTopics.inferBaseTopics(
                                            "${video.title} ${video.uploader}",
                                        ),
                                )
                            },
                        )
                    }
                }
            val discovery = interleave(discoveryLists)
            if (recent.isEmpty() && discovery.isEmpty() && related.values.all { it.isEmpty() }) {
                error("Домашняя лента пуста")
            }

            val items = HomeFeedEngine.rankInitial(history, related, discovery, limit = 32)
            val initialItems = items.take(16)
            val backlog = items.drop(16).toMutableList()
            val seen = history.watches.take(50).map { it.url }.toMutableSet()
            seen += initialItems.map { it.video.url }
            AllHomeFeedSession(
                discoverySession = discoverySearch?.takeIf { searchBatch.hasMore },
                initialItems = initialItems,
                initialHasMore = searchBatch.hasMore || backlog.isNotEmpty(),
                backlog = backlog,
                seen = seen,
            )
        }

    private suspend fun openKiosk(topic: HomeTopic): HomeFeedSession {
        val kioskId = topic.kioskId ?: error("Kiosk id is missing")
        val session = youtube.kiosk(kioskId)
        val batch = session.first()
        val items = topicItems(batch, topic)
        return PagedHomeFeedSession(
            initialItems = items,
            initialHasMore = batch.hasMore,
            topic = topic,
            loadPage = { session.loadMore() },
        )
    }

    private suspend fun openSearch(topic: HomeTopic): HomeFeedSession {
        val query = topic.query ?: error("Search query is missing")
        val session = youtube.search(query)
        val batch = session.first()
        val items = topicItems(batch, topic)
        return PagedHomeFeedSession(
            initialItems = items,
            initialHasMore = batch.hasMore,
            topic = topic,
            loadPage = { session.loadMore() },
        )
    }

    private fun topicItems(
        batch: SearchBatch,
        topic: HomeTopic,
    ): List<HomeFeedItem> =
        batch.videos.map { video ->
            HomeFeedItem(
                video = video,
                topics =
                    setOf(topic.id) +
                        HomeTopics.inferBaseTopics("${video.title} ${video.uploader}"),
            )
        }

    private class AllHomeFeedSession(
        private val discoverySession: YouTubeSearchSession?,
        override val initialItems: List<HomeFeedItem>,
        initialHasMore: Boolean,
        private val backlog: MutableList<HomeFeedItem>,
        private val seen: MutableSet<String>,
    ) : HomeFeedSession {
        private var more = initialHasMore
        override val hasMore: Boolean
            get() = more

        override suspend fun loadMore(): List<HomeFeedItem> {
            if (backlog.isNotEmpty()) {
                val chunk = backlog.take(8).toList()
                backlog.subList(0, chunk.size).clear()
                chunk.forEach { seen += it.video.url }
                more = backlog.isNotEmpty() || (discoverySession != null && discoverySession.hasMore)
                return chunk
            }
            val session = discoverySession
            if (!more || session == null) return emptyList()
            val batch = session.loadMore()
            more = batch.hasMore
            return batch.videos
                .filter { seen.add(it.url) }
                .map { video ->
                    HomeFeedItem(
                        video = video,
                        topics = HomeTopics.inferBaseTopics("${video.title} ${video.uploader}"),
                    )
                }
        }
    }

    private class PagedHomeFeedSession(
        override val initialItems: List<HomeFeedItem>,
        initialHasMore: Boolean,
        private val topic: HomeTopic,
        private val loadPage: suspend () -> SearchBatch,
    ) : HomeFeedSession {
        private var more = initialHasMore
        private val seen = initialItems.map { it.video.url }.toMutableSet()
        override val hasMore: Boolean
            get() = more

        override suspend fun loadMore(): List<HomeFeedItem> {
            if (!more) return emptyList()
            val batch = loadPage()
            more = batch.hasMore
            return batch.videos
                .filter { seen.add(it.url) }
                .map { video ->
                    HomeFeedItem(
                        video = video,
                        topics =
                            setOf(topic.id) +
                                HomeTopics.inferBaseTopics("${video.title} ${video.uploader}"),
                    )
                }
        }
    }

    private companion object {
        const val DISCOVERY_QUERY = "популярное"
    }
}
