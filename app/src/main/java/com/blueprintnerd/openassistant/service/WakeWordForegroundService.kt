package com.blueprintnerd.openassistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.blueprintnerd.openassistant.OpenAssistantApp
import com.blueprintnerd.openassistant.R
import com.blueprintnerd.openassistant.ui.AssistActivity
import com.blueprintnerd.openassistant.ui.MainActivity

class WakeWordForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val app = application as OpenAssistantApp
            app.wakeWordEngine.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val app = application as OpenAssistantApp
        if (intent?.action == ACTION_RELOAD) {
            app.wakeWordEngine.stop()
        }

        val powerManager = getSystemService(PowerManager::class.java)
        val started = app.wakeWordEngine.start(
            isExternallyPaused = {
                app.preferences.pauseWakeWhenScreenOff && powerManager?.isInteractive == false
            },
        ) { detectedKeyword ->
            app.ttsEngine.playActivationChime()
            val launch = Intent(this, AssistActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(AssistActivity.EXTRA_AUTO_LISTEN, true)
                putExtra(AssistActivity.EXTRA_WAKE_PHRASE, detectedKeyword)
            }
            startActivity(launch)
        }

        if (!started) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        val app = application as OpenAssistantApp
        app.wakeWordEngine.stop()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val app = application as OpenAssistantApp
        val phrases = app.preferences.wakeWords.take(2).joinToString(" / ")
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeWordForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, OpenAssistantApp.CHANNEL_WAKE_WORD)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("OpenAssistant listening on-device")
            .setContentText("Wake phrases: $phrases")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_STOP = "com.blueprintnerd.openassistant.STOP_WAKE"
        private const val ACTION_RELOAD = "com.blueprintnerd.openassistant.RELOAD_WAKE"

        fun start(context: Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun reload(context: Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java).setAction(ACTION_RELOAD)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
