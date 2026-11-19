package com.mixam.mxtube.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewCountCorrectorTest {
    @Test
    fun parsesRussianAbbreviatedCounts() {
        assertEquals(33_000L, ViewCountCorrector.parseViewCount("33 тыс. просмотров"))
        assertEquals(2_800L, ViewCountCorrector.parseViewCount("2,8 тыс."))
        assertEquals(1_200_000L, ViewCountCorrector.parseViewCount("1,2 млн просмотров"))
        assertEquals(19_600_000L, ViewCountCorrector.parseViewCount("19,6 млн"))
        assertEquals(1_000_000_000L, ViewCountCorrector.parseViewCount("1 млрд"))
    }

    @Test
    fun parsesEnglishAbbreviatedCounts() {
        assertEquals(33_000L, ViewCountCorrector.parseViewCount("33K views"))
        assertEquals(1_200_000L, ViewCountCorrector.parseViewCount("1.2M views"))
        assertEquals(1_000_000_000L, ViewCountCorrector.parseViewCount("1B views"))
    }

    @Test
    fun parsesUkrainianAbbreviatedCounts() {
        assertEquals(33_000L, ViewCountCorrector.parseViewCount("33 тис. переглядів"))
        assertEquals(1_200L, ViewCountCorrector.parseViewCount("1,2 тис."))
    }

    @Test
    fun parsesPlainCountsAndReturnsNullWithoutDigits() {
        assertEquals(33L, ViewCountCorrector.parseViewCount("33 просмотра"))
        assertNull(ViewCountCorrector.parseViewCount("Нет просмотров"))
    }

    @Test
    fun parsesFullCountsWithSpaces() {
        assertEquals(6_220_185L, ViewCountCorrector.parseViewCount("6 220 185 просмотров"))
        assertEquals(476_882L, ViewCountCorrector.parseViewCount("476 882 просмотра"))
        assertEquals(703_489L, ViewCountCorrector.parseViewCount("703\u00A0489 просмотров"))
    }

    @Test
    fun correctsStoredVideoRendererCounts() {
        ViewCountCorrector.record(
            """{"contents":{"videoRenderer":{"videoId":"abcdefghijk","viewCountText":{"simpleText":"787 тыс. просмотров"}}}}""",
        )
        assertEquals(
            787_000L,
            ViewCountCorrector.correct("https://www.youtube.com/watch?v=abcdefghijk&t=10", 787),
        )
    }

    @Test
    fun correctsStoredCompactRendererCounts() {
        ViewCountCorrector.record(
            """{"onResponseReceivedCommands":[{"appendContinuationItemsAction":{"continuationItems":[{"compactVideoRenderer":{"videoId":"zyxwvutsrqp","viewCountText":{"runs":[{"text":"1,2 млн просмотров"}]}}}]}}]}""",
        )
        assertEquals(
            1_200_000L,
            ViewCountCorrector.correct("https://youtu.be/zyxwvutsrqp", 1),
        )
    }

    @Test
    fun correctsLockupViewModelCounts() {
        ViewCountCorrector.record(
            """{"contents":{"lockupViewModel":{"contentId":"lockupvid01","contentType":"LOCKUP_CONTENT_TYPE_VIDEO","metadata":{"lockupMetadataViewModel":{"metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"NovaFeel"}},{"text":{"content":"54 тыс."},"leadingIcon":{"name":"PLAY_ARROW_OUTLINED"}},{"text":{"content":"1 нед. назад"}}]}]}}}}}}}}""",
        )
        assertEquals(
            54_000L,
            ViewCountCorrector.correct("https://www.youtube.com/watch?v=lockupvid01", 54),
        )
    }

    @Test
    fun channelLockupWithoutLeadingIconKeepsTheViewMultiplier() {
        ViewCountCorrector.record(
            """{"contents":{"lockupViewModel":{"contentId":"channelvid1","contentType":"LOCKUP_CONTENT_TYPE_VIDEO","metadata":{"lockupMetadataViewModel":{"metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"11,5 млн просмотров"}},{"text":{"content":"1 мес. назад"}}]}]}}}}}}}""",
        )
        assertEquals(11_500_000L, ViewCountCorrector.correct("https://www.youtube.com/watch?v=channelvid1", 11))
    }

    @Test
    fun channelGridViewCountKeepsTheViewMultiplier() {
        ViewCountCorrector.record(
            """{"contents":{"gridVideoRenderer":{"videoId":"gridvid0001","shortViewCountText":{"simpleText":"10 млн просмотров"}}}}""",
        )
        assertEquals(10_000_000L, ViewCountCorrector.correct("https://www.youtube.com/watch?v=gridvid0001", 10))
    }

    @Test
    fun channelSubscriberHeaderKeepsTheSubscriberMultiplier() {
        val id = "UC2222222222222222222222"
        ViewCountCorrector.record(
            """{"metadata":{"channelMetadataRenderer":{"externalId":"$id"}},"header":{"pageHeaderViewModel":{"metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"3,4 млн подписчиков"}}]}]}}}}}""",
        )
        assertEquals(3_400_000L, ViewCountCorrector.correctSubscribers(id, 3))
    }

    @Test
    fun dateWithoutViewsLabelCannotBeMistakenForViews() {
        ViewCountCorrector.record(
            """{"contents":{"lockupViewModel":{"contentId":"datevid0001","metadata":{"lockupMetadataViewModel":{"metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"1 month ago"}}]}]}}}}}}}""",
        )
        assertEquals(21L, ViewCountCorrector.correct("https://www.youtube.com/watch?v=datevid0001", 21))
    }

    @Test
    fun readsLegacyChannelHandleAndVideoCount() {
        val id = channelId("metadataOld")
        ViewCountCorrector.record(
            """{"metadata":{"channelMetadataRenderer":{"externalId":"$id","vanityChannelUrl":"https://www.youtube.com/@LegacyHandle"}},"header":{"c4TabbedHeaderRenderer":{"subscriberCountText":{"runs":[{"text":"3,4 млн подписчиков"}]},"videosCountText":{"runs":[{"text":"1 234 видео"}]}}}}""",
        )
        assertEquals("@LegacyHandle", ViewCountCorrector.channelHandle(id))
        assertEquals(1_234L, ViewCountCorrector.channelVideoCount(id))
        assertEquals(3_400_000L, ViewCountCorrector.correctSubscribers(id, 3))
    }

    @Test
    fun readsPageHeaderViewModelHandleAndCounts() {
        val id = channelId("metadataNew")
        ViewCountCorrector.record(
            """{"metadata":{"channelMetadataRenderer":{"externalId":"$id"}},"header":{"pageHeaderViewModel":{"metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"@NewHandle"}},{"text":{"content":"1.2M subscribers"}},{"text":{"content":"345 videos"}}]}]}}}}}""",
        )
        assertEquals("@NewHandle", ViewCountCorrector.channelHandle(id))
        assertEquals(345L, ViewCountCorrector.channelVideoCount(id))
        assertEquals(1_200_000L, ViewCountCorrector.correctSubscribers(id, 1))
    }

    @Test
    fun ignoresInvalidHandleAndKeepsUnknownVideoCount() {
        val id = channelId("metadataBad")
        ViewCountCorrector.record(
            """{"metadata":{"channelMetadataRenderer":{"externalId":"$id","vanityChannelUrl":"https://example.com/@NotYouTube"}},"header":{"pageHeaderViewModel":{"metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"@not a handle"}},{"text":{"content":"12K subscribers"}}]}]}}}}}""",
        )
        assertNull(ViewCountCorrector.channelHandle(id))
        assertEquals(-1L, ViewCountCorrector.channelVideoCount(id))
        assertEquals(12_000L, ViewCountCorrector.correctSubscribers(id, 1))
    }

    @Test
    fun acceptsXssiPrefixedResponses() {
        ViewCountCorrector.record(
            ")]}'\n" +
                """{"contents":{"videoRenderer":{"videoId":"xssiprefix1","viewCountText":{"simpleText":"33 тыс. просмотров"}}}}""",
        )
        assertEquals(
            33_000L,
            ViewCountCorrector.correct("https://www.youtube.com/watch?v=xssiprefix1", 33),
        )
    }

    private fun channelId(suffix: String) = "UC" + suffix.padEnd(22, '0')
}
