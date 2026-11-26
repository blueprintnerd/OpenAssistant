package com.blueprintnerd.openassistant.service

import android.service.voice.VoiceInteractionService

class OpenAssistantInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        val app = application as? com.blueprintnerd.openassistant.OpenAssistantApp ?: return
        if (app.preferences.wakeWordEnabled) {
            WakeWordForegroundService.start(this)
        }
    }
}
