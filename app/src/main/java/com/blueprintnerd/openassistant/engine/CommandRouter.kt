package com.blueprintnerd.openassistant.engine

import android.media.AudioManager
import android.view.KeyEvent
import com.blueprintnerd.openassistant.data.AssistantDataStore
import com.blueprintnerd.openassistant.data.MusicProvider
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

class CommandRouter(
    private val device: DeviceActions,
    private val store: AssistantDataStore,
    private val web: WebKnowledgeClient = WebKnowledgeClient(),
) {
    suspend fun handle(rawInput: String, allowRoutines: Boolean = true): AssistantResponse {
        val input = rawInput.trim()
        if (input.isEmpty()) {
            return AssistantResponse(
                title = "Listening",
                body = "How can I help?",
                spokenText = "How can I help?",
                suggestions = DEFAULT_SUGGESTIONS,
            )
        }
        val lower = input.lowercase(Locale.US).trimEnd('.', '!', '?').trim()

        // 1. User Routines
        if (allowRoutines) {
            val matchedRoutine = store.getRoutines().firstOrNull { routine ->
                routine.triggerPhrases.any { phrase ->
                    lower == phrase.lowercase(Locale.US).trim()
                }
            }
            if (matchedRoutine != null) {
                val results = matchedRoutine.actions.map { step ->
                    handle(step, allowRoutines = false)
                }
                val combinedBody = results.joinToString("\n") { "${it.title}: ${it.body}" }
                val combinedSpeech = results.joinToString(". ") { it.spokenText }
                return AssistantResponse(
                    title = "Routine: ${matchedRoutine.name}",
                    body = combinedBody,
                    spokenText = combinedSpeech,
                    cardType = CardType.LIST,
                    listItems = results.map { "${it.title}: ${it.body}" },
                )
            }
        }

        // 2. Greetings & Help
        if (lower in setOf("hi", "hello", "hey", "hey there", "hi there", "yo", "sup")) {
            return AssistantResponse(
                title = "OpenAssistant",
                body = "Hi!",
                spokenText = "Hi!",
                suggestions = DEFAULT_SUGGESTIONS,
            )
        }
        if (lower in setOf("what can you do", "help")) {
            return AssistantResponse(
                title = "OpenAssistant",
                body = "Try asking me to set a timer, check the weather, play music on Spotify or YouTube Music, or control your phone.",
                spokenText = "Try asking me to set a timer, check the weather, play music, or control your phone.",
                suggestions = DEFAULT_SUGGESTIONS,
            )
        }

        // 3. Flashlight
        if (lower.matches(Regex("""^(turn on|enable|switch on)\s+(the\s+)?(flashlight|torch)$"""))) {
            val msg = device.setFlashlight(true)
            return AssistantResponse("Flashlight", msg, cardType = CardType.ACTION_CONFIRMATION)
        }
        if (lower.matches(Regex("""^(turn off|disable|switch off)\s+(the\s+)?(flashlight|torch)$"""))) {
            val msg = device.setFlashlight(false)
            return AssistantResponse("Flashlight", msg, cardType = CardType.ACTION_CONFIRMATION)
        }

        // 4. Volume & Mute
        Regex("""^(?:set\s+)?volume\s+(?:to\s+)?(\d{1,3})\s*%?$""").matchEntire(lower)?.let { m ->
            val pct = m.groupValues[1].toInt()
            val msg = device.setVolumePercent(pct)
            return AssistantResponse("Volume", msg, cardType = CardType.ACTION_CONFIRMATION)
        }
        if (lower.matches(Regex("""^(turn\s+up|increase|raise)\s+(the\s+)?volume$""")) || lower == "volume up" || lower == "louder") {
            val msg = device.adjustVolume(AudioManager.ADJUST_RAISE)
            return AssistantResponse("Volume", msg, cardType = CardType.ACTION_CONFIRMATION)
        }
        if (lower.matches(Regex("""^(turn\s+down|decrease|lower)\s+(the\s+)?volume$""")) || lower == "volume down" || lower == "quieter") {
            val msg = device.adjustVolume(AudioManager.ADJUST_LOWER)
            return AssistantResponse("Volume", msg, cardType = CardType.ACTION_CONFIRMATION)
        }
        if (lower in setOf("mute", "mute volume", "mute audio", "silence")) {
            val msg = device.setMute(true)
            return AssistantResponse("Volume", msg, cardType = CardType.ACTION_CONFIRMATION)
        }
        if (lower in setOf("unmute", "unmute volume", "unmute audio")) {
            val msg = device.setMute(false)
            return AssistantResponse("Volume", msg, cardType = CardType.ACTION_CONFIRMATION)
        }

        // 5. Battery
        if (lower.contains("battery") && (lower.contains("level") || lower.contains("percent") || lower.contains("how much") || lower == "battery")) {
            val msg = device.getBatteryStatus()
            return AssistantResponse("Battery", msg)
        }

        // 6. Media playback controls
        when {
            lower in setOf("pause", "pause music", "pause media") ->
                return AssistantResponse("Media", device.sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, "Paused playback."), cardType = CardType.ACTION_CONFIRMATION)
            lower in setOf("resume", "resume music", "play music", "continue playing") ->
                return AssistantResponse("Media", device.sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, "Resumed playback."), cardType = CardType.ACTION_CONFIRMATION)
            lower in setOf("next", "next song", "next track", "skip", "skip song", "skip track") ->
                return AssistantResponse("Media", device.sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Skipped to next track."), cardType = CardType.ACTION_CONFIRMATION)
            lower in setOf("previous", "previous song", "previous track", "go back") ->
                return AssistantResponse("Media", device.sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Playing previous track."), cardType = CardType.ACTION_CONFIRMATION)
            lower in setOf("stop music", "stop media", "stop playing") ->
                return AssistantResponse("Media", device.sendMediaKey(KeyEvent.KEYCODE_MEDIA_STOP, "Stopped playback."), cardType = CardType.ACTION_CONFIRMATION)
        }
        Regex("""^play\s+(.+?)\s+on\s+(spotify|youtube music|yt music)$""").matchEntire(lower)?.let { m ->
            val query = m.groupValues[1].trim()
            val service = m.groupValues[2].trim()
            val provider = if (service == "spotify") MusicProvider.SPOTIFY else MusicProvider.YT_MUSIC
            val msg = device.playMediaSearch(query, provider)
            return AssistantResponse("Media", msg, cardType = CardType.ACTION_CONFIRMATION, dismissAfterMs = 2000)
        }
        Regex("""^play\s+(.+)$""").matchEntire(lower)?.let { m ->
            val query = m.groupValues[1].trim()
            if (query.isNotEmpty() && query !in setOf("a game", "coin")) {
                val msg = device.playMediaSearch(query)
                return AssistantResponse("Media", msg, cardType = CardType.ACTION_CONFIRMATION, dismissAfterMs = 2000)
            }
        }

        // 7. Timers
        if (lower in setOf("show timers", "show my timers", "open timers")) {
            return AssistantResponse("Timers", device.showTimers(), cardType = CardType.ACTION_CONFIRMATION)
        }
        parseTimerCommand(lower)?.let { (seconds, label) ->
            val msg = device.setTimer(seconds, label)
            return AssistantResponse("Timer", msg, cardType = CardType.ACTION_CONFIRMATION)
        }

        // 8. Alarms
        if (lower in setOf("show alarms", "show my alarms", "open alarms")) {
            return AssistantResponse("Alarms", device.showAlarms(), cardType = CardType.ACTION_CONFIRMATION)
        }
        parseAlarmCommand(lower)?.let { (hour24, minute, label) ->
            val msg = device.setAlarm(hour24, minute, label)
            return AssistantResponse("Alarm", msg, cardType = CardType.ACTION_CONFIRMATION)
        }

        // 9. Reminders
        if (lower in setOf("show reminders", "show my reminders", "what are my reminders", "my reminders")) {
            val active = store.getReminders().filterNot { it.done }
            return if (active.isEmpty()) {
                AssistantResponse("Reminders", "You have no active reminders.", cardType = CardType.LIST)
            } else {
                val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.US)
                val items = active.map { "${it.text} (${fmt.format(Date(it.triggerAtMillis))})" }
                AssistantResponse(
                    title = "Reminders (${active.size})",
                    body = items.joinToString("\n"),
                    spokenText = "You have ${active.size} reminders: ${active.joinToString(", ") { it.text }}.",
                    cardType = CardType.LIST,
                    listItems = items,
                )
            }
        }
        parseReminderCommand(input)?.let { (text, delayMs) ->
            val msg = device.scheduleReminder(text, delayMs)
            return AssistantResponse("Reminder", msg, cardType = CardType.ACTION_CONFIRMATION)
        }

        // 10. Shopping List
        Regex("""^add\s+(.+?)\s+to\s+(?:my\s+)?(?:shopping|grocery)\s+list$""", RegexOption.IGNORE_CASE)
            .matchEntire(input)?.let { m ->
                val item = m.groupValues[1].trim()
                val updated = store.addShoppingItem(item)
                return AssistantResponse(
                    title = "Shopping List",
                    body = "Added $item to your shopping list.",
                    cardType = CardType.LIST,
                    listItems = updated,
                )
            }
        Regex("""^(?:remove|delete)\s+(.+?)\s+from\s+(?:my\s+)?(?:shopping|grocery)\s+list$""", RegexOption.IGNORE_CASE)
            .matchEntire(input)?.let { m ->
                val item = m.groupValues[1].trim()
                val updated = store.removeShoppingItem(item)
                return AssistantResponse(
                    title = "Shopping List",
                    body = "Removed $item from your shopping list.",
                    cardType = CardType.LIST,
                    listItems = updated,
                )
            }
        if (lower.matches(Regex("""^(clear|empty)\s+(my\s+)?(shopping|grocery)\s+list$"""))) {
            store.setShoppingList(emptyList())
            return AssistantResponse("Shopping List", "Your shopping list has been cleared.", cardType = CardType.ACTION_CONFIRMATION)
        }
        if (lower.matches(Regex("""^(what(?:'s| is)\s+on\s+|show\s+|read\s+)(my\s+)?(shopping|grocery)\s+list$"""))) {
            val items = store.getShoppingList()
            return if (items.isEmpty()) {
                AssistantResponse("Shopping List", "Your shopping list is empty.", cardType = CardType.LIST)
            } else {
                AssistantResponse(
                    title = "Shopping List (${items.size})",
                    body = items.joinToString("\n"),
                    spokenText = "On your shopping list: ${items.joinToString(", ")}.",
                    cardType = CardType.LIST,
                    listItems = items,
                )
            }
        }

        // 11. Calendar
        Regex("""^(?:add|create|schedule)\s+(?:a\s+)?(?:calendar\s+)?(?:event|meeting)\s+(?:called\s+|titled\s+|for\s+)?(.+)$""", RegexOption.IGNORE_CASE)
            .matchEntire(input)?.let { m ->
                val title = m.groupValues[1].trim()
                val msg = device.createCalendarEvent(title)
                return AssistantResponse("Calendar", msg, cardType = CardType.ACTION_CONFIRMATION)
            }

        // 12. Phone Calls & SMS
        Regex("""^(?:call|dial|phone)\s+(.+)$""", RegexOption.IGNORE_CASE).matchEntire(input)?.let { m ->
            val target = m.groupValues[1].trim()
            val msg = device.callTarget(target)
            return AssistantResponse("Phone", msg, cardType = CardType.ACTION_CONFIRMATION)
        }
        Regex("""^(?:text|sms|message|send a text to|send a message to)\s+(.+?)(?:\s+(?:saying|that|:)\s+(.+))?$""", RegexOption.IGNORE_CASE)
            .matchEntire(input)?.let { m ->
                val target = m.groupValues[1].trim()
                val body = m.groupValues[2].trim()
                val msg = device.sendSms(target, body)
                return AssistantResponse("Messages", msg, cardType = CardType.ACTION_CONFIRMATION)
            }

        // 13. Navigation & Maps
        Regex(
            """^(?:navigate to|directions to|take me to|drive to|walk to|bike to|cycle to|route to|how do i get to|how to get to|show me|find)\s+(.+?)(?:\s+on the map)?$""",
            RegexOption.IGNORE_CASE,
        ).matchEntire(input)?.let { m ->
            val dest = m.groupValues[1].trim()
            if (!lower.startsWith("find out") && dest.isNotEmpty()) {
                val msg = device.navigateTo(dest)
                return AssistantResponse("Navigation", msg, cardType = CardType.ACTION_CONFIRMATION, dismissAfterMs = 1500)
            }
        }
        Regex("""^(.+?)\s+near me$""", RegexOption.IGNORE_CASE).matchEntire(input)?.let { m ->
            val place = m.groupValues[1].trim()
            if (place.isNotEmpty()) {
                val msg = device.navigateTo(place)
                return AssistantResponse("Navigation", msg, cardType = CardType.ACTION_CONFIRMATION, dismissAfterMs = 1500)
            }
        }

        // 14. Camera / Selfie
        if (lower in setOf("take a selfie", "open front camera", "selfie")) {
            return AssistantResponse("Camera", device.openCamera(selfie = true), cardType = CardType.ACTION_CONFIRMATION)
        }
        if (lower in setOf("take a photo", "take a picture", "open camera")) {
            return AssistantResponse("Camera", device.openCamera(selfie = false), cardType = CardType.ACTION_CONFIRMATION)
        }

        // 15. Settings Panels
        Regex("""^(?:open\s+)?(wi-?fi|bluetooth|airplane mode|display|brightness|sound|location|battery|assistant)\s*(?:settings)?$""")
            .matchEntire(lower)?.let { m ->
                val kind = m.groupValues[1].replace("-", "").replace(" mode", "")
                val msg = device.openSettingsPanel(kind)
                return AssistantResponse("Settings", msg, cardType = CardType.ACTION_CONFIRMATION)
            }
        if (lower == "open settings" || lower == "settings") {
            return AssistantResponse("Settings", device.openSettingsPanel("settings"), cardType = CardType.ACTION_CONFIRMATION)
        }

        // 16. Smart Home (Home Assistant) — before generic app launch
        Regex("""^(turn on|turn off|switch on|switch off)\s+(?:the\s+)?(.+)$""").matchEntire(lower)?.let { m ->
            val turnOn = m.groupValues[1].endsWith("on")
            val target = m.groupValues[2].trim()
            if (target in setOf("wifi", "wi-fi", "bluetooth", "airplane mode", "location")) {
                val kind = target.replace("-", "").replace(" mode", "")
                return AssistantResponse("Settings", device.openSettingsPanel(kind), cardType = CardType.ACTION_CONFIRMATION)
            }
            if (store.homeAssistantUrl.isNotBlank() && store.homeAssistantToken.isNotBlank()) {
                val res = web.callHomeAssistantService(
                    baseUrl = store.homeAssistantUrl,
                    token = store.homeAssistantToken,
                    targetName = target,
                    turnOn = turnOn,
                )
                return res.fold(
                    onSuccess = { AssistantResponse("Smart Home", it, cardType = CardType.ACTION_CONFIRMATION) },
                    onFailure = { AssistantResponse("Smart Home", it.message ?: "Home Assistant error.", cardType = CardType.ERROR) },
                )
            } else {
                return AssistantResponse(
                    title = "Smart Home",
                    body = "Configure your local Home Assistant URL and access token in Settings to control '$target' without Google servers.",
                    cardType = CardType.TEXT,
                )
            }
        }

        // 17. Launch App
        Regex("""^(?:open|launch|start)\s+(.+)$""", RegexOption.IGNORE_CASE).matchEntire(input)?.let { m ->
            val rawApp = m.groupValues[1].trim()
            val appName = if (rawApp.equals("yt music", ignoreCase = true)) "YouTube Music" else rawApp
            val launched = device.launchApp(appName)
            if (launched != null) {
                return AssistantResponse("Apps", launched, cardType = CardType.ACTION_CONFIRMATION, dismissAfterMs = 1500)
            }
            return AssistantResponse("Apps", "Could not find an installed app named '$appName'.", cardType = CardType.ERROR)
        }

        // 18. Time, Date, World Clock
        if (lower.matches(Regex("""^(what(?:'s| is)\s+the\s+time|what time is it|current time|time)$"""))) {
            val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date())
            return AssistantResponse("Time", "It is $timeStr.", spokenText = "It's $timeStr.")
        }
        Regex("""^what(?:'s| is)\s+(?:the\s+)?time\s+in\s+(.+)$""").matchEntire(lower)?.let { m ->
            val city = m.groupValues[1].trim()
            val tz = resolveCityTimeZone(city)
            if (tz != null) {
                val fmt = SimpleDateFormat("h:mm a, EEE", Locale.US).apply { timeZone = tz }
                val timeStr = fmt.format(Date())
                val cityTitle = city.replaceFirstChar { it.uppercase() }
                return AssistantResponse("World Clock", "In $cityTitle, it is $timeStr.")
            }
        }
        if (lower.matches(Regex("""^(what(?:'s| is)\s+(today'?s?\s+)?date|what day is it|today'?s?\s+date|date)$"""))) {
            val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date())
            return AssistantResponse("Date", "Today is $dateStr.")
        }

        // 19. Unit & Currency Conversions
        UnitConverter.tryConvert(input)?.let { converted ->
            return AssistantResponse(
                title = "Unit Converter",
                body = converted,
                cardType = CardType.CALCULATOR,
            )
        }
        UnitConverter.tryParseCurrencyQuery(input)?.let { (amount, from, to) ->
            val converted = web.fetchCurrencyConversion(amount, from, to)
            if (converted != null) {
                return AssistantResponse(
                    title = "Currency Converter",
                    body = converted,
                    cardType = CardType.CALCULATOR,
                )
            }
        }

        // 20. Math & Calculator
        MathEvaluator.tryEvaluateNaturalQuery(input)?.let { (expr, result) ->
            return AssistantResponse(
                title = "Calculator",
                body = "$expr = $result",
                spokenText = "The answer is $result.",
                cardType = CardType.CALCULATOR,
            )
        }

        // 21. Classic Fun & Utilities (Coin, Dice, Random, Jokes)
        if (lower in setOf("flip a coin", "toss a coin", "coin flip", "heads or tails")) {
            val side = if (Random.nextBoolean()) "Heads" else "Tails"
            return AssistantResponse("Coin Flip", "It landed on $side.", spokenText = side)
        }
        Regex("""^roll\s+(?:a\s+)?(?:d(\d+)|(\d+)[-\s]sided\s+di(?:e|ce)|di(?:e|ce))$""").matchEntire(lower)?.let { m ->
            val sides = (m.groupValues[1].toIntOrNull() ?: m.groupValues[2].toIntOrNull() ?: 6).coerceIn(2, 1000)
            val roll = Random.nextInt(1, sides + 1)
            return AssistantResponse("Dice Roll (d$sides)", "You rolled a $roll.", spokenText = "You rolled a $roll.")
        }
        Regex("""^(?:pick a )?random number(?:\s+between\s+(\d+)\s+and\s+(\d+))?$""").matchEntire(lower)?.let { m ->
            val min = m.groupValues[1].toIntOrNull() ?: 1
            val max = m.groupValues[2].toIntOrNull() ?: 100
            val lo = minOf(min, max)
            val hi = maxOf(min, max)
            val picked = Random.nextInt(lo, hi + 1)
            return AssistantResponse("Random Number", "Your random number is $picked.", spokenText = picked.toString())
        }
        if (lower.contains("tell me a joke") || lower == "joke" || lower == "tell a joke") {
            val joke = JOKES.random()
            return AssistantResponse("Joke", joke)
        }

        // 22. Weather (Open-Meteo, zero Google)
        parseWeatherCity(lower)?.let { requestedCity ->
            val city = requestedCity.ifBlank { store.defaultCity }
            val weather = web.fetchWeather(city, store.useFahrenheit)
            return if (weather != null) {
                AssistantResponse(
                    title = "Weather",
                    body = "${weather.location}: ${weather.temperature}, ${weather.condition}. ${weather.details}",
                    spokenText = "In ${weather.location}, it is currently ${weather.temperature} and ${weather.condition}.",
                    cardType = CardType.WEATHER,
                    weather = weather,
                )
            } else {
                AssistantResponse("Weather", "Could not fetch weather for '$city'. Check your internet connection.", cardType = CardType.ERROR)
            }
        }

        // 23. Dictionary Definitions & Synonyms
        Regex("""^(?:define|(?:what(?:'s| is)\s+the\s+)?(?:definition|meaning)\s+of|what does\s+)(.+?)(?:\s+mean)?$""")
            .matchEntire(lower)?.let { m ->
                val word = m.groupValues[1].trim()
                if (word.isNotEmpty() && !word.contains(" ")) {
                    val def = web.fetchDefinition(word)
                    if (def != null) {
                        val header = buildString {
                            append(def.word)
                            if (def.phonetic.isNotBlank()) append(" (${def.phonetic})")
                            if (def.partOfSpeech.isNotBlank()) append(" - ${def.partOfSpeech}")
                        }
                        val fullBody = buildString {
                            append(def.definition)
                            if (def.example.isNotBlank()) append("\n\n\"${def.example}\"")
                            if (def.synonyms.isNotEmpty()) append("\nSynonyms: ${def.synonyms.joinToString(", ")}")
                        }
                        return AssistantResponse(
                            title = header,
                            body = fullBody,
                            spokenText = "${def.word}: ${def.definition}",
                            cardType = CardType.WIKIPEDIA,
                        )
                    }
                }
            }
        Regex("""^(?:synonyms?|another word)\s+(?:for|of)\s+(.+)$""").matchEntire(lower)?.let { m ->
            val word = m.groupValues[1].trim()
            if (word.isNotEmpty() && !word.contains(" ")) {
                val def = web.fetchDefinition(word)
                if (def != null && def.synonyms.isNotEmpty()) {
                    val list = def.synonyms.joinToString(", ")
                    return AssistantResponse(
                        title = "Synonyms for ${def.word}",
                        body = list,
                        spokenText = "Synonyms for ${def.word} include $list.",
                        cardType = CardType.WIKIPEDIA,
                    )
                }
            }
        }

        // 24. Explicit Web Search
        Regex("""^(?:search(?: the web)? for|look up|google)\s+(.+)$""", RegexOption.IGNORE_CASE)
            .matchEntire(input)?.let { m ->
                val q = m.groupValues[1].trim()
                val msg = device.openWebSearch(q)
                return AssistantResponse("Web Search", msg, cardType = CardType.ACTION_CONFIRMATION)
            }

        // 25. Factual Knowledge Lookup (Wikipedia + DuckDuckGo Instant Answers — zero LLM, zero Google)
        val topic = extractKnowledgeTopic(input)
        val wiki = web.fetchWikipediaSummary(topic)
        if (wiki != null) {
            val concise = truncateSentences(wiki.extract, 2)
            return AssistantResponse(
                title = wiki.title,
                body = concise,
                spokenText = concise,
                cardType = CardType.WIKIPEDIA,
                actions = listOf(
                    ResponseAction(label = "Read on Wikipedia", url = wiki.url),
                    ResponseAction(label = "Search ${store.searchEngine.label}", url = searchUrlFor(input)),
                ),
            )
        }

        val ddg = web.fetchDuckDuckGoInstantAnswer(input)
        if (ddg != null) {
            val concise = truncateSentences(ddg.text, 2)
            return AssistantResponse(
                title = ddg.heading,
                body = concise,
                spokenText = concise,
                cardType = CardType.WIKIPEDIA,
                actions = buildList {
                    ddg.url?.let { add(ResponseAction("Open Source", url = it)) }
                    add(ResponseAction("Search ${store.searchEngine.label}", url = searchUrlFor(input)))
                },
            )
        }

        // 26. Fallback: Offer privacy-respecting web search
        return AssistantResponse(
            title = "Web Search",
            body = "I didn't find an instant answer for \"$input\". Tap below to search with ${store.searchEngine.label}.",
            spokenText = "I couldn't find an instant answer, so here is a web search.",
            actions = listOf(
                ResponseAction(label = "Search ${store.searchEngine.label}", url = searchUrlFor(input)),
            ),
            suggestions = DEFAULT_SUGGESTIONS,
        )
    }

    private fun searchUrlFor(query: String): String {
        val enc = URLEncoder.encode(query.trim(), "UTF-8")
        return String.format(store.searchEngine.searchUrlTemplate, enc)
    }

    internal fun parseTimerCommand(lower: String): Pair<Int, String?>? {
        val m = Regex(
            """^(?:set\s+(?:a\s+)?)?timer\s+(?:for\s+)?(?:(\d+)\s*(?:hours?|hrs?|h)\s*)?(?:(?:and\s+)?(\d+)\s*(?:minutes?|mins?|m)\s*)?(?:(?:and\s+)?(\d+)\s*(?:seconds?|secs?|s)\s*)?(?:(?:called|named|for|labeled)\s+(.+))?$"""
        ).matchEntire(lower) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: 0
        val min = m.groupValues[2].toIntOrNull() ?: 0
        val sec = m.groupValues[3].toIntOrNull() ?: 0
        val total = h * 3600 + min * 60 + sec
        if (total <= 0) return null
        val label = m.groupValues[4].trim().takeIf { it.isNotEmpty() }
        return total to label
    }

    internal fun parseAlarmCommand(lower: String): Triple<Int, Int, String?>? {
        val m = Regex(
            """^(?:set\s+(?:an\s+)?)?(?:alarm|wake me up)\s+(?:for|at)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)?(?:\s+(?:called|named|for|labeled)\s+(.+))?$"""
        ).matchEntire(lower) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        val amPm = m.groupValues[3].replace(".", "").lowercase(Locale.US)
        val label = m.groupValues[4].trim().takeIf { it.isNotEmpty() }

        if (minute !in 0..59 || hour !in 0..23) return null
        if (amPm == "pm" && hour in 1..11) hour += 12
        if (amPm == "am" && hour == 12) hour = 0
        return Triple(hour, minute, label)
    }

    internal fun parseReminderCommand(input: String): Pair<String, Long>? {
        val m = Regex(
            """^remind me\s+(?:to\s+)?(.+?)(?:\s+in\s+(\d+)\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?))?$""",
            RegexOption.IGNORE_CASE,
        ).matchEntire(input.trim()) ?: return null
        val text = m.groupValues[1].trim()
        val amount = m.groupValues[2].toLongOrNull() ?: 15L
        val unit = m.groupValues[3].lowercase(Locale.US)
        val multiplier = when {
            unit.startsWith("h") -> 3_600_000L
            unit.startsWith("s") -> 1_000L
            else -> 60_000L
        }
        return text to (amount * multiplier)
    }

    private fun parseWeatherCity(lower: String): String? {
        if (lower in setOf("weather", "what is the weather", "what's the weather", "forecast", "how is the weather", "how's the weather")) {
            return ""
        }
        Regex("""^(?:what(?:'s| is)\s+the\s+|how(?:'s| is)\s+the\s+)?(?:weather|forecast|temperature)(?:\s+like)?\s+(?:in|for)\s+(.+)$""")
            .matchEntire(lower)?.let { return it.groupValues[1].trim() }
        Regex("""^(.+?)\s+(?:weather|forecast)$""").matchEntire(lower)?.let {
            val candidate = it.groupValues[1].trim()
            if (candidate.split(" ").size <= 3 && candidate !in setOf("the", "today's", "todays", "tomorrow's")) {
                return candidate
            }
        }
        return null
    }

    private fun extractKnowledgeTopic(input: String): String {
        return input
            .replace(Regex("""^(?:who is|who was|what is|what are|what was|where is|tell me about|explain)\s+(?:a\s+|an\s+|the\s+)?""", RegexOption.IGNORE_CASE), "")
            .trimEnd('?', '.', '!')
            .trim()
    }

    private fun truncateSentences(text: String, maxSentences: Int): String {
        val parts = text.split(Regex("""(?<=[.!?])\s+"""))
        return parts.take(maxSentences).joinToString(" ").trim()
    }

    private fun resolveCityTimeZone(city: String): TimeZone? {
        val map = mapOf(
            "new york" to "America/New_York",
            "los angeles" to "America/Los_Angeles",
            "chicago" to "America/Chicago",
            "denver" to "America/Denver",
            "london" to "Europe/London",
            "paris" to "Europe/Paris",
            "berlin" to "Europe/Berlin",
            "tokyo" to "Asia/Tokyo",
            "seoul" to "Asia/Seoul",
            "sydney" to "Australia/Sydney",
            "dubai" to "Asia/Dubai",
            "mumbai" to "Asia/Kolkata",
            "delhi" to "Asia/Kolkata",
            "singapore" to "Asia/Singapore",
            "toronto" to "America/Toronto",
            "vancouver" to "America/Vancouver",
            "utc" to "UTC",
            "gmt" to "GMT",
        )
        val id = map[city.lowercase(Locale.US)] ?: return null
        return TimeZone.getTimeZone(id)
    }

    companion object {
        val DEFAULT_SUGGESTIONS = listOf(
            "What's the weather?",
            "Set a timer for 5 minutes",
            "What time is it?",
            "15% of 85",
            "Flip a coin",
            "Good morning",
        )

        private val JOKES = listOf(
            "Why do programmers prefer dark mode? Because light attracts bugs.",
            "I told my computer I needed a break, and now it won't stop sending me Kit-Kat ads.",
            "Why did the scarecrow win an award? Because he was outstanding in his field.",
            "What do you call fake spaghetti? An impasta.",
            "Why don't scientists trust atoms? Because they make up everything.",
            "How does a penguin build its house? Igloos it together.",
        )
    }
}
