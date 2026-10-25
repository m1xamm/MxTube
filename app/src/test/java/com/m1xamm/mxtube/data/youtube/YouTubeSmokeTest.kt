package com.m1xamm.mxtube.data.youtube

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class YouTubeSmokeTest {
    @Before
    fun setUp() {
        assumeTrue(System.getenv("MX_TUBE_SMOKE") == "1")
        NewPipe.init(OkHttpDownloader())
    }

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
        val repository = YouTubeRepository()
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
}
