package com.jacksonkasi.cliplex.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordLanguageTest {
	@Test
	fun scriptOverridesIncorrectSavedLanguage() {
		assertEquals("hi", languageForWord("भाई", "en"))
	}

	@Test
	fun unchangedHindiTextIsNotAcceptedAsTamilMeaning() {
		assertNull(validWordTranslation("बसीकल", "बसीकल", "hi", "ta"))
	}

	@Test
	fun tamilScriptIsAcceptedAsTamilMeaning() {
		assertEquals("சகோதரன்", validWordTranslation("भाई", "சகோதரன்", "hi", "ta"))
	}
}
