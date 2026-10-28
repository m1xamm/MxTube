package com.m1xamm.mxtube.data.youtube

import com.m1xamm.mxtube.data.network.Http
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class OkHttpDownloader : Downloader() {
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

        Http.client.newCall(httpRequest).execute().use { response ->
            val body = response.body.string()
            ViewCountCorrector.record(body)
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                body,
                response.request.url.toString(),
            )
        }
    }
}
