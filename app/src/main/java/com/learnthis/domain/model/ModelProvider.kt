package com.learnthis.domain.model

/**
 * Supplies only model artifacts whose byte count and checksum have already been verified.
 * The resolver uses this boundary for tiers that do not yet have a catalog model.
 */
fun interface ModelProvider {
	fun verifiedModelFor(
		learningLanguage: LearningLanguage,
		speechQuality: SpeechQuality,
	): ModelType?

	companion object {
		val NONE: ModelProvider = ModelProvider { _, _ -> null }
	}
}
