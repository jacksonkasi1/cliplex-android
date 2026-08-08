package com.learnthis.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureServiceReadErrorTest {
	@Test fun reportsReadFailureWhileActive() {
		assertTrue(CaptureService.isUnexpectedAudioReadError(-3, intentionallyStopping = false, recorderIsCurrent = true))
	}

	@Test fun ignoresReadFailureDuringIntentionalOrCompletedRelease() {
		assertFalse(CaptureService.isUnexpectedAudioReadError(-3, intentionallyStopping = true, recorderIsCurrent = true))
		assertFalse(CaptureService.isUnexpectedAudioReadError(-3, intentionallyStopping = false, recorderIsCurrent = false))
		assertFalse(CaptureService.isUnexpectedAudioReadError(9600, intentionallyStopping = false, recorderIsCurrent = true))
	}
}
