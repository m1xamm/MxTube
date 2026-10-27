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
    private val localeTag = context.resources.configuration.locales[0].toLanguageTag()
    private val mutex = Mutex()

    suspend fun read(): CachedHomeFeed? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!file.exists()) return@withLock null
                runCatching {
                    val root = JSONObject(file.readText())
                    if (root.optInt("v") != CACHE_SCHEMA) return@withLock null
                    if (root.optString("locale") != localeTag) return@withLock null
                    val items = root.optJSONArray("items") ?: return@withLock null
                    val feed =
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
                    if (feed.isEmpty()) {
                        null
                    } else {
                        CachedHomeFeed(
                            items = feed,
                            savedAt = root.optLong("savedAt", 0L),
                        )
                    }
                }.getOrNull()
            }
        }

    suspend fun write(items: List<HomeFeedItem>) {
        if (items.isEmpty()) return
        val savedAt = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val root =
                        JSONObject()
                            .put("v", CACHE_SCHEMA)
                            .put("locale", localeTag)
                            .put("savedAt", savedAt)
                            .put(
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
        const val CACHE_SCHEMA = 6
    }
}

data class CachedHomeFeed(
    val items: List<HomeFeedItem>,
    val savedAt: Long,
)
