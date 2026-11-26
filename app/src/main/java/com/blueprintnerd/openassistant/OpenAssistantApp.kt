package com.blueprintnerd.openassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.blueprintnerd.openassistant.data.AssistantPreferences
import com.blueprintnerd.openassistant.engine.AndroidDeviceActionExecutor
import com.blueprintnerd.openassistant.engine.CommandRouter
import com.blueprintnerd.openassistant.engine.WebKnowledgeClient
import com.blueprintnerd.openassistant.speech.LocalTtsEngine
import com.blueprintnerd.openassistant.speech.ModelManager
import com.blueprintnerd.openassistant.speech.SherpaSttEngine
import com.blueprintnerd.openassistant.speech.SherpaWakeWordEngine

class OpenAssistantApp : Application() {
    lateinit var preferences: AssistantPreferences
        private set
    lateinit var modelManager: ModelManager
        private set
    lateinit var wakeWordEngine: SherpaWakeWordEngine
        private set
    lateinit var sttEngine: SherpaSttEngine
        private set
    lateinit var ttsEngine: LocalTtsEngine
        private set
    lateinit var deviceActions: AndroidDeviceActionExecutor
        private set
    lateinit var commandRouter: CommandRouter
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AssistantPreferences(this)
        modelManager = ModelManager(this)
        wakeWordEngine = SherpaWakeWordEngine(modelManager, preferences)
        sttEngine = SherpaSttEngine(modelManager)
        ttsEngine = LocalTtsEngine(this, modelManager, preferences)
        deviceActions = AndroidDeviceActionExecutor(this, preferences)
        commandRouter = CommandRouter(deviceActions, preferences, WebKnowledgeClient())

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_WAKE_WORD,
                    "Wake Word Listener",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "On-device wake word detection (Hey Google / custom phrases)"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Local OpenAssistant reminders"
                }
            )
        }
    }

    companion object {
        const val CHANNEL_WAKE_WORD = "openassistant_wake_word"
        const val CHANNEL_REMINDERS = "openassistant_reminders"
    }
}
