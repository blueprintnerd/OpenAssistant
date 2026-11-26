package com.blueprintnerd.openassistant.engine

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.blueprintnerd.openassistant.data.AssistantPreferences
import com.blueprintnerd.openassistant.data.MusicProvider
import com.blueprintnerd.openassistant.data.ReminderItem
import com.blueprintnerd.openassistant.service.ReminderReceiver
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

interface DeviceActions {
    fun setFlashlight(enabled: Boolean): String
    fun adjustVolume(direction: Int): String
    fun setVolumePercent(percent: Int): String
    fun setMute(mute: Boolean): String
    fun getBatteryStatus(): String
    fun setTimer(seconds: Int, label: String?): String
    fun showTimers(): String
    fun setAlarm(hour24: Int, minute: Int, message: String?): String
    fun showAlarms(): String
    fun launchApp(appNameQuery: String): String?
    fun openCamera(selfie: Boolean = false): String
    fun callTarget(target: String): String
    fun sendSms(target: String, message: String): String
    fun navigateTo(destination: String): String
    fun sendMediaKey(keyCode: Int, actionLabel: String): String
    fun playMediaSearch(query: String, provider: MusicProvider? = null): String
    fun openSettingsPanel(kind: String): String
    fun createCalendarEvent(title: String): String
    fun scheduleReminder(text: String, delayMillis: Long): String
    fun scheduleReminderAt(text: String, triggerAtMillis: Long, timeLabel: String): String
    fun getLastKnownCoordinates(): Pair<Double, Double>?
    fun openWebSearch(query: String): String
    fun openUrl(url: String): Boolean
}

class AndroidDeviceActionExecutor(
    private val context: Context,
    private val preferences: AssistantPreferences,
) : DeviceActions {

    override fun setFlashlight(enabled: Boolean): String {
        val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return "Camera service unavailable."
        return runCatching {
            val cameraId = camManager.cameraIdList.firstOrNull { id ->
                camManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "No flashlight found on this device."
            camManager.setTorchMode(cameraId, enabled)
            if (enabled) "Flashlight turned on." else "Flashlight turned off."
        }.getOrElse {
            "Could not toggle flashlight: ${it.message}"
        }
    }

    override fun adjustVolume(direction: Int): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI,
        )
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val pct = (cur * 100) / max
        return if (direction == AudioManager.ADJUST_RAISE) {
            "Volume turned up to $pct percent."
        } else {
            "Volume turned down to $pct percent."
        }
    }

    override fun setVolumePercent(percent: Int): String {
        val clamped = percent.coerceIn(0, 100)
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = ((clamped / 100.0) * max).toInt().coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return "Media volume set to $clamped percent."
    }

    override fun setMute(mute: Boolean): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val dir = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        return if (mute) "Volume muted." else "Volume unmuted."
    }

    override fun getBatteryStatus(): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        if (level < 0) return "Could not read battery level."
        val pct = (level * 100) / scale.coerceAtLeast(1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return if (charging) "Battery is at $pct percent and charging." else "Battery is at $pct percent."
    }

    override fun setTimer(seconds: Int, label: String?): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (!label.isNullOrBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (startSafe(intent)) {
            val formatted = formatDuration(seconds)
            if (!label.isNullOrBlank()) "Timer set for $formatted ($label)." else "Timer set for $formatted."
        } else {
            "No clock app available to set a timer."
        }
    }

    override fun showTimers(): String {
        val intent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (startSafe(intent)) "Opening your timers." else "No clock app found."
    }

    override fun setAlarm(hour24: Int, minute: Int, message: String?): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour24)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (!message.isNullOrBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val amPm = if (hour24 >= 12) "PM" else "AM"
        val h12 = when (val h = hour24 % 12) {
            0 -> 12
            else -> h
        }
        val timeText = String.format(Locale.US, "%d:%02d %s", h12, minute, amPm)
        return if (startSafe(intent)) {
            "Alarm set for $timeText."
        } else {
            "No clock app available to set an alarm."
        }
    }

    override fun showAlarms(): String {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (startSafe(intent)) "Opening your alarms." else "No clock app found."
    }

    override fun launchApp(appNameQuery: String): String? {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)
        val needle = appNameQuery.trim().lowercase(Locale.US)

        val exact = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase(Locale.US) == needle
        }
        val startsWith = exact ?: apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase(Locale.US).startsWith(needle)
        }
        val contains = startsWith ?: apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase(Locale.US).contains(needle)
        } ?: return null

        val pkg = contains.activityInfo.packageName
        val label = contains.loadLabel(pm).toString()
        val launchIntent = pm.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return null

        return if (startSafe(launchIntent)) "Opening $label." else null
    }

    override fun openCamera(selfie: Boolean): String {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (selfie) {
                putExtra("android.intent.extras.CAMERA_FACING", 1)
                putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            }
        }
        return if (startSafe(intent)) {
            if (selfie) "Opening front camera." else "Opening camera."
        } else {
            "Could not open camera."
        }
    }

    override fun callTarget(target: String): String {
        val resolved = resolvePhoneNumber(target)
        val number = resolved?.second ?: target.filter { it.isDigit() || it == '+' }
        val display = resolved?.first ?: target
        if (number.isBlank()) {
            return "Could not find a phone number for $target."
        }
        val hasCallPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val action = if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (startSafe(intent)) {
            if (hasCallPerm) "Calling $display." else "Dialing $display."
        } else {
            "No phone app available."
        }
    }

    override fun sendSms(target: String, message: String): String {
        val resolved = resolvePhoneNumber(target)
        val number = resolved?.second ?: target.filter { it.isDigit() || it == '+' }
        val display = resolved?.first ?: target
        if (number.isBlank()) {
            return "Could not find a phone number for $target."
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (startSafe(intent)) {
            "Drafting text to $display."
        } else {
            "No messaging app available."
        }
    }

    override fun navigateTo(destination: String): String {
        val encoded = URLEncoder.encode(destination.trim(), "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (startSafe(intent)) {
            "Opening navigation to $destination."
        } else {
            openWebSearch("directions to $destination")
        }
    }

    override fun sendMediaKey(keyCode: Int, actionLabel: String): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        return actionLabel
    }

    override fun playMediaSearch(query: String, provider: MusicProvider?): String {
        val chosen = provider ?: preferences.musicProvider
        val targetPkg = chosen.packageName

        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(android.app.SearchManager.QUERY, query)
            if (targetPkg != null) setPackage(targetPkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startSafe(intent)) {
            return if (targetPkg != null) "Playing $query on ${chosen.label}." else "Playing $query."
        }

        // Fallback to direct app search URIs for Spotify and YouTube Music
        val enc = URLEncoder.encode(query.trim(), "UTF-8")
        return when (chosen) {
            MusicProvider.SPOTIFY -> {
                val spotifyUri = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$enc")).apply {
                    setPackage("com.spotify.music")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (startSafe(spotifyUri)) {
                    "Searching Spotify for $query."
                } else {
                    openUrl("https://open.spotify.com/search/$enc")
                    "Opening Spotify web search for $query."
                }
            }
            MusicProvider.YT_MUSIC -> {
                val ytmIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$enc")).apply {
                    setPackage("com.google.android.apps.youtube.music")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (startSafe(ytmIntent)) {
                    "Searching YouTube Music for $query."
                } else {
                    openUrl("https://music.youtube.com/search?q=$enc")
                    "Opening YouTube Music for $query."
                }
            }
            MusicProvider.AUTO -> "No media player responded to play $query."
        }
    }

    override fun openSettingsPanel(kind: String): String {
        val action = when (kind) {
            "wifi" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "brightness", "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "volume", "sound" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "assistant" -> Settings.ACTION_VOICE_INPUT_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return if (startSafe(intent)) "Opening $kind settings." else "Could not open settings."
    }

    override fun createCalendarEvent(title: String): String {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (startSafe(intent)) "Creating calendar event: $title." else "No calendar app found."
    }

    override fun scheduleReminder(text: String, delayMillis: Long): String {
        val triggerAt = System.currentTimeMillis() + delayMillis.coerceAtLeast(10_000L)
        val mins = (delayMillis / 60_000L).coerceAtLeast(1)
        return scheduleReminderAt(text, triggerAt, "in ${formatDuration((mins * 60).toInt())}")
    }

    override fun scheduleReminderAt(text: String, triggerAtMillis: Long, timeLabel: String): String {
        val triggerAt = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 10_000L)
        val item = ReminderItem(
            id = UUID.randomUUID().toString(),
            text = text,
            triggerAtMillis = triggerAt,
        )
        preferences.saveReminders(preferences.getReminders() + item)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, item.id)
            putExtra(ReminderReceiver.EXTRA_REMINDER_TEXT, item.text)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
        return "Reminder set: $text ($timeLabel)."
    }

    override fun getLastKnownCoordinates(): Pair<Double, Double>? {
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasCoarse) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return runCatching {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
            val best = providers.mapNotNull { p ->
                runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            }.maxByOrNull { it.time }
            best?.let { it.latitude to it.longitude }
        }.getOrNull()
    }

    override fun openWebSearch(query: String): String {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = String.format(preferences.searchEngine.searchUrlTemplate, encoded)
        openUrl(url)
        return "Searching ${preferences.searchEngine.label} for $query."
    }

    override fun openUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafe(intent)
    }

    private fun resolvePhoneNumber(nameQuery: String): Pair<String, String>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        return runCatching {
            context.contentResolver.query(
                uri,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%${nameQuery.trim()}%"),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0) to cursor.getString(1)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun startSafe(intent: Intent): Boolean = runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    companion object {
        fun formatDuration(totalSeconds: Int): String {
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return buildList {
                if (h > 0) add("$h ${if (h == 1) "hour" else "hours"}")
                if (m > 0) add("$m ${if (m == 1) "minute" else "minutes"}")
                if (s > 0 && h == 0) add("$s ${if (s == 1) "second" else "seconds"}")
            }.joinToString(" ").ifBlank { "0 seconds" }
        }
    }
}
