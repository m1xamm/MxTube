package com.m1xamm.mxtube.data.home

import com.m1xamm.mxtube.data.local.HistoryState
import com.m1xamm.mxtube.data.youtube.VideoSummary

object HomeFeedEngine {
    fun rankInitial(
        history: HistoryState,
        relatedBySeed: Map<String, List<VideoSummary>>,
        discovery: List<HomeFeedItem>,
        limit: Int = 16,
    ): List<HomeFeedItem> {
        val recent = history.watches.take(5)
        val affinity = HomeTopics.affinity(history)
        val watched = history.watches.take(50).map { it.url }.toSet()
        val candidates = linkedMapOf<String, Candidate>()
        val discoveryUrls = mutableSetOf<String>()
        var order = 0

        recent.forEachIndexed { seedIndex, seed ->
            val videos = relatedBySeed[seed.url].orEmpty()
            videos.take(6).forEachIndexed { index, video ->
                if (video.url in watched) return@forEachIndexed
                val topics =
                    seed.topics +
                        HomeTopics.inferBaseTopics("${video.title} ${video.uploader}")
                val score =
                    (5 - seedIndex) * 10f +
                        (6 - index) +
                        HomeTopics.affinitySum(topics, affinity) * 3f
                mergeCandidate(candidates, video, topics, score, order++)
            }
        }

        discovery
            .asSequence()
            .filter { it.video.url !in watched }
            .forEach { item ->
                if (!discoveryUrls.add(item.video.url)) return@forEach
                val topics =
                    item.topics +
                        HomeTopics.inferBaseTopics("${item.video.title} ${item.video.uploader}")
                val affinityScore = HomeTopics.affinitySum(topics, affinity)
                val score =
                    4f +
                        affinityScore * 3f +
                        if (affinityScore == 0f) 7f else 0f
                mergeCandidate(candidates, item.video, topics, score, order++)
            }

        val result = mutableListOf<HomeFeedItem>()
        val seen = mutableSetOf<String>()
        val uploaders = mutableMapOf<String, Int>()

        fun add(candidate: Candidate, ignoreUploaderLimit: Boolean = false) {
            if (result.size >= limit || !seen.add(candidate.video.url)) return
            val uploader = candidate.video.uploader.lowercase()
            val count = uploaders[uploader] ?: 0
            if (!ignoreUploaderLimit && count >= 2) return
            uploaders[uploader] = count + 1
            result += HomeFeedItem(candidate.video, candidate.topics)
        }

        val exploreUrls =
            candidates.values
                .filter {
                    it.video.url in discoveryUrls &&
                        HomeTopics.affinitySum(it.topics, affinity) == 0f
                }
                .map { it.video.url }
                .toSet()
        val normal =
            candidates.values
                .filter { it.video.url !in exploreUrls }
                .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.order })
        val explore =
            candidates.values
                .filter { it.video.url in exploreUrls }
                .sortedBy { it.order }

        val normalIterator = normal.iterator()
        val exploreIterator = explore.iterator()
        var nextNormal = if (normalIterator.hasNext()) normalIterator.next() else null
        var nextExplore = if (exploreIterator.hasNext()) exploreIterator.next() else null
        var position = 0

        while (result.size < limit && (nextNormal != null || nextExplore != null)) {
            val preferExplore = position % 5 == 3
            val useExplore =
                (preferExplore && nextExplore != null) || nextNormal == null
            val candidate = if (useExplore) nextExplore else nextNormal
            if (candidate == null) break
            add(candidate)
            if (useExplore) {
                nextExplore = if (exploreIterator.hasNext()) exploreIterator.next() else null
            } else {
                nextNormal = if (normalIterator.hasNext()) normalIterator.next() else null
            }
            position++
        }

        if (result.size < limit) {
            candidates.values
                .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.order })
                .forEach { add(it, ignoreUploaderLimit = true) }
        }

        return result
    }

    private fun mergeCandidate(
        candidates: MutableMap<String, Candidate>,
        video: VideoSummary,
        topics: Set<String>,
        score: Float,
        order: Int,
    ) {
        val existing = candidates[video.url]
        if (existing == null) {
            candidates[video.url] = Candidate(video, topics, score, order)
        } else {
            candidates[video.url] =
                existing.copy(
                    topics = existing.topics + topics,
                    score = maxOf(existing.score, score),
                )
        }
    }

    private data class Candidate(
        val video: VideoSummary,
        val topics: Set<String>,
        val score: Float,
        val order: Int,
    )
}
