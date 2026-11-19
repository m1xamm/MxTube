package com.mixam.mxtube.notifications

import com.mixam.mxtube.data.network.Http
import com.mixam.mxtube.data.youtube.ChannelIdentity
import com.mixam.mxtube.data.youtube.VideoSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.Call
import okhttp3.Callback
import org.jsoup.Jsoup
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

internal class UploadFeedHttpException(val status: Int) : IOException("YouTube feed HTTP $status")
internal class InvalidUploadFeedException(message: String) : Exception(message)
internal data class SubscriptionUploadFeed(val channelUrl: String, val videos: List<VideoSummary>)

internal object SubscriptionUploadFeedSource {
    private const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024
    private val client by lazy {
        Http.client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
    private val embeddedChannelId = Regex("\"(?:externalId|channelId)\"\\s*:\\s*\"(UC[A-Za-z0-9_-]{22})\"")

    suspend fun fetch(channelUrl: String, cachedResolvedUrl: String?): SubscriptionUploadFeed =
        runInterruptible(Dispatchers.IO) {
            val downloader = FeedDownloader()
            val ref = ChannelIdentity.from(channelUrl) ?: throw InvalidUploadFeedException("Invalid channel URL")
            val resolved = cachedResolvedUrl?.let(ChannelIdentity::from)
                ?.takeIf { ChannelIdentity.isChannelId(it.key) }
                ?: if (ChannelIdentity.isChannelId(ref.key) || ref.key.startsWith("user/")) ref else {
                    val page = downloader.get(ref.url).responseBody()
                    val document = Jsoup.parse(page, ref.url)
                    val rssId = document.select("link[type=application/rss+xml]").asSequence()
                        .mapNotNull { ChannelIdentity.idFromFeedUrl(it.attr("href")) }.firstOrNull()
                    val metaId = document.selectFirst("meta[itemprop=channelId]")?.attr("content")
                        ?.takeIf(ChannelIdentity::isChannelId)
                    val id = rssId ?: metaId ?: embeddedChannelId.find(page)?.groupValues?.get(1)
                        ?: throw InvalidUploadFeedException("Channel RSS identity unavailable")
                    ChannelIdentity.from(ChannelIdentity.canonicalUrl(id))
                        ?: throw InvalidUploadFeedException("Invalid RSS channel identity")
                }
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val extractor = ServiceList.YouTube.getFeedExtractor(resolved.url)
            extractor.onFetchPage(downloader)
            val canonical = ChannelIdentity.from(extractor.url)
                ?: throw InvalidUploadFeedException("Response is not a YouTube Atom feed")
            val page = extractor.initialPage
            if (page.errors.isNotEmpty()) throw InvalidUploadFeedException("Incomplete Atom entries")
            val videos = page.items.take(50).mapNotNull { item ->
                val id = UploadVideoIdentity.fromUrl(item.url) ?: return@mapNotNull null
                VideoSummary(
                    url = UploadVideoIdentity.url(id),
                    title = item.name.orEmpty(),
                    uploader = item.uploaderName.orEmpty(),
                    durationSeconds = item.duration,
                    viewCount = item.viewCount,
                    uploadedAtEpochMillis = item.uploadDate?.instant?.toEpochMilli(),
                    thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                    uploaderUrl = canonical.url,
                )
            }
            SubscriptionUploadFeed(canonical.url, videos)
        }

    private class FeedDownloader : Downloader() {
        override fun execute(request: Request): Response {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            if (request.httpMethod() != "GET") throw InvalidUploadFeedException("Unexpected feed request method")
            val httpRequest = okhttp3.Request.Builder().url(request.url()).apply {
                request.headers().forEach { (name, values) -> values.forEach { addHeader(name, it) } }
            }.get().build()
            val call = client.newCall(httpRequest)
            val result = CompletableFuture<Response>()
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    result.completeExceptionally(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    response.use {
                        try {
                            if (!response.isSuccessful) throw UploadFeedHttpException(response.code)
                            val source = response.body.source()
                            if (source.request(MAX_RESPONSE_BYTES + 1)) {
                                throw InvalidUploadFeedException("Feed response exceeds limit")
                            }
                            result.complete(Response(
                                response.code,
                                response.message,
                                response.headers.toMultimap(),
                                source.readUtf8(),
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
                throw (error.cause ?: IOException("Feed request failed", error))
            }
        }
    }
}
