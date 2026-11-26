package com.blueprintnerd.openassistant.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Log
import com.blueprintnerd.openassistant.data.AssistantPreferences
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class LocalTtsEngine(
    context: Context,
    private val modelManager: ModelManager,
    private val preferences: AssistantPreferences,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var piperTts: OfflineTts? = null

    @Volatile
    private var androidTts: TextToSpeech? = null

    @Volatile
    private var androidTtsReady = false

    @Volatile
    private var activeTrack: AudioTrack? = null

    private var speakJob: Job? = null

    init {
        androidTts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                androidTts?.language = Locale.US
                androidTtsReady = true
            }
        }
    }

    fun playActivationChime() {
        if (!preferences.chimeEnabled) return
        scope.launch {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 140)
                kotlinx.coroutines.delay(180)
                tone.release()
            }
        }
    }

    fun speak(text: String) {
        if (!preferences.speakResponses || text.isBlank()) return
        stop()
        val clean = text.replace(Regex("\\s+"), " ").trim()

        if (preferences.preferPiperTts && modelManager.isInstalled(ModelKind.TTS)) {
            speakJob = scope.launch {
                val spoken = speakWithPiper(clean)
                if (!spoken) {
                    speakWithAndroidTts(clean)
                }
            }
        } else {
            speakWithAndroidTts(clean)
        }
    }

    @Synchronized
    private fun ensurePiper(): OfflineTts? {
        piperTts?.let { return it }
        if (!modelManager.isInstalled(ModelKind.TTS)) return null
        val dir = modelManager.getModelDir(ModelKind.TTS)
        val modelFile = dir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") } ?: return null
        val tokensFile = File(dir, "tokens.txt").takeIf { it.exists() } ?: return null
        val espeakDir = File(dir, "espeak-ng-data").takeIf { it.exists() }?.absolutePath ?: ""

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    dataDir = espeakDir,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        )
        return try {
            OfflineTts(assetManager = null, config = config).also {
                piperTts = it
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Piper OfflineTts", e)
            null
        }
    }

    private fun speakWithPiper(text: String): Boolean {
        val tts = ensurePiper() ?: return false
        return try {
            val audio = tts.generate(text = text, sid = 0, speed = 1.05f)
            if (audio.samples.isEmpty()) return false
            playPcm(audio.samples, audio.sampleRate)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Piper TTS synthesis failed", e)
            false
        }
    }

    private fun playPcm(samples: FloatArray, sampleRate: Int) {
        val pcm16 = ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        activeTrack = track
        try {
            track.play()
            track.write(pcm16, 0, pcm16.size)
            track.stop()
        } finally {
            runCatching { track.release() }
            if (activeTrack === track) activeTrack = null
        }
    }

    private fun speakWithAndroidTts(text: String) {
        if (androidTtsReady) {
            androidTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "oa_utterance")
        }
    }

    fun stop() {
        speakJob?.cancel()
        speakJob = null
        activeTrack?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        activeTrack = null
        androidTts?.stop()
    }

    fun release() {
        stop()
        piperTts?.let { runCatching { it.release() } }
        piperTts = null
        androidTts?.shutdown()
        androidTts = null
    }

    companion object {
        private const val TAG = "LocalTtsEngine"
    }
}
