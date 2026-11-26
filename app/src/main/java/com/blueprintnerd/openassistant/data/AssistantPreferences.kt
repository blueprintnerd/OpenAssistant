package com.blueprintnerd.openassistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Routine(
    val id: String,
    val name: String,
    val triggerPhrases: List<String>,
    val actions: List<String>,
)

data class ReminderItem(
    val id: String,
    val text: String,
    val triggerAtMillis: Long,
    val done: Boolean = false,
)

enum class SearchEngine(val label: String, val searchUrlTemplate: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=%s"),
    KAGI("Kagi", "https://kagi.com/search?q=%s"),
}

enum class MusicProvider(val label: String, val packageName: String?) {
    AUTO("System Default", null),
    SPOTIFY("Spotify", "com.spotify.music"),
    YT_MUSIC("YouTube Music", "com.google.android.apps.youtube.music"),
}

class AssistantPreferences(context: Context) : AssistantDataStore {
    private val prefs = context.getSharedPreferences("open_assistant_prefs", Context.MODE_PRIVATE)

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, value).apply()

    var wakeWords: List<String>
        get() {
            val raw = prefs.getString(KEY_WAKE_WORDS, null)
            if (raw.isNullOrBlank()) return DEFAULT_WAKE_WORDS
            return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { DEFAULT_WAKE_WORDS }
        }
        set(value) = prefs.edit().putString(KEY_WAKE_WORDS, value.joinToString("\n")).apply()

    var wakeWordThreshold: Float
        get() = prefs.getFloat(KEY_WAKE_THRESHOLD, 0.25f)
        set(value) = prefs.edit().putFloat(KEY_WAKE_THRESHOLD, value.coerceIn(0.05f, 0.95f)).apply()

    var wakeWordBoostScore: Float
        get() = prefs.getFloat(KEY_WAKE_BOOST, 1.5f)
        set(value) = prefs.edit().putFloat(KEY_WAKE_BOOST, value.coerceIn(0.5f, 4.0f)).apply()

    var chimeEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHIME_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CHIME_ENABLED, value).apply()

    var speakResponses: Boolean
        get() = prefs.getBoolean(KEY_SPEAK_RESPONSES, true)
        set(value) = prefs.edit().putBoolean(KEY_SPEAK_RESPONSES, value).apply()

    var preferPiperTts: Boolean
        get() = prefs.getBoolean(KEY_PREFER_PIPER_TTS, true)
        set(value) = prefs.edit().putBoolean(KEY_PREFER_PIPER_TTS, value).apply()

    override var useFahrenheit: Boolean
        get() = prefs.getBoolean(KEY_USE_FAHRENHEIT, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_FAHRENHEIT, value).apply()

    override var defaultCity: String
        get() = prefs.getString(KEY_DEFAULT_CITY, "New York") ?: "New York"
        set(value) = prefs.edit().putString(KEY_DEFAULT_CITY, value.trim()).apply()

    override var searchEngine: SearchEngine
        get() {
            val name = prefs.getString(KEY_SEARCH_ENGINE, SearchEngine.DUCKDUCKGO.name)
            return SearchEngine.entries.firstOrNull { it.name == name } ?: SearchEngine.DUCKDUCKGO
        }
        set(value) = prefs.edit().putString(KEY_SEARCH_ENGINE, value.name).apply()

    override var musicProvider: MusicProvider
        get() {
            val name = prefs.getString(KEY_MUSIC_PROVIDER, MusicProvider.AUTO.name)
            return MusicProvider.entries.firstOrNull { it.name == name } ?: MusicProvider.AUTO
        }
        set(value) = prefs.edit().putString(KEY_MUSIC_PROVIDER, value.name).apply()

    override var homeAssistantUrl: String
        get() = prefs.getString(KEY_HA_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HA_URL, value.trim().trimEnd('/')).apply()

    override var homeAssistantToken: String
        get() = prefs.getString(KEY_HA_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HA_TOKEN, value.trim()).apply()

    override fun getShoppingList(): List<String> {
        val raw = prefs.getString(KEY_SHOPPING_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    override fun setShoppingList(items: List<String>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString(KEY_SHOPPING_LIST, arr.toString()).apply()
    }

    override fun addShoppingItem(item: String): List<String> {
        val updated = getShoppingList() + item.trim()
        setShoppingList(updated)
        return updated
    }

    override fun removeShoppingItem(item: String): List<String> {
        val updated = getShoppingList().filterNot { it.equals(item.trim(), ignoreCase = true) }
        setShoppingList(updated)
        return updated
    }

    override fun getReminders(): List<ReminderItem> {
        val raw = prefs.getString(KEY_REMINDERS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ReminderItem(
                    id = obj.getString("id"),
                    text = obj.getString("text"),
                    triggerAtMillis = obj.getLong("triggerAtMillis"),
                    done = obj.optBoolean("done", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveReminders(items: List<ReminderItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("triggerAtMillis", item.triggerAtMillis)
                    put("done", item.done)
                }
            )
        }
        prefs.edit().putString(KEY_REMINDERS, arr.toString()).apply()
    }

    override fun getRoutines(): List<Routine> {
        val raw = prefs.getString(KEY_ROUTINES, null)
        if (raw.isNullOrBlank()) return DEFAULT_ROUTINES
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val triggers = obj.getJSONArray("triggers")
                val actions = obj.getJSONArray("actions")
                Routine(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    triggerPhrases = (0 until triggers.length()).map { triggers.getString(it) },
                    actions = (0 until actions.length()).map { actions.getString(it) },
                )
            }
        }.getOrDefault(DEFAULT_ROUTINES)
    }

    fun saveRoutines(routines: List<Routine>) {
        val arr = JSONArray()
        routines.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("name", r.name)
                    put("triggers", JSONArray(r.triggerPhrases))
                    put("actions", JSONArray(r.actions))
                }
            )
        }
        prefs.edit().putString(KEY_ROUTINES, arr.toString()).apply()
    }

    companion object {
        val DEFAULT_WAKE_WORDS = listOf("HEY GOOGLE", "OK GOOGLE", "HEY ASSISTANT")

        val DEFAULT_ROUTINES = listOf(
            Routine(
                id = "good_morning",
                name = "Good morning",
                triggerPhrases = listOf("good morning", "start my day"),
                actions = listOf("what time is it", "what is the weather", "show my reminders"),
            ),
            Routine(
                id = "good_night",
                name = "Good night",
                triggerPhrases = listOf("good night", "bedtime"),
                actions = listOf("turn off flashlight", "mute volume", "what time is it"),
            ),
            Routine(
                id = "leaving_home",
                name = "Leaving home",
                triggerPhrases = listOf("i'm leaving", "leaving home"),
                actions = listOf("what is the weather", "what is on my shopping list"),
            ),
        )

        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_WAKE_WORDS = "wake_words"
        private const val KEY_WAKE_THRESHOLD = "wake_threshold"
        private const val KEY_WAKE_BOOST = "wake_boost"
        private const val KEY_CHIME_ENABLED = "chime_enabled"
        private const val KEY_SPEAK_RESPONSES = "speak_responses"
        private const val KEY_PREFER_PIPER_TTS = "prefer_piper_tts"
        private const val KEY_USE_FAHRENHEIT = "use_fahrenheit"
        private const val KEY_DEFAULT_CITY = "default_city"
        private const val KEY_SEARCH_ENGINE = "search_engine"
        private const val KEY_MUSIC_PROVIDER = "music_provider"
        private const val KEY_HA_URL = "ha_url"
        private const val KEY_HA_TOKEN = "ha_token"
        private const val KEY_SHOPPING_LIST = "shopping_list"
        private const val KEY_REMINDERS = "reminders"
        private const val KEY_ROUTINES = "routines"
    }
}
