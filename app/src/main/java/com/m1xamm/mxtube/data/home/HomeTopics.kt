package com.m1xamm.mxtube.data.home

import com.m1xamm.mxtube.data.local.HistoryState
import java.util.Locale

object HomeTopics {
    val all =
        HomeTopic(
            id = "all",
            label = "Все",
            kind = HomeTopicKind.Personalized,
        )

    val base: List<HomeTopic> =
        listOf(
            HomeTopic(
                id = "music",
                label = "Музыка",
                kind = HomeTopicKind.Kiosk,
                kioskId = "trending_music",
                keywords = listOf("музыка", "music", "песня", "song", "album", "artist", "клип"),
            ),
            HomeTopic(
                id = "gaming",
                label = "Игры",
                kind = HomeTopicKind.Kiosk,
                kioskId = "trending_gaming",
                keywords =
                    listOf(
                        "игр", "game", "gaming", "gameplay", "прохожд", "стрим", "minecraft",
                        "roblox", "fortnite", "dota", "cs2", "gta",
                    ),
            ),
            HomeTopic(
                id = "movies",
                label = "Фильмы",
                kind = HomeTopicKind.Kiosk,
                kioskId = "trending_movies_and_shows",
                keywords =
                    listOf("фильм", "кино", "movie", "trailer", "трейлер", "сериал", "cinema"),
            ),
            HomeTopic(
                id = "news",
                label = "Новости",
                kind = HomeTopicKind.Search,
                query = "новости",
                keywords = listOf("новост", "news", "событ"),
            ),
            HomeTopic(
                id = "sport",
                label = "Спорт",
                kind = HomeTopicKind.Search,
                query = "спорт",
                keywords =
                    listOf(
                        "спорт", "sport", "футбол", "хоккей", "матч", "бокс", "ufc", "nba", "nhl",
                    ),
            ),
            HomeTopic(
                id = "science",
                label = "Наука",
                kind = HomeTopicKind.Search,
                query = "наука",
                keywords =
                    listOf("наук", "science", "космос", "space", "исследован", "физик", "биолог"),
            ),
            HomeTopic(
                id = "tech",
                label = "Технологии",
                kind = HomeTopicKind.Search,
                query = "технологии",
                keywords =
                    listOf(
                        "технолог", "tech", "обзор", "review", "смартфон", "iphone", "android",
                        "гаджет",
                    ),
            ),
            HomeTopic(
                id = "auto",
                label = "Авто",
                kind = HomeTopicKind.Search,
                query = "авто",
                keywords = listOf("авто", "car", "машин", "drive", "tuning", "мото"),
            ),
            HomeTopic(
                id = "travel",
                label = "Путешествия",
                kind = HomeTopicKind.Search,
                query = "путешествия",
                keywords = listOf("путешеств", "travel", "тур", "trip", "отдых"),
            ),
        )

    private val baseById = base.associateBy { it.id }
    private val baseLabels = (base.map { it.label } + all.label).map { it.lowercase(Locale.ROOT) }
    private val baseQueries = base.mapNotNull { it.query?.lowercase(Locale.ROOT) }.toSet()

    private val blockedWords =
        setOf(
            "порно", "секс", "интим", "наркот", "суицид", "диагноз", "болезн", "зарплат",
            "пароль", "логин", "почта", "паспорт", "адрес",
        )
    private val questionWords =
        setOf(
            "как", "почему", "зачем", "что", "где", "когда", "кто", "сколько", "можно",
            "стоит", "лучше", "какой", "какая", "какие",
        )
    private val selfWords = setOf("я", "мне", "меня", "мой", "моя", "мое", "мои", "себя")

    fun ordered(history: HistoryState): List<HomeTopic> {
        val baseScores = baseScores(history)
        val dynamics = dynamicTopics(history)
        val scoredBase =
            base.mapIndexed { index, topic ->
                ScoredTopic(topic, baseScores[topic.id] ?: 0f, index)
            }
        val scoredDynamic = dynamics.map { ScoredTopic(it.topic, it.score, base.size + it.index) }
        val orderedRest =
            (scoredBase + scoredDynamic)
                .sortedWith(
                    compareByDescending<ScoredTopic> { it.score }
                        .thenBy { it.order },
                )
                .map { it.topic }
        return listOf(all) + orderedRest
    }

    fun initialTopics(): List<HomeTopic> = listOf(all) + base

    fun find(
        history: HistoryState,
        id: String,
    ): HomeTopic? = ordered(history).firstOrNull { it.id == id }

    fun affinity(history: HistoryState): Map<String, Float> = baseScores(history)

    fun affinitySum(
        topics: Set<String>,
        affinity: Map<String, Float>,
    ): Float = topics.sumOf { topic -> (affinity[topic] ?: 0f).toDouble() }.toFloat()

    fun inferBaseTopics(text: String): Set<String> {
        val normalized = text.lowercase(Locale.ROOT)
        return base
            .asSequence()
            .filter { topic -> topic.keywords.any { normalized.contains(it) } }
            .map { it.id }
            .toSet()
    }

    fun topicsFor(
        sourceTopicId: String?,
        title: String,
        uploader: String,
        query: String? = null,
    ): Set<String> {
        val topics = mutableSetOf<String>()
        if (!sourceTopicId.isNullOrBlank() && sourceTopicId != all.id) {
            topics += sourceTopicId
        }
        topics += inferBaseTopics("$title $uploader")
        if (!query.isNullOrBlank()) {
            topics += inferBaseTopics(query)
        }
        return topics
    }

    fun isBaseTopic(id: String): Boolean = baseById.containsKey(id)

    private fun baseScores(history: HistoryState): Map<String, Float> {
        val scores = mutableMapOf<String, Float>()
        history.watches.forEachIndexed { index, entry ->
            val recency = (1f - index * 0.12f).coerceAtLeast(0.3f)
            entry.topics
                .filter(::isBaseTopic)
                .forEach { topic ->
                    scores[topic] = (scores[topic] ?: 0f) + recency
                }
            inferBaseTopics("${entry.title} ${entry.uploader}").forEach { topic ->
                scores[topic] = (scores[topic] ?: 0f) + recency * 0.4f
            }
        }
        history.searches.forEachIndexed { index, entry ->
            val recency = (1f - index * 0.08f).coerceAtLeast(0.3f)
            inferBaseTopics(entry.query).forEach { topic ->
                scores[topic] = (scores[topic] ?: 0f) + recency * 0.5f
            }
        }
        return scores
    }

    private fun dynamicTopics(history: HistoryState): List<DynamicTopic> {
        val groups = history.searches.groupBy { normalize(it.query) }
        return groups
            .mapNotNull { (normalized, entries) ->
                val totalCount = entries.sumOf { it.count }
                if (totalCount < 2) return@mapNotNull null
                if (!isDynamicQueryAllowed(normalized)) return@mapNotNull null
                val latest = entries.first()
                val watchBoost = history.watches.count { normalize(it.sourceQuery.orEmpty()) == normalized }
                DynamicTopic(
                    topic =
                        HomeTopic(
                            id = "dynamic:$normalized",
                            label = latest.query.trim().replaceFirstChar { it.titlecase(Locale.ROOT) },
                            kind = HomeTopicKind.Search,
                            query = latest.query.trim(),
                            keywords = normalized.split(" "),
                            isDynamic = true,
                        ),
                    score = totalCount * 1.5f + watchBoost,
                    index = 0,
                )
            }
            .sortedByDescending { it.score }
            .take(MAX_DYNAMIC_TOPICS)
            .mapIndexed { index, dynamic -> dynamic.copy(index = index) }
    }

    private fun isDynamicQueryAllowed(normalized: String): Boolean {
        if (normalized.isBlank() || normalized.length < 3 || normalized.length > 32) return false
        if (normalized.contains("http") || normalized.contains("www.") || normalized.contains("@")) {
            return false
        }
        if (normalized.contains("?") || normalized.any { it.isDigit() } && normalized.none { it.isLetter() }) {
            return false
        }
        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 3) return false
        if (words.any { word -> blockedWords.any { word.contains(it) } }) return false
        if (words.first() in questionWords || words.first() in selfWords) return false
        if (words.any { it in selfWords }) return false
        if (normalized in baseLabels || normalized in baseQueries) return false
        return normalized.any { it.isLetter() }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private data class ScoredTopic(
        val topic: HomeTopic,
        val score: Float,
        val order: Int,
    )

    private data class DynamicTopic(
        val topic: HomeTopic,
        val score: Float,
        val index: Int,
    )

    private const val MAX_DYNAMIC_TOPICS = 8
}
