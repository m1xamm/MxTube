package com.m1xamm.mxtube.data.youtube

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.concurrent.CountDownLatch

class YouTubePlaylistSessionTest {
    @Test
    fun readsPlaylistVideosAndContinuation() = runBlocking {
        NewPipe.init(OkHttpDownloader(), Localization("en", "US"), ContentCountry("US"))
        val continuation = Page("https://www.youtube.com/playlist-page2")
        val extractor = FakePlaylistExtractor(
            initial = InfoItemsPage(listOf(stream("playlist001"), stream("playlist002", short = true)), continuation, emptyList()),
            next = InfoItemsPage(listOf(stream("playlist003")), null, emptyList()),
        )
        val session = YouTubePlaylistSession(extractor)
        val first = session.first()
        assertEquals(listOf("playlist001"), first.videos.map { it.title })
        assertTrue(first.hasMore)
        val second = session.loadMore()
        assertEquals(listOf("playlist003"), second.videos.map { it.title })
        assertFalse(second.hasMore)
        assertFalse(session.loadMore().hasMore)
        assertEquals(listOf(continuation.url), extractor.requests)
    }

    @Test
    fun cancelledLatePlaylistResponseDoesNotAdvanceTheContinuation() = runBlocking<Unit> {
        NewPipe.init(OkHttpDownloader(), Localization("en", "US"), ContentCountry("US"))
        val continuation = Page("https://www.youtube.com/playlist-page2")
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val extractor = FakePlaylistExtractor(
            initial = InfoItemsPage(emptyList(), continuation, emptyList()),
            next = InfoItemsPage(emptyList(), null, emptyList()),
            started = started,
            release = release,
        )
        val session = YouTubePlaylistSession(extractor)
        assertTrue(session.first().hasMore)
        val operation = async { session.loadMore() }
        try {
            withTimeout(5_000L) { started.await() }
            operation.cancel()
            release.countDown()
            withTimeout(5_000L) { operation.join() }
            assertFalse(extractor.requests.isEmpty())
            assertEquals(listOf(continuation.url), extractor.requests)
            assertFalse(session.loadMore().hasMore)
            assertEquals(listOf(continuation.url, continuation.url), extractor.requests)
        } finally {
            operation.cancel()
            release.countDown()
        }
    }

    private fun stream(id: String, short: Boolean = false) =
        StreamInfoItem(ServiceList.YouTube.serviceId, "https://www.youtube.com/watch?v=$id", id, StreamType.VIDEO_STREAM).apply {
            uploaderName = "Channel"
            uploaderUrl = "https://www.youtube.com/@channel"
            duration = 60L
            viewCount = 10L
            setShortFormContent(short)
        }

    private class FakePlaylistExtractor(
        private val initial: InfoItemsPage<StreamInfoItem>,
        private val next: InfoItemsPage<StreamInfoItem>,
        private val started: CompletableDeferred<Unit>? = null,
        private val release: CountDownLatch? = null,
    ) : PlaylistExtractor(
        ServiceList.YouTube,
        ListLinkHandler("https://www.youtube.com/playlist?list=PL123", "https://www.youtube.com/playlist?list=PL123", "PL123", emptyList(), ""),
    ) {
        val requests = mutableListOf<String>()

        override fun onFetchPage(downloader: Downloader) {}
        override fun getInitialPage(): InfoItemsPage<StreamInfoItem> = initial
        override fun getPage(page: Page): InfoItemsPage<StreamInfoItem> {
            requests.add(page.url)
            if (requests.size == 1) {
                started?.complete(Unit)
                release?.await()
            }
            return next
        }

        override fun getName(): String = "Playlist"
        override fun getUploaderUrl(): String = "https://www.youtube.com/@channel"
        override fun getUploaderName(): String = "Channel"
        override fun getUploaderAvatars(): List<Image> = emptyList()
        override fun isUploaderVerified(): Boolean = false
        override fun getStreamCount(): Long = 3L
        override fun getDescription(): Description = Description("", Description.PLAIN_TEXT)
    }
}