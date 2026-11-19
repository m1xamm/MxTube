package com.mixam.mxtube.data.youtube

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.File
import java.util.Locale

class YouTubeSmokeTest {
    @Before
    fun setUp() {
        assumeTrue(System.getenv("MX_TUBE_SMOKE") == "1")
        NewPipe.init(
            OkHttpDownloader(),
            Localization("ru", "RU"),
            ContentCountry("RU"),
        )
    }

    private fun repository() = YouTubeRepository(string = { "Audio" }, locale = { Locale.ENGLISH })

    @Test
    fun searchAndResolveStream() {
        val search = ServiceList.YouTube.getSearchExtractor("lofi hip hop")
        search.fetchPage()

        val videos =
            search.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .filter { it.streamType == StreamType.VIDEO_STREAM }

        assertTrue("search returned no videos", videos.isNotEmpty())
        println("found ${videos.size} videos")

        val first = videos.first()
        println("${first.name} | ${first.uploaderName} | ${first.duration}s | ${first.url}")

        val stream = ServiceList.YouTube.getStreamExtractor(first.url)
        stream.fetchPage()

        val muxed = stream.videoStreams.filter { it.isUrl }
        val videoOnly = stream.videoOnlyStreams.filter { it.isUrl }

        println("title: ${stream.name}")
        println("muxed: ${muxed.map { it.height }}")
        println("video-only: ${videoOnly.map { it.height }}")
        println("audio count: ${stream.audioStreams.size}")
        println("hls: ${stream.hlsUrl.isNotEmpty()}, dash: ${stream.dashMpdUrl.isNotEmpty()}")

        assertTrue(
            "no playable stream",
            muxed.isNotEmpty() || videoOnly.isNotEmpty() || stream.hlsUrl.isNotEmpty() || stream.dashMpdUrl.isNotEmpty(),
        )
    }

    @Test
    fun repositoryResolvesPlayableQualities() = runBlocking {
        val repository = repository()
        val session = repository.search("lofi hip hop")
        val batch = session.first()

        assertTrue("repository search returned no videos", batch.videos.isNotEmpty())

        val video = batch.videos.first()
        val info = repository.resolve(video.url)

        println("resolved: ${info.title} | ${info.qualities.size} qualities")
        println("audio tracks: ${info.audioTracks.map { it.label }}")
        println(
            info.qualities.joinToString { quality ->
                "${quality.label} ${quality.videoMimeType}"
            },
        )

        assertTrue("no qualities", info.qualities.isNotEmpty())
        assertTrue("quality without video url", info.qualities.all { it.videoUrl.isNotBlank() })
    }

    @Test
    fun channelHeaderVideosAndPlaylistsAreExtracted() = runBlocking<Unit> {
        val repository = repository()
        val batch = repository.search("lofi girl").first()
        val video = batch.videos.first()
        assertTrue("search rows must carry the channel url", video.uploaderUrl != null)

        val page = repository.channelPage(video.uploaderUrl.orEmpty())
        val header = page.header
        println("CHANNEL key=${header.key} name=${header.name} url=${header.url}")
        println("CHANNEL avatar=${header.avatarUrl} subs=${header.subscriberCount} desc=${header.description.length}")
        println("CHANNEL tabs=${page.availableTabs}")
        assertTrue("channel has no canonical id", ChannelIdentity.isChannelId(header.key))
        assertTrue("channel has no avatar", header.avatarUrl != null)

        val videosSession = page.tab(ChannelTabs.VIDEOS)
        val videos = videosSession?.first()
        assertTrue("channel videos tab returned nothing", videos != null && videos.videos.isNotEmpty())
        println("TAB videos=${videos?.videos?.size} hasMore=${videosSession?.hasMore}")
        videos?.videos?.take(3)?.forEach { println("  ${it.uploadedAtEpochMillis} | ${it.uploaderUrl} | ${it.title}") }

        if (videos != null && videosSession?.hasMore == true) {
            val more = videosSession.loadMore()
            println("TAB videos page2=${more.videos.size}")
            assertTrue("second page was empty", more.videos.isNotEmpty())
            assertTrue(
                "second page repeated the first",
                more.videos.none { newer -> videos.videos.any { it.url == newer.url } },
            )
        }

        val playlists = page.tab(ChannelTabs.PLAYLISTS)?.first()
        println("TAB playlists=${playlists?.playlists?.size}")
        playlists?.playlists?.take(3)?.forEach { println("  ${it.videoCount} | ${it.url} | ${it.name}") }
    }

    @Test
    fun kioskViewCountsAreCorrected() = runBlocking {
        val repository = repository()
        val batch = repository.kiosk("trending_music").first()
        assertTrue("kiosk returned no videos", batch.videos.isNotEmpty())
        batch.videos.take(10).forEach { video ->
            println("VIEWCOUNT ${video.viewCount} | ${video.title}")
        }
    }

    @Test
    fun dumpRelatedResponse() = runBlocking {
        val delegate = OkHttpDownloader()
        val capture =
            object : Downloader() {
                override fun execute(request: Request): Response {
                    val response = delegate.execute(request)
                    if (request.url().contains("/youtubei/v1/next")) {
                        File("build/related-response.json").writeText(response.responseBody())
                    }
                    return response
                }
            }
        NewPipe.init(capture, Localization("ru", "RU"), ContentCountry("RU"))
        val extractor =
            ServiceList.YouTube
                .getStreamExtractor("https://www.youtube.com/watch?v=fVtmSti-4rA")
                .apply {
                    forceLocalization(Localization("ru", "RU"))
                    forceContentCountry(ContentCountry("RU"))
                }
        extractor.fetchPage()
        extractor.relatedItems?.items?.videos().orEmpty().take(5).forEach { video ->
            println("RELATED ${video.viewCount} | ${video.title}")
        }
    }
}
