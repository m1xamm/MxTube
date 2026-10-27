package com.m1xamm.mxtube.data.home

import android.content.Context
import com.m1xamm.mxtube.R
import com.m1xamm.mxtube.data.local.HistoryState
import java.util.Locale

object HomeTopics {
    private val BASE_DEFS =
        listOf(
            BaseTopicDef(
                id = "music",
                labelRes = R.string.topic_music,
                kind = HomeTopicKind.Kiosk,
                kioskId = "trending_music",
                keywords =
                    listOf(
                        "музыка", "музика", "music", "песня", "пісня", "song", "album",
                        "artist", "клип", "кліп", "гурт",
                    ),
            ),
            BaseTopicDef(
                id = "gaming",
                labelRes = R.string.topic_gaming,
                kind = HomeTopicKind.Kiosk,
                kioskId = "trending_gaming",
                keywords =
                    listOf(
                        "игр", "гра", "ігор", "game", "gaming", "gameplay", "прохожд",
                        "проходж", "стрим", "стрім", "minecraft", "roblox", "fortnite",
                        "dota", "cs2", "gta",
                    ),
            ),
            BaseTopicDef(
                id = "movies",
                labelRes = R.string.topic_movies,
                kind = HomeTopicKind.Kiosk,
                kioskId = "trending_movies_and_shows",
                keywords =
                    listOf(
                        "фильм", "фільм", "кино", "кіно", "movie", "trailer", "трейлер",
                        "сериал", "серіал", "cinema",
                    ),
            ),
            BaseTopicDef(
                id = "news",
                labelRes = R.string.topic_news,
                kind = HomeTopicKind.Search,
                queryRes = R.string.topic_query_news,
                keywords = listOf("новост", "новини", "новина", "news", "событ", "події"),
            ),
            BaseTopicDef(
                id = "sport",
                labelRes = R.string.topic_sport,
                kind = HomeTopicKind.Search,
                queryRes = R.string.topic_query_sport,
                keywords =
                    listOf(
                        "спорт", "sport", "футбол", "хоккей", "хокей", "матч", "бокс", "ufc",
                        "nba", "nhl",
                    ),
            ),
            BaseTopicDef(
                id = "science",
                labelRes = R.string.topic_science,
                kind = HomeTopicKind.Search,
                queryRes = R.string.topic_query_science,
                keywords =
                    listOf(
                        "наук", "наука", "science", "космос", "space", "исследован", "дослідж",
                        "физик", "фізик", "биолог", "біолог",
                    ),
            ),
            BaseTopicDef(
                id = "tech",
                labelRes = R.string.topic_tech,
                kind = HomeTopicKind.Search,
                queryRes = R.string.topic_query_tech,
                keywords =
                    listOf(
                        "технолог", "tech", "обзор", "огляд", "review", "смартфон", "iphone",
                        "android", "гаджет", "ґаджет",
                    ),
            ),
            BaseTopicDef(
                id = "auto",
                labelRes = R.string.topic_auto,
                kind = HomeTopicKind.Search,
                queryRes = R.string.topic_query_auto,
                keywords = listOf("авто", "car", "машин", "drive", "tuning", "мото"),
            ),
            BaseTopicDef(
                id = "travel",
                labelRes = R.string.topic_travel,
                kind = HomeTopicKind.Search,
                queryRes = R.string.topic_query_travel,
                keywords =
                    listOf(
                        "путешеств", "подорож", "travel", "тур", "trip", "отдых", "відпочинок",
                    ),
            ),
        )

    private val blockedWords =
        setOf(
            "порно", "секс", "интим", "наркот", "суицид", "диагноз", "болезн", "зарплат",
            "пароль", "логин", "почта", "паспорт", "адрес", "porn", "sex", "suicide",
            "password", "login", "email", "passport", "address", "суїцид", "логін",
            "пошта", "адреса",
        )

    private val questionWords =
        setOf(
            "как", "почему", "зачем", "что", "где", "когда", "кто", "сколько", "можно",
            "стоит", "лучше", "какой", "какая", "какие", "how", "why", "what", "where",
            "when", "who", "much", "should", "best", "which", "як", "чому", "навіщо", "що",
            "де", "коли", "хто", "скільки", "можна", "варто", "краще", "який", "яка", "які",
        )

    private val selfWords =
        setOf(
            "я", "мне", "меня", "мой", "моя", "мое", "мои", "себя", "i", "me", "my", "mine",
            "myself", "мені", "мене", "мій", "моя", "моє", "мої", "себе",
        )

    var all: HomeTopic =
        HomeTopic(
            id = "all",
            label = "All",
            kind = HomeTopicKind.Personalized,
        )
        private set

    var base: List<HomeTopic> = BASE_DEFS.map { it.fallbackTopic() }
        private set

    var discoveryQuery: String = "popular"
        private set

    private var baseById = base.associateBy { it.id }
    private var baseLabels = labelsOf(all, base)
    private var baseQueries = queriesOf(base)

    fun init(context: Context) {
        all =
            HomeTopic(
                id = "all",
                label = context.getString(R.string.topic_all),
                kind = HomeTopicKind.Personalized,
            )
        base =
            BASE_DEFS.map { def ->
                HomeTopic(
                    id = def.id,
                    label = context.getString(def.labelRes),
                    kind = def.kind,
                    kioskId = def.kioskId,
                    query = def.queryRes?.let(context::getString),
                    keywords = def.keywords,
                )
            }
        baseById = base.associateBy { it.id }
        baseLabels = labelsOf(all, base)
        baseQueries = queriesOf(base)
        discoveryQuery = context.getString(R.string.home_discovery_query)
    }

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

    private fun labelsOf(
        all: HomeTopic,
        base: List<HomeTopic>,
    ): Set<String> = (base.map { it.label } + all.label).map { it.lowercase(Locale.ROOT) }.toSet()

    private fun queriesOf(base: List<HomeTopic>): Set<String> =
        base.mapNotNull { it.query?.lowercase(Locale.ROOT) }.toSet()

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

    private data class BaseTopicDef(
        val id: String,
        val labelRes: Int,
        val kind: HomeTopicKind,
        val kioskId: String? = null,
        val queryRes: Int? = null,
        val keywords: List<String>,
    ) {
        fun fallbackTopic(): HomeTopic =
            HomeTopic(
                id = id,
                label = id,
                kind = kind,
                kioskId = kioskId,
                keywords = keywords,
            )
    }

    private const val MAX_DYNAMIC_TOPICS = 8
}
