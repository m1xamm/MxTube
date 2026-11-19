package com.mixam.mxtube.notifications

import com.mixam.mxtube.data.local.SubscriptionEntry
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object SubscriptionNotificationState {
    const val FILE_NAME = "mx_subscription_notifications.json"
    const val MAX_CHANNELS = 300
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024
    val mutex = Mutex()

    fun read(directory: File): MutableMap<String, ChannelUploadNotificationState> {
        val file = File(directory, FILE_NAME)
        if (!file.exists() || file.length() > MAX_FILE_BYTES) return linkedMapOf()
        return try {
            val root = JSONObject(file.readText())
            if (root.optInt("v") != 1) return linkedMapOf()
            val entries = root.optJSONArray("channels") ?: return linkedMapOf()
            linkedMapOf<String, ChannelUploadNotificationState>().apply {
                for (index in 0 until minOf(entries.length(), MAX_CHANNELS)) {
                    val item = entries.optJSONObject(index) ?: continue
                    val key = item.optString("key").takeIf { it.isNotBlank() && it.length <= 128 } ?: continue
                    val url = item.optString("url").takeIf { it.isNotBlank() && it.length <= 512 } ?: continue
                    val seen = item.optJSONArray("seen")
                    put(key, ChannelUploadNotificationState(
                        addedAtEpochMillis = item.optLong("addedAt"),
                        channelUrl = url,
                        seeded = item.optBoolean("seeded"),
                        seenVideoIds = buildList {
                            if (seen != null) {
                                for (i in 0 until minOf(seen.length(), SubscriptionNotificationPolicy.MAX_SEEN_IDS)) {
                                    val id = seen.optString(i)
                                    if (UploadVideoIdentity.fromUrl(UploadVideoIdentity.url(id)) != null) add(id)
                                }
                            }
                        }.distinct(),
                        lastPersonalizedAtEpochMillis = item.optLong("lastPersonalizedAt").coerceAtLeast(0L),
                        lastCheckedAtEpochMillis = item.optLong("lastCheckedAt").coerceAtLeast(0L),
                        resolvedChannelUrl = item.optString("resolvedUrl").takeIf { it.isNotBlank() && it.length <= 512 },
                        retryPending = item.optBoolean("retry"),
                    ))
                }
            }
        } catch (_: org.json.JSONException) {
            linkedMapOf()
        }
    }

    fun prune(
        states: MutableMap<String, ChannelUploadNotificationState>,
        subscriptions: List<SubscriptionEntry>,
    ) {
        val current = subscriptions.take(MAX_CHANNELS).associateBy { it.key }
        states.entries.removeAll { (key, state) ->
            val channel = current[key]
            channel == null || channel.addedAtEpochMillis != state.addedAtEpochMillis || channel.url != state.channelUrl
        }
    }

    fun write(directory: File, states: Map<String, ChannelUploadNotificationState>) {
        val root = JSONObject().put("v", 1).put("channels", JSONArray().apply {
            states.entries.take(MAX_CHANNELS).forEach { (key, state) ->
                put(JSONObject()
                    .put("key", key)
                    .put("url", state.channelUrl)
                    .put("addedAt", state.addedAtEpochMillis)
                    .put("seeded", state.seeded)
                    .put("seen", JSONArray(state.seenVideoIds.take(SubscriptionNotificationPolicy.MAX_SEEN_IDS)))
                    .put("lastPersonalizedAt", state.lastPersonalizedAtEpochMillis)
                    .put("lastCheckedAt", state.lastCheckedAtEpochMillis)
                    .put("retry", state.retryPending)
                    .put("resolvedUrl", state.resolvedChannelUrl.orEmpty()))
            }
        })
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FILE_BYTES) throw IOException("Upload notification state exceeds limit")
        val target = File(directory, FILE_NAME)
        val temporary = File(directory, "$FILE_NAME.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
