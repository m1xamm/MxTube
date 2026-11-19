package com.mixam.mxtube.data.local

import android.content.Context
import com.mixam.mxtube.data.youtube.ChannelIdentity
import com.mixam.mxtube.data.youtube.ChannelRef
import com.mixam.mxtube.data.youtube.VideoSummary
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
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class BlockedChannelEntry(
    val key: String,
    val url: String,
    val name: String,
    val avatarUrl: String?,
    val blockedAtEpochMillis: Long,
    val aliases: Set<String> = emptySet(),
)

data class BlockedChannelsState(
    val channels: List<BlockedChannelEntry> = emptyList(),
) {
    val keys: Set<String>
        get() = channels.flatMap { listOf(it.key) + it.aliases }.toSet()
}

class BlockedChannelsStore internal constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(BlockedChannelsState())
    private var loaded = false

    val state: StateFlow<BlockedChannelsState> = _state.asStateFlow()

    init {
        scope.launch {
            mutex.withLock { ensureLoaded() }
        }
    }

    fun isBlocked(rawUrl: String?): Boolean {
        val key = ChannelIdentity.keyOf(rawUrl) ?: return false
        return key in _state.value.keys
    }

    fun isBlocked(video: VideoSummary): Boolean = isBlocked(video.uploaderUrl)

    suspend fun snapshot(): BlockedChannelsState =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoaded()
                _state.value
            }
        }

    suspend fun block(
        ref: ChannelRef,
        name: String,
        avatarUrl: String?,
        now: Long = System.currentTimeMillis(),
        aliases: Set<String> = emptySet(),
    ) {
        mutate { current ->
            val previous = current.channels.firstOrNull { it.key == ref.key }
            val entry =
                BlockedChannelEntry(
                    key = ref.key,
                    url = ref.url,
                    name = name.ifBlank { previous?.name.orEmpty() },
                    avatarUrl = avatarUrl ?: previous?.avatarUrl,
                    blockedAtEpochMillis = previous?.blockedAtEpochMillis ?: now,
                    aliases = previous?.aliases.orEmpty() + aliases,
                )
            current.copy(channels = listOf(entry) + current.channels.filterNot { it.key == ref.key })
        }
    }

    suspend fun unblock(key: String) {
        mutate { current -> current.copy(channels = current.channels.filterNot { it.key == key }) }
    }

    private suspend fun mutate(block: (BlockedChannelsState) -> BlockedChannelsState) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoaded()
                val next = block(_state.value)
                if (next == _state.value) return@withLock
                write(next)
                _state.value = next
            }
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _state.value = read()
    }

    private fun read(): BlockedChannelsState {
        if (!file.exists()) return BlockedChannelsState()
        return runCatching {
            val root = JSONObject(file.readText())
            if (root.optInt("v") != SCHEMA) return BlockedChannelsState()
            val channels =
                root.optJSONArray("channels")?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val key = item.optString("key")
                            val url = item.optString("url")
                            if (key.isBlank() || url.isBlank()) continue
                            add(
                                BlockedChannelEntry(
                                    key = key,
                                    url = url,
                                    name = item.optString("name"),
                                    avatarUrl = item.optString("avatar").takeIf { it.isNotBlank() },
                                    blockedAtEpochMillis = item.optLong("blockedAt"),
                                    aliases = item.optJSONArray("aliases")?.let { values ->
                                        buildSet {
                                            for (aliasIndex in 0 until values.length()) {
                                                values.optString(aliasIndex).takeIf { it.isNotBlank() }?.let(::add)
                                            }
                                        }
                                    }.orEmpty(),
                                ),
                            )
                        }
                    }
                }.orEmpty()
            BlockedChannelsState(channels)
        }.getOrDefault(BlockedChannelsState())
    }

    private fun write(state: BlockedChannelsState) {
        val root = JSONObject()
            .put("v", SCHEMA)
            .put(
                "channels",
                JSONArray().apply {
                    state.channels.forEach { entry ->
                        put(
                            JSONObject()
                                .put("key", entry.key)
                                .put("url", entry.url)
                                .put("name", entry.name)
                                .put("avatar", entry.avatarUrl.orEmpty())
                                .put("blockedAt", entry.blockedAtEpochMillis)
                                .put("aliases", JSONArray(entry.aliases.toList())),
                        )
                    }
                },
            )
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(root.toString())
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private companion object {
        const val FILE_NAME = "mx_blocked_channels.json"
        const val SCHEMA = 1
    }
}
