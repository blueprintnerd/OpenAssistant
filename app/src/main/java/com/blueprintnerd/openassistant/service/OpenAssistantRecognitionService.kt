package com.blueprintnerd.openassistant.service

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.blueprintnerd.openassistant.OpenAssistantApp
import com.blueprintnerd.openassistant.speech.SttCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenAssistantRecognitionService : RecognitionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeJob: Job? = null

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        val app = application as OpenAssistantApp
        app.wakeWordEngine.pause()

        activeJob?.cancel()
        activeJob = scope.launch {
            runCatching { listener.readyForSpeech(Bundle()) }
            app.sttEngine.listenOnce(
                callbacks = SttCallbacks(
                    onPartialResult = { partial ->
                        val b = Bundle().apply {
                            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(partial))
                        }
                        runCatching { listener.partialResults(b) }
                    },
                    onAmplitude = { amp ->
                        runCatching { listener.rmsChanged(amp * 10f) }
                    },
                    onFinalResult = { text ->
                        val b = Bundle().apply {
                            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
                        }
                        runCatching { listener.results(b) }
                        app.wakeWordEngine.resume()
                    },
                    onError = {
                        runCatching { listener.error(SpeechRecognizer.ERROR_NO_MATCH) }
                        app.wakeWordEngine.resume()
                    },
                )
            )
        }
    }

    override fun onCancel(listener: Callback) {
        val app = application as OpenAssistantApp
        app.sttEngine.requestStop()
        activeJob?.cancel()
        app.wakeWordEngine.resume()
    }

    override fun onStopListening(listener: Callback) {
        val app = application as OpenAssistantApp
        app.sttEngine.requestStop()
    }
}
