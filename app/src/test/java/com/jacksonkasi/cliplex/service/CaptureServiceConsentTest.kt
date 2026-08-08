package com.jacksonkasi.cliplex.service

import android.app.Activity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureServiceConsentTest {
	@Test fun acceptsAndroidResultOkValue() {
		assertTrue(CaptureService.isProjectionConsentAccepted(Activity.RESULT_OK, hasResultData = true))
	}

	@Test fun rejectsCanceledOrMissingConsentData() {
		assertFalse(CaptureService.isProjectionConsentAccepted(Activity.RESULT_CANCELED, hasResultData = true))
		assertFalse(CaptureService.isProjectionConsentAccepted(Activity.RESULT_OK, hasResultData = false))
	}
}
