package com.blueprintnerd.openassistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blueprintnerd.openassistant.OpenAssistantApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? OpenAssistantApp ?: return
        if (app.preferences.wakeWordEnabled) {
            WakeWordForegroundService.start(context)
        }
    }
}
