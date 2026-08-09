package com.jacksonkasi.cliplex.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaViewTest {
    @Test
    fun `video lessons open in video mode by default`() {
        assertEquals(MediaView.VIDEO, defaultMediaView(hasVideo = true))
    }

    @Test
    fun `audio lessons and missing video safely use audio mode`() {
        assertEquals(MediaView.AUDIO, defaultMediaView(hasVideo = false))
        assertEquals(MediaView.AUDIO, MediaView.VIDEO.availableWhen(hasVideo = false))
        assertEquals(MediaView.VIDEO, MediaView.VIDEO.availableWhen(hasVideo = true))
    }
}
