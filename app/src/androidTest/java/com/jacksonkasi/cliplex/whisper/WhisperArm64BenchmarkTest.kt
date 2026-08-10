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
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Controlled physical-device benchmark. The verified production Q5_1 model must be provisioned
 * in the debug app's whisper_models directory before this test runs. Every output identifies the
 * exact source, APK, native dependencies, configuration, model, and device used for the run.
 */
@RunWith(AndroidJUnit4::class)
class WhisperArm64BenchmarkTest {
	@Test
	fun threadMatrixWritesArmBackendCsv(): Unit = runBlocking {
		val context = ApplicationProvider.getApplicationContext<Context>()
		val arguments = InstrumentationRegistry.getArguments()
		val gitCommit = arguments.getString("cliplexBenchmarkCommit", "").requireMatch(
			Regex("[0-9a-fA-F]{7,40}"),
			"Pass -e cliplexBenchmarkCommit with the source commit used to build the APK",
		).lowercase(Locale.US)
		val apkSha256 = arguments.getString("cliplexBenchmarkApkSha256", "").requireMatch(
			Regex("[0-9a-fA-F]{64}"),
			"Pass -e cliplexBenchmarkApkSha256 with the installed APK SHA-256",
		).lowercase(Locale.US)
		val configuration = if (BuildConfig.KLEIDIAI_INTEGRATION_ENABLED) {
			"kleidiai-experimental"
		} else {
			"generic"
		}
		val buildVariant = "${BuildConfig.FLAVOR}-${BuildConfig.BUILD_TYPE}"
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
		val deviceName = sanitize("${Build.MANUFACTURER}-${Build.MODEL}")
		val timestamp = FILE_TIMESTAMP_FORMATTER.format(Instant.now())
		val output = File(
			outputDirectory,
			"$deviceName-$timestamp-${gitCommit.take(7)}-$configuration-q5_1-${apkSha256.take(8)}.csv",
		)
		val powerManager = context.getSystemService(PowerManager::class.java)
		val engine = WhisperEngine()
		var referenceTranscript: String? = null
		var inferenceFailures = 0
		var validationFailures = 0
		var releaseFailure: Throwable? = null

		try {
			output.bufferedWriter().use { writer ->
				writer.appendLine(CSV_HEADER)

				fun writeRow(
					phase: String,
					run: Int,
					threads: Int,
					backend: ArmBackendDiagnostics? = null,
					diagnostics: WhisperInferenceDiagnostics? = null,
					transcript: String = "",
					errors: List<String> = emptyList(),
				) {
					val normalizedTranscript = normalize(transcript)
					val values = commonValues(
						gitCommit = gitCommit,
						buildVariant = buildVariant,
						configuration = configuration,
						apkSha256 = apkSha256,
						model = model,
						samples = samples,
					) + listOf(
						backend?.modelQuantization ?: "unknown",
						phase,
						run.toString(),
						threads.toString(),
						diagnostics?.audioContextOverride?.toString().orEmpty(),
						powerManager.currentThermalStatus.toString(),
						backend?.arm64?.toString().orEmpty(),
						backend?.neon?.toString().orEmpty(),
						backend?.dotProd?.toString().orEmpty(),
						backend?.i8mm?.toString().orEmpty(),
						backend?.kleidiAiIntegrationEnabled?.toString().orEmpty(),
						backend?.kleidiAiSourcesIncluded?.toString().orEmpty(),
						backend?.kleidiAiKernelSelectionObserved?.toString().orEmpty(),
						backend?.modelEligibleForKleidiAi?.toString().orEmpty(),
						backend?.selectedComputePath.orEmpty(),
						backend?.fallbackReason.orEmpty(),
						errors.isEmpty().toString(),
						diagnostics?.timings?.whisperInferenceMs?.let(::format).orEmpty(),
						diagnostics?.timings?.nativeTotalMs?.let(::format).orEmpty(),
						diagnostics?.timings?.kotlinTotalMs?.let(::format).orEmpty(),
						normalizedTranscript.takeIf { it.isNotEmpty() }?.let { sha256(it.toByteArray()) }.orEmpty(),
						normalizedTranscript,
						transcript,
						errors.joinToString("; "),
					)
					writer.appendLine(values.joinToString(",", transform = ::csv))
					writer.flush()
				}

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
						inferenceFailures++
						writeRow(
							phase = phase,
							run = run,
							threads = threads,
							errors = listOf(result.exceptionOrNull()?.message.orEmpty().ifBlank { "Inference failed" }),
						)
						return
					}

					val diagnostics = transcription.diagnostics
					val backend = transcription.runtime.backend
					val transcript = transcription.segments.joinToString(" ") { it.text }.trim()
					val normalizedTranscript = normalize(transcript)
					val errors = buildList {
						if (transcript.isBlank()) add("Transcript is empty")
						if (diagnostics.threadCount != threads) {
							add("Native thread count ${diagnostics.threadCount} did not match $threads")
						}
						if (diagnostics.audioContextOverride != 0) {
							add("Audio context override was ${diagnostics.audioContextOverride}, expected 0")
						}
						if (phase == "measured") {
							if (normalizedTranscript != EXPECTED_NORMALIZED_TRANSCRIPT) {
								add("Transcript did not match the canonical fixture transcript")
							}
							val reference = referenceTranscript
							if (reference == null) referenceTranscript = normalizedTranscript
							else if (reference != normalizedTranscript) {
								add("Transcript changed across thread configurations")
							}
						}
					}
					if (errors.isNotEmpty()) validationFailures++
					writeRow(phase, run, threads, backend, diagnostics, transcript, errors)
				}

				for (threads in THREAD_COUNTS) {
					repeat(WARM_UP_RUNS) { execute(threads, "warmup", it + 1) }
					repeat(MEASURED_RUNS) { execute(threads, "measured", it + 1) }
				}
			}
		} finally {
			releaseFailure = engine.releaseModel().exceptionOrNull()
			if (output.isFile) {
				context.getExternalFilesDir("benchmarks")?.let { externalDirectory ->
					externalDirectory.mkdirs()
					output.copyTo(File(externalDirectory, output.name), overwrite = false)
				}
			}
		}

		releaseFailure?.let { throw AssertionError("Failed to release benchmark model", it) }
		assertEquals("Inference failures are recorded in ${output.name}", 0, inferenceFailures)
		assertEquals("Correctness failures are recorded in ${output.name}", 0, validationFailures)
		assertEquals(
			1 + (WARM_UP_RUNS + MEASURED_RUNS) * THREAD_COUNTS.size,
			output.readLines().size,
		)
		Unit
	}

	private fun commonValues(
		gitCommit: String,
		buildVariant: String,
		configuration: String,
		apkSha256: String,
		model: ModelType,
		samples: ShortArray,
	): List<String> = listOf(
		Instant.now().toString(), gitCommit, BuildConfig.VERSION_NAME, buildVariant, configuration,
		apkSha256, BuildConfig.WHISPER_COMMIT, BuildConfig.KLEIDIAI_VERSION, Build.MANUFACTURER,
		Build.MODEL, if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "unknown",
		Build.VERSION.RELEASE, Build.VERSION.SDK_INT.toString(), Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
		INPUT_REPOSITORY_PATH, sha256(samples),
		(samples.size * 1_000L / WhisperEngine.SAMPLE_RATE_HZ).toString(), samples.size.toString(),
		WhisperEngine.SAMPLE_RATE_HZ.toString(), model.fileName, model.sha256,
	)

	private fun String.requireMatch(regex: Regex, message: String): String = also {
		require(regex.matches(it)) { message }
	}

	private fun sanitize(value: String): String = value.lowercase(Locale.US)
		.replace(Regex("[^a-z0-9]+"), "-").trim('-')

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
		const val EXPECTED_NORMALIZED_TRANSCRIPT =
			"and so my fellow americans ask not what your country can do for you ask what you can do"
		val FILE_TIMESTAMP_FORMATTER: DateTimeFormatter =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'").withZone(ZoneOffset.UTC)
		val THREAD_COUNTS = intArrayOf(2, 4, 6, 8)
		const val WARM_UP_RUNS = 2
		const val MEASURED_RUNS = 10
		const val CSV_HEADER =
			"timestamp_utc,git_commit,app_version,build_variant,configuration,apk_sha256," +
				"whisper_commit,kleidiai_version,manufacturer,device_model,soc,android_version,sdk," +
				"abi,input,input_sha256,input_duration_ms,input_samples,sample_rate_hz,model," +
				"model_sha256,model_quantization,phase,run_index,threads,audio_ctx,thermal_status," +
				"cpu_arm64,cpu_neon,cpu_dotprod,cpu_i8mm,kleidiai_integration_enabled," +
				"kleidiai_sources_included,kleidiai_kernel_selection_observed," +
				"model_eligible_for_kleidiai,selected_compute_path,fallback_reason,success," +
				"inference_ms,native_total_ms,kotlin_total_ms,transcript_sha256," +
				"normalized_transcript,transcript,error"
	}
}
