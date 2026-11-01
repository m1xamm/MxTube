package com.m1xamm.mxtube.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelIdentityTest {
    @Test
    fun readsEveryChannelShape() {
        assertEquals(
            ChannelRef("UC9v-e1KH7ViUK2Nq5He7z_A", "https://www.youtube.com/channel/UC9v-e1KH7ViUK2Nq5He7z_A"),
            ChannelIdentity.from("https://www.youtube.com/channel/UC9v-e1KH7ViUK2Nq5He7z_A"),
        )
        assertEquals(
            ChannelRef("@eranimus", "https://www.youtube.com/@eranimus"),
            ChannelIdentity.from("https://m.youtube.com/@Eranimus/videos"),
        )
        assertEquals(
            ChannelRef("c/eram", "https://www.youtube.com/c/ERAM"),
            ChannelIdentity.from("https://www.youtube.com/c/ERAM"),
        )
        assertEquals(
            ChannelRef("user/someuser", "https://www.youtube.com/user/someuser"),
            ChannelIdentity.from("https://youtube.com/user/someuser?app=desktop"),
        )
        assertEquals(
            ChannelRef("UC9v-e1KH7ViUK2Nq5He7z_A", "https://www.youtube.com/channel/UC9v-e1KH7ViUK2Nq5He7z_A"),
            ChannelIdentity.from("UC9v-e1KH7ViUK2Nq5He7z_A"),
        )
    }

    @Test
    fun keepsChannelIdCaseButFoldsHandles() {
        assertEquals("UCAbCdEfGhIjKlMnOpQrStUv", ChannelIdentity.keyOf("https://www.youtube.com/channel/UCAbCdEfGhIjKlMnOpQrStUv"))
        assertEquals("@mxtube", ChannelIdentity.keyOf("https://www.youtube.com/@MxTube"))
    }

    @Test
    fun ignoresAnythingThatIsNotAChannel() {
        assertNull(ChannelIdentity.from("https://www.youtube.com/watch?v=fVtmSti-4rA"))
        assertNull(ChannelIdentity.from("https://www.youtube.com/shorts/fVtmSti"))
        assertNull(ChannelIdentity.from("https://www.youtube.com/playlist?list=PL1234567890"))
        assertNull(ChannelIdentity.from("https://example.com/channel/UC9v-e1KH7ViUK2Nq5He7z_A"))
        assertNull(ChannelIdentity.from("https://www.youtube.com"))
        assertNull(ChannelIdentity.from("https://www.youtube.com/channel/"))
        assertNull(ChannelIdentity.from(null))
        assertNull(ChannelIdentity.from("  "))
    }
}
