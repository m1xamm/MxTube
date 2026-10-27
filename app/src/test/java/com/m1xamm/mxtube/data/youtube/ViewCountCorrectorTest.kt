package com.m1xamm.mxtube.data.youtube

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
}
