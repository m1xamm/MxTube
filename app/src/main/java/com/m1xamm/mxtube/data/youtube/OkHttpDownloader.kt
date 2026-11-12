package com.m1xamm.mxtube.data.youtube

import com.m1xamm.mxtube.data.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

internal suspend fun <T> withExtractorContext(block: () -> T): T {
    val context = currentCoroutineContext()
    return runInterruptible(Dispatchers.IO) {
        try {
            block().also { context.ensureActive() }
        } catch (error: Exception) {
            context.ensureActive()
            throw error
        }
    }
}

class OkHttpDownloader(private val client: OkHttpClient = Http.client) : Downloader() {
    override fun execute(request: Request): Response {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val httpRequest =
            okhttp3.Request.Builder()
                .url(request.url())
                .apply {
                    request.headers().forEach { (name, values) ->
                        values.forEach { addHeader(name, it) }
                    }
                }
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .build()

        val call = client.newCall(httpRequest)
        val result = CompletableFuture<Response>()
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                result.completeExceptionally(e)
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    try {
                        val body = response.body.string()
                        ViewCountCorrector.record(body)
                        result.complete(Response(
                            response.code,
                            response.message,
                            response.headers.toMultimap(),
                            body,
                            response.request.url.toString(),
                        ))
                    } catch (error: Exception) {
                        result.completeExceptionally(error)
                    }
                }
            }
        })
        return try {
            result.get()
        } catch (error: InterruptedException) {
            call.cancel()
            throw error
        } catch (error: ExecutionException) {
            throw (error.cause ?: IOException("YouTube request failed", error))
        }
    }
}
