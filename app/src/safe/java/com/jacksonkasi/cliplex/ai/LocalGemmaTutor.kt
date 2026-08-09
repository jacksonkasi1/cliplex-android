package com.jacksonkasi.cliplex.ai

import android.content.Context
import com.jacksonkasi.cliplex.data.local.SessionEntity
import java.io.File

/**
 * Compact safe-distribution implementation.
 *
 * The safe flavor keeps the deterministic lesson-grounded tutor and deliberately does not package
 * LiteRT-LM. This keeps the direct-download APK small while preserving private, offline answers
 * through PracticeEngine's fallback.
 */
class LocalGemmaTutor(private val context: Context) {
    companion object {
        const val MODEL_FILE_NAME = "gemma3-1b-it-int4.litertlm"
    }

    val modelFile: File
        get() = File(File(context.filesDir, "models"), MODEL_FILE_NAME)

    fun isInstalled(): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    suspend fun answer(session: SessionEntity, question: String, motherTongue: String): String? = null
}
