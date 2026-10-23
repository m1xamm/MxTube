package com.m1xamm.mxtube.data.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class OkHttpDownloader(
    private val client: OkHttpClient = defaultClient(),
) : Downloader() {
    override fun execute(request: Request): Response {
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

        client.newCall(httpRequest).execute().use { response ->
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString(),
            )
        }
    }

    private companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
