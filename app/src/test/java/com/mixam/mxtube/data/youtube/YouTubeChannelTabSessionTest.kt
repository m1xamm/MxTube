package com.mixam.mxtube.data.youtube

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabExtractor
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.CountDownLatch

class YouTubeChannelTabSessionTest {
    @Test
    fun wrappedInterruptionDoesNotFailTheCaller() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val operation = async {
            withExtractorContext {
                started.complete(Unit)
                try {
                    release.await()
                } catch (error: InterruptedException) {
                    throw java.io.IOException("Wrapped interruption", error)
                }
            }
        }
        try {
            withTimeout(5_000L) { started.await() }
            operation.cancel()
            withTimeout(5_000L) { operation.join() }
            assertTrue(currentCoroutineContext().isActive)
        } finally {
            operation.cancel()
            release.countDown()
        }
    }

    @Test
    fun cancelledLateResponseDoesNotAdvanceTheContinuation() = runBlocking<Unit> {
        NewPipe.init(OkHttpDownloader(), Localization("en", "US"), ContentCountry("US"))
        val url = "https://www.youtube.com/@channel/videos"
        val continuation = Page("https://www.youtube.com/page2")
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val requests = mutableListOf<String>()
        val extractor = object : ChannelTabExtractor(
            ServiceList.YouTube,
            ListLinkHandler(url, url, "channel", listOf("videos"), ""),
        ) {
            override fun onFetchPage(downloader: Downloader) {}

            override fun getInitialPage(): InfoItemsPage<InfoItem> =
                InfoItemsPage(emptyList(), continuation, emptyList())

            override fun getPage(page: Page): InfoItemsPage<InfoItem> {
                requests.add(page.url)
                if (requests.size == 1) {
                    started.complete(Unit)
                    release.await()
                }
                return InfoItemsPage(emptyList(), null, emptyList())
            }
        }
        val session = YouTubeChannelTabSession(extractor)
        assertTrue(session.first().hasMore)
        val operation = async { session.loadMore() }
        try {
            withTimeout(5_000L) { started.await() }
            operation.cancel()
            release.countDown()
            withTimeout(5_000L) { operation.join() }
            assertTrue(session.hasMore)
            assertFalse(session.loadMore().hasMore)
            assertEquals(listOf(continuation.url, continuation.url), requests)
        } finally {
            operation.cancel()
            release.countDown()
        }
    }
}
