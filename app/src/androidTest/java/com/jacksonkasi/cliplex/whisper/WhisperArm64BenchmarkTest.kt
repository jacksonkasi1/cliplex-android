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
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Controlled physical-device benchmark. The model must be provisioned in the debug app's
 * whisper_models directory before this test runs. Every native observation is flushed to CSV.
 */
@RunWith(AndroidJUnit4::class)
class WhisperArm64BenchmarkTest {
	@Test
	fun baselineVersusShortEnglishContextWritesRawCsv() = runBlocking {
		val context = ApplicationProvider.getApplicationContext<Context>()
		val model = ModelType.TINY_EN_Q5_1
		val modelFile = File(context.getDir("whisper_models", Context.MODE_PRIVATE), model.fileName)
		require(modelFile.isFile) {
			"Provision ${model.fileName} in ${modelFile.parent} before running the benchmark"
		}
		assertEquals(model.expectedByteSize, modelFile.length())
		assertEquals(model.sha256, sha256(modelFile))

		val assetBytes = InstrumentationRegistry.getInstrumentation().targetContext.assets
			.open("jfk.wav").use { it.readBytes() }
		val wav = Pcm16.readWav(assetBytes)
		val mono = if (wav.channels == 2) Pcm16.stereoToMono(wav.samples) else wav.samples
		val resampled = Pcm16.resampleLinear(mono, wav.sampleRate, WhisperEngine.SAMPLE_RATE_HZ)
		val samples = resampled.copyOfRange(0, minOf(resampled.size, 9_400 * 16))
		assertEquals(9_400L, samples.size * 1_000L / WhisperEngine.SAMPLE_RATE_HZ)

		val outputDirectory = File(context.filesDir, "benchmarks").apply { mkdirs() }
		val output = File(outputDirectory, "whisper-arm64-raw.csv")
		val commit = InstrumentationRegistry.getArguments()
			.getString("cliplexBenchmarkCommit", "unknown")
		val powerManager = context.getSystemService(PowerManager::class.java)
		val engine = WhisperEngine()
		var referenceTranscript: String? = null

		output.bufferedWriter().use { writer ->
			writer.appendLine(
				"timestamp_utc,commit,app_version,manufacturer,device_model,device,soC,android_version," +
					"sdk,abi,input,input_sha256,input_duration_ms,input_samples,sample_rate_hz," +
					"model,model_sha256,configuration,phase,run_index,threads,audio_ctx," +
					"fast_mode_applied,model_warm,thermal_status,inference_ms,native_total_ms," +
					"kotlin_total_ms,transcript_sha256,transcript",
			)

			suspend fun execute(configuration: String, optimized: Boolean, phase: String, run: Int) {
				val result = engine.ensureModelAndTranscribe(
					modelPath = modelFile.absolutePath,
					samples = samples,
					options = WhisperTranscriptionOptions(
						language = "en",
						nThreads = WhisperEngine.DEFAULT_N_THREADS,
						shortEnglishFastMode = optimized,
					),
				).getOrThrow()
				val diagnostics = result.diagnostics
				val transcript = result.segments.joinToString(" ") { it.text }.trim()
				assertTrue("Empty transcript for $configuration $phase $run", transcript.isNotBlank())
				if (phase == "measured") {
					val expected = referenceTranscript
					if (expected == null) referenceTranscript = normalize(transcript)
					else assertEquals("Transcript changed for $configuration run $run", expected, normalize(transcript))
				}
				assertEquals(optimized, diagnostics.fastModeApplied)
				assertEquals(if (optimized) 512 else 0, diagnostics.audioContextOverride)

				val values = listOf(
					Instant.now().toString(), commit, BuildConfig.VERSION_NAME,
					Build.MANUFACTURER, Build.MODEL, Build.DEVICE,
					if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "unknown",
					Build.VERSION.RELEASE, Build.VERSION.SDK_INT.toString(), Build.SUPPORTED_ABIS.first(),
					"native/whisper.cpp/samples/jfk.wav:first-9.4s-pcm16",
					sha256(samples), diagnostics.audioDurationMs.toString(), diagnostics.sampleCount.toString(),
					WhisperEngine.SAMPLE_RATE_HZ.toString(), model.fileName, model.sha256,
					configuration, phase, run.toString(), diagnostics.threadCount.toString(),
					diagnostics.audioContextOverride.toString(), diagnostics.fastModeApplied.toString(),
					diagnostics.modelWasWarm.toString(), powerManager.currentThermalStatus.toString(),
					format(diagnostics.timings.whisperInferenceMs),
					format(diagnostics.timings.nativeTotalMs),
					format(diagnostics.timings.kotlinTotalMs), sha256(transcript.toByteArray()), transcript,
				)
				writer.appendLine(values.joinToString(",", transform = ::csv))
				writer.flush()
			}

			repeat(WARM_UP_RUNS) { execute("baseline", false, "warmup", it + 1) }
			repeat(MEASURED_RUNS) { execute("baseline", false, "measured", it + 1) }
			repeat(WARM_UP_RUNS) { execute("optimized", true, "warmup", it + 1) }
			repeat(MEASURED_RUNS) { execute("optimized", true, "measured", it + 1) }
		}
		engine.releaseModel().getOrThrow()
		assertEquals(1 + (WARM_UP_RUNS + MEASURED_RUNS) * 2, output.readLines().size)
	}

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
		const val WARM_UP_RUNS = 2
		const val MEASURED_RUNS = 5
	}
}
