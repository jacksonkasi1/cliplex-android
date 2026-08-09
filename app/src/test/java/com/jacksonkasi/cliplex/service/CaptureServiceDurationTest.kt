package com.jacksonkasi.cliplex.service

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureServiceDurationTest {
    @Test
    fun captureLimitIsThreeMinutes() {
        assertEquals(180, CaptureService.MAX_CAPTURE_DURATION_SECONDS)
        assertEquals(3, CaptureService.MAX_CAPTURE_DURATION_SECONDS / 60)
    }
}
