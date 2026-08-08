package com.jacksonkasi.cliplex.common

/** Uses the word's writing system before trusting potentially stale recognition metadata. */
fun languageForWord(word: String, fallback: String? = null): String {
	val detected = when {
		word.hasScript(Character.UnicodeScript.DEVANAGARI) -> "hi"
		word.hasScript(Character.UnicodeScript.TAMIL) -> "ta"
		word.hasScript(Character.UnicodeScript.TELUGU) -> "te"
		word.hasScript(Character.UnicodeScript.KANNADA) -> "kn"
		word.hasScript(Character.UnicodeScript.MALAYALAM) -> "ml"
		word.hasScript(Character.UnicodeScript.BENGALI) -> "bn"
		word.hasScript(Character.UnicodeScript.GUJARATI) -> "gu"
		word.hasScript(Character.UnicodeScript.ARABIC) -> "ar"
		word.hasScript(Character.UnicodeScript.HAN) -> "zh"
		word.hasScript(Character.UnicodeScript.HANGUL) -> "ko"
		word.hasScript(Character.UnicodeScript.CYRILLIC) -> "ru"
		word.hasScript(Character.UnicodeScript.GREEK) -> "el"
		word.hasScript(Character.UnicodeScript.HEBREW) -> "he"
		word.hasScript(Character.UnicodeScript.THAI) -> "th"
		word.hasScript(Character.UnicodeScript.GEORGIAN) -> "ka"
		else -> null
	}
	return detected ?: fallback?.substringBefore('-')?.takeIf(String::isNotBlank) ?: "en"
}

/** Rejects unchanged text or text written in a script that cannot match the requested language. */
fun validWordTranslation(source: String, translated: String?, sourceLanguage: String, targetLanguage: String): String? {
	val value = translated?.trim()?.takeIf(String::isNotBlank) ?: return null
	if (sourceLanguage.substringBefore('-') == targetLanguage.substringBefore('-')) return value
	if (value.equals(source.trim(), ignoreCase = true)) return null

	val expectedScripts = scriptsForLanguage(targetLanguage.substringBefore('-')) ?: return value
	return value.takeIf { candidate -> expectedScripts.any(candidate::hasScript) }
}

private fun scriptsForLanguage(language: String): Set<Character.UnicodeScript>? = when (language) {
	"hi", "mr" -> setOf(Character.UnicodeScript.DEVANAGARI)
	"ta" -> setOf(Character.UnicodeScript.TAMIL)
	"te" -> setOf(Character.UnicodeScript.TELUGU)
	"kn" -> setOf(Character.UnicodeScript.KANNADA)
	"ml" -> setOf(Character.UnicodeScript.MALAYALAM)
	"bn" -> setOf(Character.UnicodeScript.BENGALI)
	"gu" -> setOf(Character.UnicodeScript.GUJARATI)
	"ur", "ar", "fa" -> setOf(Character.UnicodeScript.ARABIC)
	"zh" -> setOf(Character.UnicodeScript.HAN)
	"ja" -> setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA)
	"ko" -> setOf(Character.UnicodeScript.HANGUL)
	"ru", "uk", "be", "bg", "mk" -> setOf(Character.UnicodeScript.CYRILLIC)
	"el" -> setOf(Character.UnicodeScript.GREEK)
	"he" -> setOf(Character.UnicodeScript.HEBREW)
	"th" -> setOf(Character.UnicodeScript.THAI)
	"ka" -> setOf(Character.UnicodeScript.GEORGIAN)
	"en", "fr", "de", "es", "pt", "it", "tr", "vi", "id", "ms", "nl", "pl", "ro",
	"sw", "af", "sq", "ca", "hr", "cs", "da", "et", "fi", "gl", "hu", "is", "ga",
	"lv", "lt", "mt", "no", "sk", "sl", "sv", "cy" -> setOf(Character.UnicodeScript.LATIN)
	else -> null
}

private fun String.hasScript(script: Character.UnicodeScript): Boolean = any {
	it.isLetter() && Character.UnicodeScript.of(it.code) == script
}
