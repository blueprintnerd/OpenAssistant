package com.blueprintnerd.openassistant.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

data class SttCallbacks(
    val onPartialResult: (String) -> Unit,
    val onAmplitude: (Float) -> Unit,
    val onFinalResult: (String) -> Unit,
    val onError: (String) -> Unit,
)

class SherpaSttEngine(
    private val modelManager: ModelManager,
) {
    @Volatile
    private var recognizer: OnlineRecognizer? = null

    @Volatile
    private var stopRequested = false

    fun isModelReady(): Boolean = modelManager.isInstalled(ModelKind.ASR)

    @Synchronized
    private fun ensureRecognizer(): OnlineRecognizer? {
        recognizer?.let { return it }
        if (!modelManager.isInstalled(ModelKind.ASR)) return null

        val dir = modelManager.getModelDir(ModelKind.ASR)
        val encoder = modelManager.findFile(dir, "encoder", ".onnx") ?: return null
        val decoder = modelManager.findFile(dir, "decoder", ".onnx") ?: return null
        val joiner = modelManager.findFile(dir, "joiner", ".onnx") ?: return null
        val tokens = File(dir, "tokens.txt").takeIf { it.exists() } ?: return null

        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = decoder.absolutePath,
                    joiner = joiner.absolutePath,
                ),
                tokens = tokens.absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "zipformer",
            ),
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 3.2f, minUtteranceLength = 0f),
                rule2 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = 1.2f, minUtteranceLength = 0f),
                rule3 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0f, minUtteranceLength = 15f),
            ),
            enableEndpoint = true,
            decodingMethod = "greedy_search",
            maxActivePaths = 4,
        )

        return try {
            OnlineRecognizer(assetManager = null, config = config).also {
                recognizer = it
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Sherpa OnlineRecognizer", e)
            null
        }
    }

    fun requestStop() {
        stopRequested = true
    }

    @SuppressLint("MissingPermission")
    suspend fun listenOnce(
        maxDurationMs: Long = 12_000L,
        callbacks: SttCallbacks,
    ) = withContext(Dispatchers.IO) {
        stopRequested = false
        val rec = ensureRecognizer()
        if (rec == null) {
            callbacks.onError("On-device Speech-to-Text model is not downloaded yet. Open Models in OpenAssistant to download it.")
            return@withContext
        }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SAMPLE_RATE / 5)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2,
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            callbacks.onError("Could not access microphone.")
            return@withContext
        }

        val stream = rec.createStream()
        val buffer = ShortArray(1600) // 100ms chunks
        val startMs = System.currentTimeMillis()
        var lastPartial = ""

        try {
            audioRecord.startRecording()
            while (currentCoroutineContext().isActive && !stopRequested) {
                if (System.currentTimeMillis() - startMs > maxDurationMs) break

                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                var sumSq = 0.0
                val floats = FloatArray(read) { i ->
                    val v = buffer[i] / 32768.0f
                    sumSq += v * v
                    v
                }
                val rms = sqrt(sumSq / read).toFloat().coerceIn(0f, 1f)
                callbacks.onAmplitude((rms * 4.5f).coerceIn(0f, 1f))

                stream.acceptWaveform(floats, SAMPLE_RATE)
                while (rec.isReady(stream)) {
                    rec.decode(stream)
                }

                val text = rec.getResult(stream).text.trim()
                if (text.isNotEmpty() && text != lastPartial) {
                    lastPartial = text
                    callbacks.onPartialResult(text)
                }

                if (rec.isEndpoint(stream)) {
                    break
                }
            }

            stream.inputFinished()
            while (rec.isReady(stream)) {
                rec.decode(stream)
            }
            val finalText = rec.getResult(stream).text.trim().ifEmpty { lastPartial }
            callbacks.onAmplitude(0f)
            callbacks.onFinalResult(finalText)
        } catch (e: Exception) {
            Log.e(TAG, "STT capture failed", e)
            callbacks.onError(e.message ?: "Speech recognition error")
        } finally {
            runCatching { audioRecord.stop() }
            runCatching { audioRecord.release() }
            runCatching { stream.release() }
        }
    }

    @Synchronized
    fun release() {
        recognizer?.let { runCatching { it.release() } }
        recognizer = null
    }

    companion object {
        private const val TAG = "SherpaSttEngine"
        private const val SAMPLE_RATE = 16000
    }
}
