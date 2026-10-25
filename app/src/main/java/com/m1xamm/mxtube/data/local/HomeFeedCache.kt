package com.m1xamm.mxtube.data.local

import android.content.Context
import com.m1xamm.mxtube.data.home.HomeFeedItem
import com.m1xamm.mxtube.data.youtube.VideoSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HomeFeedCache(context: Context) {
    private val file = File(context.filesDir, "mx_home_cache.json")
    private val mutex = Mutex()

    suspend fun read(): List<HomeFeedItem> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!file.exists()) return@withLock emptyList()
                runCatching {
                    val root = JSONObject(file.readText())
                    val items = root.optJSONArray("items") ?: return@withLock emptyList()
                    buildList {
                        for (index in 0 until items.length()) {
                            val item = items.optJSONObject(index) ?: continue
                            val url = item.optString("url")
                            if (url.isBlank()) continue
                            add(
                                HomeFeedItem(
                                    video =
                                        VideoSummary(
                                            url = url,
                                            title = item.optString("title"),
                                            uploader = item.optString("uploader"),
                                            durationSeconds = item.optLong("duration"),
                                            viewCount = item.optLong("views"),
                                            uploadedAtEpochMillis =
                                                item.optLong("uploadedAt").takeIf { it > 0 },
                                            thumbnailUrl =
                                                item.optString("thumbnail").takeIf { it.isNotBlank() },
                                        ),
                                    topics = item.optJSONArray("topics").toStringSet(),
                                ),
                            )
                        }
                    }
                }.getOrDefault(emptyList())
            }
        }

    suspend fun write(items: List<HomeFeedItem>) {
        if (items.isEmpty()) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val root =
                        JSONObject().put(
                            "items",
                            JSONArray().apply {
                                items.take(MAX_CACHE_ITEMS).forEach { feedItem ->
                                    val video = feedItem.video
                                    put(
                                        JSONObject().apply {
                                            put("url", video.url)
                                            put("title", video.title)
                                            put("uploader", video.uploader)
                                            put("duration", video.durationSeconds)
                                            put("views", video.viewCount)
                                            put("uploadedAt", video.uploadedAtEpochMillis ?: 0L)
                                            put("thumbnail", video.thumbnailUrl.orEmpty())
                                            put("topics", JSONArray(feedItem.topics.toList()))
                                        },
                                    )
                                }
                            },
                        )
                    val temp = File(file.parentFile, "${file.name}.tmp")
                    temp.writeText(root.toString())
                    if (!temp.renameTo(file)) {
                        file.writeText(root.toString())
                        temp.delete()
                    }
                }
            }
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            mutex.withLock { file.delete() }
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return buildSet {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private companion object {
        const val MAX_CACHE_ITEMS = 24
    }
}
