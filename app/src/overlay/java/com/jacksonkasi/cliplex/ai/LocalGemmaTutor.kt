package com.jacksonkasi.cliplex.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.jacksonkasi.cliplex.common.AppLanguage
import com.jacksonkasi.cliplex.data.local.SessionEntity
import com.jacksonkasi.cliplex.data.local.SessionSegmentsCodec
import com.jacksonkasi.cliplex.translation.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Optional lesson-grounded Gemma tutor used by the full overlay flavor. */
class LocalGemmaTutor(private val context: Context) {
    companion object {
        const val MODEL_FILE_NAME = "gemma3-1b-it-int4.litertlm"
        private const val TAG = "LocalGemmaTutor"
    }

    private val initializationMutex = Mutex()
    private val inferenceMutex = Mutex()
    private val tutorTranslator = TranslationEngine()
    @Volatile private var engine: Engine? = null

    val modelFile: File
        get() = File(File(context.filesDir, "models"), MODEL_FILE_NAME)

    fun isInstalled(): Boolean = modelFile.isFile && modelFile.length() > 500_000_000L

    suspend fun answer(session: SessionEntity, question: String, motherTongue: String): String? =
        withContext(Dispatchers.IO) {
            if (!isInstalled()) return@withContext null
            inferenceMutex.withLock {
                runCatching {
                    val lesson = SessionSegmentsCodec.decode(session.segmentsJson)
                        .joinToString("\n") { segment ->
                            "- ${segment.text}" + segment.translatedText?.takeIf { it.isNotBlank() }
                                ?.let { " => $it" }.orEmpty()
                        }.take(6_000)
                    val instruction = """
                        You are a private language tutor. Explain clearly in English using only the captured lesson below.
                        If the lesson does not support an answer, clearly say so. Never invent lesson facts.
                        Do not guess the meaning of symbols or words when no translation is provided.
                        For every important word not written in Latin script, also show its pronunciation in English letters.
                        Keep the explanation focused and under 100 words.

                        CAPTURED LESSON:
                        $lesson
                    """.trimIndent()
                    ensureEngine().createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(instruction),
                            samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.3),
                        ),
                    ).use { conversation -> conversation.sendMessage(question).toString().trim() }
                        .let { localize(it, motherTongue) }
                }.onFailure { Log.e(TAG, "Gemma response failed", it) }
                    .getOrNull()?.takeIf { it.isNotBlank() }
            }
        }

    private suspend fun localize(answer: String, motherTongue: String): String {
        val target = AppLanguage.entries.firstOrNull { it.displayName.equals(motherTongue, ignoreCase = true) }
            ?: return answer
        if (target == AppLanguage.ENGLISH) return answer
        return tutorTranslator.initialize(AppLanguage.ENGLISH.tag, target.tag)
            .mapCatching { tutorTranslator.translate(answer).getOrThrow() }
            .getOrElse { answer }
    }

    private suspend fun ensureEngine(): Engine {
        engine?.let { return it }
        return initializationMutex.withLock {
            engine ?: Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    cacheDir = File(context.cacheDir, "gemma").apply { mkdirs() }.absolutePath,
                ),
            ).also { newEngine ->
                newEngine.initialize()
                engine = newEngine
            }
        }
    }
}
