package com.m1xamm.mxtube.data.local

import android.content.Context
import com.m1xamm.mxtube.data.subscriptions.MAX_SUBSCRIPTIONS_FEED_VIDEOS
import com.m1xamm.mxtube.data.subscriptions.boundSubscriptionsVideos
import com.m1xamm.mxtube.data.youtube.VideoSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

class SubscriptionsFeedCache internal constructor(private val file: File) {
    constructor(context: Context) : this(
        File(context.filesDir, "mx_subscriptions_feed_${context.resources.configuration.locales[0].toLanguageTag()}.json"),
    )

    private val mutex = Mutex()

    suspend fun read(activeChannelKeys: Set<String>): Map<String, List<VideoSummary>> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!file.exists() || file.length() > MAX_FILE_BYTES || activeChannelKeys.isEmpty()) {
                    return@withLock emptyMap()
                }
                runCatching {
                    val root = JSONObject(file.readText())
                    if (root.optInt("v") != SCHEMA) return@withLock emptyMap()
                    val channels = root.optJSONArray("channels") ?: return@withLock emptyMap()
                    var remaining = MAX_SUBSCRIPTIONS_FEED_VIDEOS
                    val result = linkedMapOf<String, List<VideoSummary>>()
                    for (index in 0 until minOf(channels.length(), 300)) {
                        val channel = channels.optJSONObject(index) ?: continue
                        val key = channel.optString("key")
                        if (key !in activeChannelKeys || key in result) continue
                        val items = channel.optJSONArray("videos") ?: continue
                        result[key] = buildList {
                            for (videoIndex in 0 until minOf(items.length(), remaining)) {
                                val item = items.optJSONObject(videoIndex) ?: continue
                                val url = item.optString("url")
                                if (url.isBlank()) continue
                                add(VideoSummary(
                                    url = url,
                                    title = item.optString("title"),
                                    uploader = item.optString("uploader"),
                                    durationSeconds = item.optLong("duration"),
                                    viewCount = item.optLong("views"),
                                    uploadedAtEpochMillis =
                                        if (item.isNull("uploadedAt")) null else item.optLong("uploadedAt"),
                                    thumbnailUrl = item.nullableString("thumbnail"),
                                    uploaderUrl = item.nullableString("uploaderUrl"),
                                ))
                            }
                        }
                        remaining -= result.getValue(key).size
                    }
                    boundSubscriptionsVideos(result)
                }.getOrDefault(emptyMap())
            }
        }

    suspend fun write(
        videosByChannel: Map<String, List<VideoSummary>>,
        activeChannelKeys: Set<String> = videosByChannel.keys,
    ) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val bounded = boundSubscriptionsVideos(videosByChannel.filterKeys { it in activeChannelKeys })
                val root = JSONObject().put("v", SCHEMA).put(
                    "channels",
                    JSONArray().apply {
                        bounded.forEach { (key, videos) ->
                            put(JSONObject().put("key", key).put(
                                "videos",
                                JSONArray().apply {
                                    videos.forEach { video ->
                                        put(JSONObject().apply {
                                            put("url", video.url)
                                            put("title", video.title)
                                            put("uploader", video.uploader)
                                            put("duration", video.durationSeconds)
                                            put("views", video.viewCount)
                                            put("uploadedAt", video.uploadedAtEpochMillis ?: JSONObject.NULL)
                                            put("thumbnail", video.thumbnailUrl ?: JSONObject.NULL)
                                            put("uploaderUrl", video.uploaderUrl ?: JSONObject.NULL)
                                        })
                                    }
                                },
                            ))
                        }
                    },
                )
                coroutineContext.ensureActive()
                val temp = File(file.parentFile, "${file.name}.tmp")
                try {
                    temp.writeText(root.toString())
                    coroutineContext.ensureActive()
                    if (!temp.renameTo(file)) {
                        file.writeText(root.toString())
                    }
                } catch (error: java.io.IOException) {
                    return@withLock
                } finally {
                    temp.delete()
                }
            }
        }
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key)

    private companion object {
        const val SCHEMA = 1
        const val MAX_FILE_BYTES = 4L * 1024 * 1024
    }
}
