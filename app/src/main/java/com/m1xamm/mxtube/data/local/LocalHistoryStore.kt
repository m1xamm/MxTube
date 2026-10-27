package com.m1xamm.mxtube.data.local

import android.content.Context
import com.m1xamm.mxtube.data.youtube.VideoSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LocalHistoryStore(context: Context) {
    private val file = File(context.filesDir, "mx_history.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(HistoryState())
    private var loaded = false

    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                ensureLoaded()
            }
        }
    }

    suspend fun snapshot(): HistoryState =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoaded()
                _state.value
            }
        }

    suspend fun recordWatch(
        video: VideoSummary,
        topics: Set<String>,
        sourceQuery: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        mutate { current ->
            val previous = current.watches.firstOrNull { it.url == video.url }
            val entry =
                WatchHistoryEntry(
                    url = video.url,
                    title = video.title,
                    uploader = video.uploader,
                    thumbnailUrl = video.thumbnailUrl,
                    durationSeconds = video.durationSeconds,
                    watchedAtEpochMillis = now,
                    progressMs = previous?.progressMs ?: 0L,
                    topics = topics + previous?.topics.orEmpty(),
                    sourceQuery = sourceQuery ?: previous?.sourceQuery,
                )
            current.copy(
                watches =
                    (listOf(entry) + current.watches.filterNot { it.url == video.url })
                        .take(MAX_WATCHES),
            )
        }
    }

    suspend fun updateWatchProgress(
        url: String,
        progressMs: Long,
    ) {
        mutate { current ->
            val watches =
                current.watches.map { entry ->
                    if (entry.url == url) {
                        entry.copy(progressMs = progressMs.coerceAtLeast(0L))
                    } else {
                        entry
                    }
                }
            current.copy(watches = watches)
        }
    }

    suspend fun recordSearch(
        query: String,
        now: Long = System.currentTimeMillis(),
    ) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        mutate { current ->
            val previous = current.searches.firstOrNull { it.query.equals(trimmed, true) }
            val entry =
                SearchHistoryEntry(
                    query = trimmed,
                    searchedAtEpochMillis = now,
                    count = (previous?.count ?: 0) + 1,
                )
            current.copy(
                searches =
                    (listOf(entry) + current.searches.filterNot { it.query.equals(trimmed, true) })
                        .take(MAX_SEARCHES),
            )
        }
    }

    suspend fun removeSearch(query: String) {
        mutate { current ->
            current.copy(searches = current.searches.filterNot { it.query.equals(query, true) })
        }
    }

    suspend fun clearSearch() {
        mutate { current -> current.copy(searches = emptyList()) }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoaded()
                _state.value = HistoryState()
                file.delete()
            }
        }
    }

    private suspend fun mutate(block: (HistoryState) -> HistoryState) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoaded()
                val next = block(_state.value)
                if (next == _state.value) return@withLock
                _state.value = next
                write(next)
            }
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _state.value = read()
    }

    private fun read(): HistoryState {
        if (!file.exists()) return HistoryState()
        return runCatching {
            val root = JSONObject(file.readText())
            val watches =
                root.optJSONArray("watches")?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val url = item.optString("url")
                            if (url.isBlank()) continue
                            add(
                                WatchHistoryEntry(
                                    url = url,
                                    title = item.optString("title"),
                                    uploader = item.optString("uploader"),
                                    thumbnailUrl = item.optString("thumbnail").takeIf { it.isNotBlank() },
                                    durationSeconds = item.optLong("duration"),
                                    watchedAtEpochMillis = item.optLong("watchedAt"),
                                    progressMs = item.optLong("progressMs"),
                                    topics = item.optJSONArray("topics").toStringSet(),
                                    sourceQuery = item.optString("sourceQuery").takeIf { it.isNotBlank() },
                                ),
                            )
                        }
                    }
                }.orEmpty()
            val searches =
                root.optJSONArray("searches")?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val query = item.optString("query")
                            if (query.isBlank()) continue
                            add(
                                SearchHistoryEntry(
                                    query = query,
                                    searchedAtEpochMillis = item.optLong("searchedAt"),
                                    count = item.optInt("count", 1).coerceAtLeast(1),
                                ),
                            )
                        }
                    }
                }.orEmpty()
            HistoryState(watches = watches, searches = searches)
        }.getOrDefault(HistoryState())
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return buildSet {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun write(state: HistoryState) {
        runCatching {
            val root =
                JSONObject().apply {
                    put(
                        "watches",
                        JSONArray().apply {
                            state.watches.forEach { entry ->
                                put(
                                    JSONObject().apply {
                                        put("url", entry.url)
                                        put("title", entry.title)
                                        put("uploader", entry.uploader)
                                        put("thumbnail", entry.thumbnailUrl.orEmpty())
                                        put("duration", entry.durationSeconds)
                                        put("watchedAt", entry.watchedAtEpochMillis)
                                        put("progressMs", entry.progressMs)
                                        put("topics", JSONArray(entry.topics.toList()))
                                        put("sourceQuery", entry.sourceQuery.orEmpty())
                                    },
                                )
                            }
                        },
                    )
                    put(
                        "searches",
                        JSONArray().apply {
                            state.searches.forEach { entry ->
                                put(
                                    JSONObject().apply {
                                        put("query", entry.query)
                                        put("searchedAt", entry.searchedAtEpochMillis)
                                        put("count", entry.count)
                                    },
                                )
                            }
                        },
                    )
                }
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(root.toString())
            if (!temp.renameTo(file)) {
                file.writeText(root.toString())
                temp.delete()
            }
        }
    }

    companion object {
        const val MAX_WATCHES = 200
        const val MAX_SEARCHES = 20
    }
}
