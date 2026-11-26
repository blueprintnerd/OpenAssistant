package com.blueprintnerd.openassistant.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.blueprintnerd.openassistant.data.AssistantPreferences
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class SherpaWakeWordEngine(
    private val modelManager: ModelManager,
    private val preferences: AssistantPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var listenJob: Job? = null
    private val paused = AtomicBoolean(false)
    private val running = AtomicBoolean(false)

    @Volatile
    private var lastTriggerAt = 0L

    fun isRunning(): Boolean = running.get()

    fun pause() {
        paused.set(true)
    }

    fun resume() {
        paused.set(false)
    }

    @SuppressLint("MissingPermission")
    fun start(onWakeWordDetected: (String) -> Unit): Boolean {
        if (running.get()) return true
        if (!modelManager.isInstalled(ModelKind.KWS)) {
            Log.w(TAG, "KWS model not installed")
            return false
        }

        val dir = modelManager.getModelDir(ModelKind.KWS)
        val encoder = modelManager.findFile(dir, "encoder", ".onnx") ?: return false
        val decoder = modelManager.findFile(dir, "decoder", ".onnx") ?: return false
        val joiner = modelManager.findFile(dir, "joiner", ".onnx") ?: return false
        val tokens = File(dir, "tokens.txt").takeIf { it.exists() } ?: return false

        val vocab = BpeKeywordTokenizer.loadVocab(tokens)
        val keywordsContent = BpeKeywordTokenizer.buildKeywordsFileContent(
            phrases = preferences.wakeWords,
            vocab = vocab,
            boostScore = preferences.wakeWordBoostScore,
            threshold = preferences.wakeWordThreshold,
        )
        if (keywordsContent.isBlank()) {
            Log.w(TAG, "No valid wake words could be tokenized")
            return false
        }

        val keywordsFile = File(dir, "openassistant_keywords.txt")
        keywordsFile.writeText(keywordsContent)

        val config = KeywordSpotterConfig(
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
                modelType = "zipformer2",
            ),
            maxActivePaths = 4,
            keywordsFile = keywordsFile.absolutePath,
            keywordsScore = preferences.wakeWordBoostScore,
            keywordsThreshold = preferences.wakeWordThreshold,
            numTrailingBlanks = 1,
        )

        return try {
            val kws = KeywordSpotter(assetManager = null, config = config)
            val s = kws.createStream()
            spotter = kws
            stream = s
            running.set(true)
            paused.set(false)

            listenJob = scope.launch {
                runCaptureLoop(kws, s, onWakeWordDetected)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Sherpa KWS", e)
            stop()
            false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runCaptureLoop(
        kws: KeywordSpotter,
        kwsStream: OnlineStream,
        onWakeWordDetected: (String) -> Unit,
    ) {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SAMPLE_RATE / 5)

        val buffer = ShortArray(1600) // 100ms at 16kHz
        var audioRecord: AudioRecord? = null

        try {
            while (scope.isActive && running.get()) {
                if (paused.get()) {
                    audioRecord?.let {
                        runCatching { it.stop() }
                        runCatching { it.release() }
                        audioRecord = null
                    }
                    delay(120)
                    continue
                }

                if (audioRecord == null) {
                    val rec = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuf * 2,
                    )
                    if (rec.state != AudioRecord.STATE_INITIALIZED) {
                        rec.release()
                        delay(500)
                        continue
                    }
                    rec.startRecording()
                    audioRecord = rec
                }

                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    val floats = FloatArray(read) { i -> buffer[i] / 32768.0f }
                    kwsStream.acceptWaveform(floats, SAMPLE_RATE)
                    while (kws.isReady(kwsStream)) {
                        kws.decode(kwsStream)
                        val result = kws.getResult(kwsStream)
                        if (result.keyword.isNotEmpty()) {
                            kws.reset(kwsStream)
                            val now = System.currentTimeMillis()
                            if (now - lastTriggerAt > COOLDOWN_MS) {
                                lastTriggerAt = now
                                val label = result.keyword.replace("_", " ").trim()
                                Log.i(TAG, "Wake word detected: $label")
                                paused.set(true)
                                audioRecord?.let {
                                    runCatching { it.stop() }
                                    runCatching { it.release() }
                                    audioRecord = null
                                }
                                onWakeWordDetected(label)
                            }
                        }
                    }
                } else {
                    delay(50)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wake word capture loop error", e)
        } finally {
            audioRecord?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
        }
    }

    fun stop() {
        running.set(false)
        listenJob?.cancel()
        listenJob = null
        stream?.let { runCatching { it.release() } }
        stream = null
        spotter?.let { runCatching { it.release() } }
        spotter = null
    }

    companion object {
        private const val TAG = "SherpaWakeWordEngine"
        private const val SAMPLE_RATE = 16000
        private const val COOLDOWN_MS = 2000L
    }
}
