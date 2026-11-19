package com.mixam.mxtube.data.local

import com.mixam.mxtube.data.youtube.ChannelRef
import com.mixam.mxtube.data.youtube.VideoSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BlockedChannelsStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun blockPersistsAndColdStoreHydratesState() = runBlocking {
        val file = File(folder.root, "blocked.json")
        val ref = ChannelRef("UC1111111111111111111111", "https://www.youtube.com/channel/UC1111111111111111111111")
        BlockedChannelsStore(file).block(ref, "Channel", null, 100L, aliases = setOf("@channel"))

        val restored = BlockedChannelsStore(file)
        withTimeout(2_000L) {
            while (restored.state.value.channels.isEmpty()) delay(10L)
        }

        assertTrue(restored.isBlocked(ref.url))
        assertTrue(restored.isBlocked(video(ref.url)))
        assertTrue(restored.isBlocked("https://www.youtube.com/@Channel/videos"))
    }

    @Test
    fun unblockRemovesChannel() = runBlocking {
        val file = File(folder.root, "blocked.json")
        val ref = ChannelRef("@channel", "https://www.youtube.com/@channel")
        val store = BlockedChannelsStore(file)
        store.block(ref, "Channel", null)
        assertTrue(store.isBlocked(ref.url))

        store.unblock(ref.key)

        assertFalse(store.isBlocked(ref.url))
        assertTrue(BlockedChannelsStore(file).snapshot().channels.isEmpty())
    }

    @Test
    fun failedBlockDoesNotChangeStateAndCanBeRetried() = runBlocking {
        val file = File(folder.root, "blocked.json")
        val temporary = File(folder.root, "blocked.json.tmp")
        assertTrue(temporary.mkdir())
        val store = BlockedChannelsStore(file)
        val ref = ChannelRef("@channel", "https://www.youtube.com/@channel")

        assertTrue(runCatching { store.block(ref, "Channel", null) }.isFailure)
        assertFalse(store.isBlocked(ref.url))
        assertFalse(file.exists())

        assertTrue(temporary.delete())
        store.block(ref, "Channel", null)
        assertTrue(store.isBlocked(ref.url))
        assertTrue(BlockedChannelsStore(file).snapshot().channels.isNotEmpty())
    }

    @Test
    fun failedUnblockKeepsSavedBlock() = runBlocking {
        val file = File(folder.root, "blocked.json")
        val store = BlockedChannelsStore(file)
        val ref = ChannelRef("@channel", "https://www.youtube.com/@channel")
        store.block(ref, "Channel", null)
        assertTrue(File(folder.root, "blocked.json.tmp").mkdir())

        assertTrue(runCatching { store.unblock(ref.key) }.isFailure)
        assertTrue(store.isBlocked(ref.url))
        assertTrue(BlockedChannelsStore(file).snapshot().channels.isNotEmpty())
    }

    private fun video(uploaderUrl: String) =
        VideoSummary(
            url = "https://www.youtube.com/watch?v=abcdefghijk",
            title = "Video",
            uploader = "Channel",
            durationSeconds = 60L,
            viewCount = 1L,
            uploadedAtEpochMillis = null,
            thumbnailUrl = null,
            uploaderUrl = uploaderUrl,
        )
}
