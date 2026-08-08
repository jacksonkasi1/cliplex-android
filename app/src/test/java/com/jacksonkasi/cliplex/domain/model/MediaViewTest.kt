package com.jacksonkasi.cliplex.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaViewTest {
	@Test fun videoIsDefaultWhenSessionHasVideo() {
		assertEquals(MediaView.AUDIO, defaultMediaView(hasVideo = true))
	}

	@Test fun audioIsDefaultWhenSessionHasNoVideo() {
		assertEquals(MediaView.AUDIO, defaultMediaView(hasVideo = false))
	}

	@Test fun deletingVideoForcesAnActiveVideoViewToAudio() {
		assertEquals(MediaView.AUDIO, MediaView.VIDEO.availableWhen(hasVideo = false))
	}
}
