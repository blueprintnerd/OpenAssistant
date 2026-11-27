package com.blueprintnerd.openassistant

import com.blueprintnerd.openassistant.data.AssistantDataStore
import com.blueprintnerd.openassistant.data.MusicProvider
import com.blueprintnerd.openassistant.data.ReminderItem
import com.blueprintnerd.openassistant.data.Routine
import com.blueprintnerd.openassistant.data.SearchEngine
import com.blueprintnerd.openassistant.engine.CardType
import com.blueprintnerd.openassistant.engine.CommandRouter
import com.blueprintnerd.openassistant.engine.DeviceActions
import com.blueprintnerd.openassistant.engine.MathEvaluator
import com.blueprintnerd.openassistant.engine.SpeechNormalizer
import com.blueprintnerd.openassistant.engine.UnitConverter
import com.blueprintnerd.openassistant.engine.WebKnowledgeClient
import com.blueprintnerd.openassistant.speech.BpeKeywordTokenizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineUnitTests {

    private class FakeDeviceActions : DeviceActions {
        var lastCall: String = ""
        override fun setFlashlight(enabled: Boolean) = "Flashlight ${if (enabled) "on" else "off"}".also { lastCall = it }
        override fun adjustVolume(direction: Int) = "Volume adjusted $direction".also { lastCall = it }
        override fun setVolumePercent(percent: Int) = "Volume $percent%".also { lastCall = it }
        override fun setMute(mute: Boolean) = if (mute) "Muted" else "Unmuted"
        override fun getBatteryStatus() = "Battery is at 88 percent."
        override fun setTimer(seconds: Int, label: String?) = "Timer:$seconds:${label ?: ""}".also { lastCall = it }
        override fun showTimers() = "Timers"
        override fun setAlarm(hour24: Int, minute: Int, message: String?) = "Alarm:$hour24:$minute:${message ?: ""}".also { lastCall = it }
        override fun showAlarms() = "Alarms"
        override fun launchApp(appNameQuery: String) = if (appNameQuery.equals("Spotify", true)) "Opening Spotify." else null
        override fun openCamera(selfie: Boolean) = if (selfie) "Selfie" else "Camera"
        override fun callTarget(target: String) = "Calling $target"
        override fun sendSms(target: String, message: String) = "SMS:$target:$message"
        override fun navigateTo(destination: String) = "Nav:$destination"
        override fun sendMediaKey(keyCode: Int, actionLabel: String) = actionLabel
        override fun playMediaSearch(query: String, provider: MusicProvider?) = "Play:$query:${provider?.name ?: "DEFAULT"}"
        override fun openSettingsPanel(kind: String) = "Settings:$kind"
        override fun createCalendarEvent(title: String) = "Calendar:$title"
        override fun scheduleReminder(text: String, delayMillis: Long) = "Reminder:$text:$delayMillis"
        override fun scheduleReminderAt(text: String, triggerAtMillis: Long, timeLabel: String) = "ReminderAt:$text:$timeLabel"
        override fun getLastKnownCoordinates(): Pair<Double, Double>? = null
        override fun openWebSearch(query: String) = "Search:$query"
        override fun openUrl(url: String) = true
    }

    private class FakeStore : AssistantDataStore {
        override var useFahrenheit = true
        override var defaultCity = "New York"
        override var searchEngine = SearchEngine.DUCKDUCKGO
        override var musicProvider = MusicProvider.AUTO
        override var homeAddress = ""
        override var workAddress = ""
        override var homeAssistantUrl = ""
        override var homeAssistantToken = ""
        private val shopping = mutableListOf<String>()
        private val reminders = mutableListOf<ReminderItem>()
        private val routines = listOf(
            Routine("morning", "Morning", listOf("good morning"), listOf("turn on flashlight", "what time is it"))
        )

        override fun getShoppingList() = shopping.toList()
        override fun setShoppingList(items: List<String>) {
            shopping.clear()
            shopping.addAll(items)
        }
        override fun addShoppingItem(item: String): List<String> {
            shopping.add(item)
            return shopping.toList()
        }
        override fun removeShoppingItem(item: String): List<String> {
            shopping.removeAll { it.equals(item, true) }
            return shopping.toList()
        }
        override fun getReminders() = reminders.toList()
        override fun getRoutines() = routines
    }

    @Test
    fun mathEvaluatorHandlesArithmeticAndSpokenMath() {
        assertEquals("714", MathEvaluator.tryEvaluateNaturalQuery("what is 42 times 17")?.second)
        assertEquals("12.75", MathEvaluator.tryEvaluateNaturalQuery("15% of 85")?.second)
        assertEquals("12", MathEvaluator.tryEvaluateNaturalQuery("square root of 144")?.second)
        assertEquals("1,024", MathEvaluator.tryEvaluateNaturalQuery("2 to the power of 10")?.second)
        assertEquals("14", MathEvaluator.tryEvaluateNaturalQuery("2 + 3 * 4")?.second)
    }

    @Test
    fun unitConverterHandlesCommonConversions() {
        assertEquals("100 celsius = 212 fahrenheit", UnitConverter.tryConvert("100 celsius to fahrenheit"))
        assertEquals("100 fahrenheit = 37.777778 celsius", UnitConverter.tryConvert("100 °F to °C"))
        assertEquals("5 miles = 8.04672 kilometers", UnitConverter.tryConvert("convert 5 miles to km"))
        assertEquals("10 kilograms = 22.046226 pounds", UnitConverter.tryConvert("10 kg in lbs"))
        assertEquals("2 gigabytes = 2,048 megabytes", UnitConverter.tryConvert("2 gb to mb"))
        assertEquals("3 hours = 180 minutes", UnitConverter.tryConvert("how many minutes in 3 hours"))
        assertEquals("1,000 square feet = 92.903 square meters", UnitConverter.tryConvert("1000 sqft to sqm"))
        assertEquals(Triple(100.0, "USD", "EUR"), UnitConverter.tryParseCurrencyQuery("100 dollars to euros"))
    }

    @Test
    fun bpeTokenizerBuildsHeyGoogleKeywordsFile() {
        val vocab = setOf("\u2581HEY", "\u2581G", "OO", "GLE", "\u2581OK")
        val content = BpeKeywordTokenizer.buildKeywordsFileContent(
            phrases = listOf("HEY GOOGLE", "OK GOOGLE"),
            vocab = vocab,
            boostScore = 1.5f,
            threshold = 0.25f,
        )
        assertTrue(content.contains("\u2581HEY \u2581G OO GLE :1.50 #0.25 @HEY_GOOGLE"))
        assertTrue(content.contains("\u2581OK \u2581G OO GLE :1.50 #0.25 @OK_GOOGLE"))
    }

    @Test
    fun routerHandlesTimersAlarmsAndDeviceActions() = runTest {
        val device = FakeDeviceActions()
        val store = FakeStore()
        val router = CommandRouter(device, store)

        val timer = router.handle("set a timer for 5 minutes and 30 seconds called pasta")
        assertEquals("Timer:330:pasta", timer.body)

        val alarm = router.handle("set an alarm for 7:30 am")
        assertEquals("Alarm:7:30:", alarm.body)

        val flash = router.handle("turn on the flashlight")
        assertEquals("Flashlight on", flash.body)

        val vol = router.handle("set volume to 65%")
        assertEquals("Volume 65%", vol.body)

        val app = router.handle("open Spotify")
        assertEquals("Opening Spotify.", app.body)

        val sms = router.handle("text Alex saying On my way")
        assertEquals("SMS:Alex:On my way", sms.body)
    }

    @Test
    fun routerHandlesShoppingListAndRoutines() = runTest {
        val device = FakeDeviceActions()
        val store = FakeStore()
        val router = CommandRouter(device, store)

        router.handle("add milk to my shopping list")
        router.handle("add eggs to my shopping list")
        val listResp = router.handle("what is on my shopping list")
        assertEquals(CardType.LIST, listResp.cardType)
        assertEquals(listOf("milk", "eggs"), listResp.listItems)

        val routineResp = router.handle("good morning")
        assertEquals("Routine: Morning", routineResp.title)
        assertEquals(2, routineResp.listItems.size)
        assertNotNull(router.handle("what time is it in tokyo").body)

        val hiResp = router.handle("hi")
        assertEquals("Hi!", hiResp.body)
        assertEquals("Hi!", hiResp.spokenText)

        val spotifyResp = router.handle("play Daft Punk on Spotify")
        assertEquals("Play:daft punk:SPOTIFY", spotifyResp.body)

        val ytmResp = router.handle("play lo-fi beats on yt music")
        assertEquals("Play:lo-fi beats:YT_MUSIC", ytmResp.body)

        val navResp = router.handle("how do i get to Central Park")
        assertEquals("Nav:Central Park", navResp.body)

        val nearMeResp = router.handle("coffee shops near me")
        assertEquals("Nav:coffee shops", nearMeResp.body)

        router.handle("set my home address to 123 Main St")
        val homeNavResp = router.handle("navigate home")
        assertEquals("Nav:123 Main St", homeNavResp.body)
    }

    @Test
    fun speechNormalizerHandlesSpokenNumbersAndFillers() = runTest {
        assertEquals("set a timer for 5 minutes", SpeechNormalizer.normalize("Hey Google can you please set a timer for five minutes"))
        assertEquals("wake me up at 7:30 am", SpeechNormalizer.normalize("wake me up at seven thirty am"))
        assertEquals("set volume to 50 percent", SpeechNormalizer.normalize("please set volume to fifty percent"))
        assertEquals("set a timer for 30 minutes", SpeechNormalizer.normalize("set a timer for half an hour"))

        val device = FakeDeviceActions()
        val store = FakeStore()
        val router = CommandRouter(device, store)

        // End-to-end spoken STT command
        val spokenTimer = router.handle("Hey Google can you please set a timer for five minutes")
        assertEquals("Timer:300:", spokenTimer.body)

        val spokenAlarm = router.handle("wake me up at seven thirty am")
        assertEquals("Alarm:7:30:", spokenAlarm.body)

        // Multi-turn follow-up for timer & SMS
        val askTimer = router.handle("set a timer")
        assertTrue(askTimer.expectFollowUp)
        val doneTimer = router.handle("ten minutes")
        assertEquals("Timer:600:", doneTimer.body)

        val askSms = router.handle("text Mom")
        assertTrue(askSms.expectFollowUp)
        val doneSms = router.handle("I'll be home soon")
        assertEquals("SMS:Mom:I'll be home soon", doneSms.body)

        // Absolute time reminder
        val absRem = router.handle("remind me to pay rent tomorrow at 9 am")
        assertTrue(absRem.body.startsWith("ReminderAt:pay rent:Tomorrow at 9:00 AM"))
    }

    @Test
    fun duckDuckGoLiteParserFiltersAdsAndExtractsResults() {
        val sampleHtml = """
            <table>
              <tr><td><a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fduckduckgo.com%2Fy.js%3Fad_domain%3Dtiqets.com" class='result-link'>Sponsored Ad</a></td></tr>
              <tr><td class='result-snippet'>Buy tickets now!</td></tr>
              <tr><td><a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fen.wikipedia.org%2Fwiki%2FEiffel_Tower" class='result-link'>Eiffel Tower - Wikipedia</a></td></tr>
              <tr><td class='result-snippet'>The Eiffel Tower is 330 meters tall in Paris, France.</td></tr>
              <tr><td><span class='link-text'>en.wikipedia.org/wiki/Eiffel_Tower</span></td></tr>
            </table>
        """.trimIndent()
        val results = WebKnowledgeClient().parseDuckDuckGoLiteHtml(sampleHtml, maxResults = 3)
        assertEquals(1, results.size)
        assertEquals("Eiffel Tower - Wikipedia", results[0].title)
        assertEquals("https://en.wikipedia.org/wiki/Eiffel_Tower", results[0].url)
        assertEquals("en.wikipedia.org/wiki/Eiffel_Tower", results[0].displayUrl)
        assertTrue(results[0].snippet.contains("330 meters"))
    }
}
