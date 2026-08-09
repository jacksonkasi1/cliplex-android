package com.jacksonkasi.cliplex.whisper

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jacksonkasi.cliplex.BuildConfig
import com.jacksonkasi.cliplex.audio.Pcm16
import com.jacksonkasi.cliplex.domain.model.ModelType
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Controlled physical-device benchmark. The verified production Q5_1 model must be provisioned
 * in the debug app's whisper_models directory before this test runs. The matrix deliberately keeps
 * audio context and model fixed so thread/backend results remain comparable.
 */
@RunWith(AndroidJUnit4::class)
class WhisperArm64BenchmarkTest {
	@Test
	fun threadMatrixWritesArmBackendCsv(): Unit = runBlocking {
		val context = ApplicationProvider.getApplicationContext<Context>()
		val model = ModelType.TINY_EN_Q5_1
		val modelFile = File(context.getDir("whisper_models", Context.MODE_PRIVATE), model.fileName)
		require(modelFile.isFile) {
			"Provision ${model.fileName} in ${modelFile.parent} before running the benchmark"
		}
		assertEquals(model.expectedByteSize, modelFile.length())
		assertEquals(model.sha256, sha256(modelFile))

		val assetBytes = InstrumentationRegistry.getInstrumentation().targetContext.assets
			.open(INPUT_ASSET_NAME).use { it.readBytes() }
		val wav = Pcm16.readWav(assetBytes)
		val mono = if (wav.channels == 2) Pcm16.stereoToMono(wav.samples) else wav.samples
		val resampled = Pcm16.resampleLinear(mono, wav.sampleRate, WhisperEngine.SAMPLE_RATE_HZ)
		val samples = resampled.copyOfRange(0, minOf(resampled.size, 9_400 * 16))
		assertEquals(9_400L, samples.size * 1_000L / WhisperEngine.SAMPLE_RATE_HZ)

		val outputDirectory = File(context.filesDir, "benchmarks").apply { mkdirs() }
		val deviceName = "${Build.MANUFACTURER}-${Build.MODEL}"
			.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
		val output = File(
			outputDirectory,
			"$deviceName-${LocalDate.now(ZoneOffset.UTC)}-arm-kernel.csv",
		)
		val arguments = InstrumentationRegistry.getArguments()
		val commit = arguments.getString("cliplexBenchmarkCommit", "unknown")
		val configurationArgument = arguments.getString("cliplexBenchmarkConfiguration", "")
		val powerManager = context.getSystemService(PowerManager::class.java)
		val engine = WhisperEngine()
		var referenceTranscript: String? = null
		var failures = 0

		output.bufferedWriter().use { writer ->
			writer.appendLine(
				"timestamp_utc,commit,app_version,manufacturer,device_model,soc,android_version,sdk," +
					"abi,input,input_sha256,input_duration_ms,input_samples,sample_rate_hz,model," +
					"model_sha256,model_quantization,configuration,phase,run_index,threads,audio_ctx," +
					"thermal_status,cpu_arm64,cpu_neon,cpu_dotprod,cpu_i8mm,kleidiai_compiled," +
					"kleidiai_available,selected_backend,selected_kernel_path,generic_fallback," +
					"fallback_reason,success,inference_ms,native_total_ms,kotlin_total_ms," +
					"transcript_sha256,transcript,error",
			)

			suspend fun execute(threads: Int, phase: String, run: Int) {
				val result = engine.ensureModelAndTranscribe(
					modelPath = modelFile.absolutePath,
					samples = samples,
					options = WhisperTranscriptionOptions(
						language = "en",
						nThreads = threads,
						shortEnglishFastMode = false,
					),
				)
				val transcription = result.getOrNull()
				if (transcription == null) {
					failures++
					val values = commonValues(commit, model, samples) + listOf(
						"unknown", configurationArgument.ifBlank { "unknown" }, phase, run.toString(),
						threads.toString(), "0", powerManager.currentThermalStatus.toString(),
						"", "", "", "", "", "", "", "", "", "", "false", "", "", "", "",
						"", result.exceptionOrNull()?.message.orEmpty(),
					)
					writer.appendLine(values.joinToString(",", transform = ::csv))
					writer.flush()
					return
				}

				val diagnostics = transcription.diagnostics
				val backend = transcription.runtime.backend
				val transcript = transcription.segments.joinToString(" ") { it.text }.trim()
				assertTrue("Empty transcript for $threads threads $phase $run", transcript.isNotBlank())
				if (phase == "measured") {
					val expected = referenceTranscript
					if (expected == null) referenceTranscript = normalize(transcript)
					else assertEquals("Transcript changed for $threads threads run $run", expected, normalize(transcript))
				}
				assertEquals(threads, diagnostics.threadCount)
				assertEquals(0, diagnostics.audioContextOverride)

				val configuration = configurationArgument.ifBlank {
					if (backend.kleidiAiCompiled) "kleidiai-compiled" else "generic"
				}
				val values = commonValues(commit, model, samples) + listOf(
					backend.modelQuantization, configuration, phase, run.toString(), threads.toString(),
					diagnostics.audioContextOverride.toString(), powerManager.currentThermalStatus.toString(),
					backend.arm64.toString(), backend.neon.toString(), backend.dotProd.toString(),
					backend.i8mm.toString(), backend.kleidiAiCompiled.toString(),
					backend.kleidiAiAvailable.toString(), backend.selectedBackend,
					backend.selectedKernelPath, backend.genericFallback.toString(), backend.fallbackReason,
					"true", format(diagnostics.timings.whisperInferenceMs),
					format(diagnostics.timings.nativeTotalMs), format(diagnostics.timings.kotlinTotalMs),
					sha256(transcript.toByteArray()), transcript, "",
				)
				writer.appendLine(values.joinToString(",", transform = ::csv))
				writer.flush()
			}

			for (threads in THREAD_COUNTS) {
				repeat(WARM_UP_RUNS) { execute(threads, "warmup", it + 1) }
				repeat(MEASURED_RUNS) { execute(threads, "measured", it + 1) }
			}
		}
		engine.releaseModel().getOrThrow()
		assertEquals(0, failures)
		assertEquals(1 + (WARM_UP_RUNS + MEASURED_RUNS) * THREAD_COUNTS.size, output.readLines().size)

		context.getExternalFilesDir("benchmarks")?.let { externalDirectory ->
			externalDirectory.mkdirs()
			output.copyTo(File(externalDirectory, output.name), overwrite = true)
		}
		Unit
	}

	private fun commonValues(commit: String, model: ModelType, samples: ShortArray): List<String> = listOf(
		Instant.now().toString(), commit, BuildConfig.VERSION_NAME, Build.MANUFACTURER, Build.MODEL,
		if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "unknown", Build.VERSION.RELEASE,
		Build.VERSION.SDK_INT.toString(), Build.SUPPORTED_ABIS.firstOrNull().orEmpty(), INPUT_REPOSITORY_PATH,
		sha256(samples), (samples.size * 1_000L / WhisperEngine.SAMPLE_RATE_HZ).toString(),
		samples.size.toString(), WhisperEngine.SAMPLE_RATE_HZ.toString(), model.fileName, model.sha256,
	)

	private fun normalize(value: String): String = value.lowercase(Locale.US)
		.replace(Regex("[^a-z0-9]+"), " ").trim()

	private fun format(value: Double): String = String.format(Locale.US, "%.3f", value)

	private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

	private fun sha256(file: File): String = file.inputStream().buffered().use { sha256(it) }

	private fun sha256(samples: ShortArray): String = sha256(Pcm16.wav(samples, WhisperEngine.SAMPLE_RATE_HZ))

	private fun sha256(bytes: ByteArray): String = bytes.inputStream().use { sha256(it) }

	private fun sha256(input: java.io.InputStream): String {
		val digest = MessageDigest.getInstance("SHA-256")
		val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
		while (true) {
			val read = input.read(buffer)
			if (read < 0) break
			digest.update(buffer, 0, read)
		}
		return digest.digest().joinToString("") { "%02x".format(it) }
	}

	private companion object {
		const val INPUT_ASSET_NAME = "jfk-first-9.4s-16khz-mono.wav"
		const val INPUT_REPOSITORY_PATH = "benchmarks/samples/$INPUT_ASSET_NAME"
		val THREAD_COUNTS = intArrayOf(2, 4, 6, 8)
		const val WARM_UP_RUNS = 2
		const val MEASURED_RUNS = 10
	}
}
