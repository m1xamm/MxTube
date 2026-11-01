package com.m1xamm.mxtube.data.local

import android.content.Context
import com.m1xamm.mxtube.data.youtube.ChannelRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SubscriptionsStore internal constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    private val mutex = Mutex()
    private val _state = MutableStateFlow(SubscriptionsState())
    private var loaded = false

    val state: StateFlow<SubscriptionsState> = _state.asStateFlow()

    fun isSubscribed(key: String?): Boolean =
        key != null && _state.value.channels.any { it.key == key }

    suspend fun snapshot(): SubscriptionsState =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoaded()
                _state.value
            }
        }

    suspend fun subscribe(
        ref: ChannelRef,
        name: String,
        avatarUrl: String?,
        now: Long = System.currentTimeMillis(),
    ) {
        mutate { current ->
            val previous = current.channels.firstOrNull { it.key == ref.key }
            val entry =
                SubscriptionEntry(
                    key = ref.key,
                    url = ref.url,
                    name = name.ifBlank { previous?.name.orEmpty() },
                    avatarUrl = avatarUrl ?: previous?.avatarUrl,
                    addedAtEpochMillis = previous?.addedAtEpochMillis ?: now,
                )
            current.copy(
                channels =
                    (listOf(entry) + current.channels.filterNot { it.key == ref.key })
                        .take(MAX_CHANNELS),
            )
        }
    }

    suspend fun unsubscribe(key: String) {
        mutate { current ->
            current.copy(channels = current.channels.filterNot { it.key == key })
        }
    }

    suspend fun updateProfile(
        key: String,
        name: String?,
        avatarUrl: String?,
    ) {
        mutate { current ->
            var changed = false
            val channels =
                current.channels.map { entry ->
                    if (entry.key != key) {
                        entry
                    } else {
                        val nextName = name?.takeIf { it.isNotBlank() } ?: entry.name
                        val nextAvatar = avatarUrl ?: entry.avatarUrl
                        if (nextName == entry.name && nextAvatar == entry.avatarUrl) {
                            entry
                        } else {
                            changed = true
                            entry.copy(name = nextName, avatarUrl = nextAvatar)
                        }
                    }
                }
            if (!changed) current else current.copy(channels = channels)
        }
    }

    suspend fun markFeedSeen(now: Long = System.currentTimeMillis()) {
        mutate { current -> current.copy(lastSeenFeedAtEpochMillis = now) }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoaded()
                _state.value = SubscriptionsState()
                file.delete()
            }
        }
    }

    private suspend fun mutate(block: (SubscriptionsState) -> SubscriptionsState) {
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

    private fun read(): SubscriptionsState {
        if (!file.exists()) return SubscriptionsState()
        return runCatching {
            val root = JSONObject(file.readText())
            if (root.optInt("v") != SCHEMA) return SubscriptionsState()
            val channels =
                root.optJSONArray("channels")?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val key = item.optString("key")
                            val url = item.optString("url")
                            if (key.isBlank() || url.isBlank()) continue
                            add(
                                SubscriptionEntry(
                                    key = key,
                                    url = url,
                                    name = item.optString("name"),
                                    avatarUrl = item.optString("avatar").takeIf { it.isNotBlank() },
                                    addedAtEpochMillis = item.optLong("addedAt"),
                                ),
                            )
                        }
                    }
                }.orEmpty()
            SubscriptionsState(
                channels = channels,
                lastSeenFeedAtEpochMillis = root.optLong("lastSeenAt"),
            )
        }.getOrDefault(SubscriptionsState())
    }

    private fun write(state: SubscriptionsState) {
        runCatching {
            val root =
                JSONObject()
                    .put("v", SCHEMA)
                    .put("lastSeenAt", state.lastSeenFeedAtEpochMillis)
                    .put(
                        "channels",
                        JSONArray().apply {
                            state.channels.forEach { entry ->
                                put(
                                    JSONObject().apply {
                                        put("key", entry.key)
                                        put("url", entry.url)
                                        put("name", entry.name)
                                        put("avatar", entry.avatarUrl.orEmpty())
                                        put("addedAt", entry.addedAtEpochMillis)
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

    private companion object {
        const val FILE_NAME = "mx_subs.json"
        const val SCHEMA = 1
        const val MAX_CHANNELS = 300
    }
}
