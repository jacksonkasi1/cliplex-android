package com.jacksonkasi.cliplex.ui.viewmodel

import com.jacksonkasi.cliplex.domain.model.LearningMode
import com.jacksonkasi.cliplex.domain.model.ModelDownloadProgress
import com.jacksonkasi.cliplex.domain.model.ModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ModelManagementStateTest {
	@Test
	fun cancelledDownloadReturnsToIdleSoReselectionCanRetry() {
		val cancelledModel = ModelType.TINY_EN_Q5_1
		val selectedMode = LearningMode.MULTILINGUAL
		val state = ModelManagementUiState(
			modes = listOf(
				LearningModeItemUiState(
					learningMode = LearningMode.ENGLISH_ONLY,
					progress = ModelDownloadProgress.Downloading(1_024L, cancelledModel.expectedByteSize),
				),
				LearningModeItemUiState(
					learningMode = selectedMode,
					progress = ModelDownloadProgress.Idle,
				),
			),
			selectedLearningMode = selectedMode,
			isChecking = false,
		)

		val reset = state.resetCancelledDownloads(setOf(cancelledModel))

		assertSame(
			ModelDownloadProgress.Idle,
			reset.modes.single { it.modelType == cancelledModel }.progress,
		)
		assertSame(selectedMode, reset.selectedLearningMode)
	}

	@Test
	fun cancellationDoesNotRegressAlreadyDownloadedOrUnrelatedCards() {
		val downloaded = LearningModeItemUiState(
			learningMode = LearningMode.ENGLISH_ONLY,
			isDownloaded = true,
			progress = ModelDownloadProgress.Verifying(),
		)
		val unrelatedError = ModelDownloadProgress.Error("offline")
		val state = ModelManagementUiState(
			modes = listOf(
				downloaded,
				LearningModeItemUiState(
					learningMode = LearningMode.MULTILINGUAL,
					progress = unrelatedError,
				),
			),
			isChecking = false,
		)

		val reset = state.resetCancelledDownloads(setOf(downloaded.modelType))

		assertSame(ModelDownloadProgress.Ready, reset.modes.first().progress)
		assertEquals(unrelatedError, reset.modes.last().progress)
	}
}
