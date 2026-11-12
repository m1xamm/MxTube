package com.m1xamm.mxtube.data.youtube

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OkHttpDownloaderTest {
    private val executor = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val releases = mutableListOf<CountDownLatch>()

    @Before
    fun setUp() {
        server.executor = executor
        server.start()
    }

    @After
    fun tearDown() {
        releases.forEach { it.countDown() }
        server.stop(0)
        executor.shutdownNow()
    }

    @Test
    fun successfulRequestsDoNotCancelTheNextRequest() = runBlocking<Unit> {
        server.createContext("/") { exchange ->
            val body = "ok".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        val downloader = OkHttpDownloader(OkHttpClient())
        repeat(2) {
            val response = withExtractorContext { downloader.execute(request()) }
            assertEquals(200, response.responseCode())
            assertEquals("ok", response.responseBody())
        }
    }

    @Test
    fun cancellationClosesARequestWaitingForHeaders() = runBlocking<Unit> {
        assertCancellation(sendPartialBody = false)
    }

    @Test
    fun cancellationClosesARequestReadingTheBody() = runBlocking<Unit> {
        assertCancellation(sendPartialBody = true)
    }

    private suspend fun assertCancellation(sendPartialBody: Boolean) = coroutineScope {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val release = CountDownLatch(1).also(releases::add)
        server.createContext("/") { exchange ->
            try {
                if (sendPartialBody) {
                    exchange.sendResponseHeaders(200, 100L)
                    exchange.responseBody.write("partial".toByteArray())
                    exchange.responseBody.flush()
                }
                started.complete(Unit)
                release.await()
            } finally {
                exchange.close()
            }
        }
        val client = OkHttpClient.Builder()
            .readTimeout(30L, TimeUnit.SECONDS)
            .eventListener(object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) {
                    if (call.isCanceled()) cancelled.complete(Unit)
                }
            })
            .build()
        val downloader = OkHttpDownloader(client)
        val operation = async { withExtractorContext { downloader.execute(request()) } }
        try {
            withTimeout(5_000L) { started.await() }
            withTimeout(5_000L) {
                operation.cancelAndJoin()
                cancelled.await()
            }
            assertTrue(operation.isCancelled)
        } finally {
            operation.cancel()
            release.countDown()
        }
    }

    private fun request(): Request =
        Request.newBuilder().get("http://127.0.0.1:${server.address.port}/").build()
}
