package com.jacksonkasi.cliplex.common

import android.icu.text.Transliterator
import java.text.Normalizer

/** Returns a simple Latin-letter reading aid for words written in another script. */
fun latinPronunciation(text: String): String? {
	val original = text.trim()
	if (original.isBlank() || original.none { it.isLetter() && !it.isLatinLetter() }) return null

	val latin = runCatching {
		Transliterator.getInstance("Any-Latin").transliterate(original)
	}.getOrNull() ?: return null

	return Normalizer.normalize(latin, Normalizer.Form.NFD)
		.replace(Regex("\\p{M}+"), "")
		.replace(Regex("(?<=\\p{L})['’](?=\\p{L})"), "")
		.replace(Regex("\\s+"), " ")
		.trim()
		.takeIf { it.isNotBlank() && !it.equals(original, ignoreCase = true) }
}

private fun Char.isLatinLetter(): Boolean = when (Character.UnicodeScript.of(code)) {
	Character.UnicodeScript.LATIN, Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED -> true
	else -> false
}
