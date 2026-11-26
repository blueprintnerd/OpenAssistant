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
import com.blueprintnerd.openassistant.engine.UnitConverter
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
        override fun openWebSearch(query: String) = "Search:$query"
        override fun openUrl(url: String) = true
    }

    private class FakeStore : AssistantDataStore {
        override var useFahrenheit = true
        override var defaultCity = "New York"
        override var searchEngine = SearchEngine.DUCKDUCKGO
        override var musicProvider = MusicProvider.AUTO
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
        assertEquals("5 miles = 8.04672 kilometers", UnitConverter.tryConvert("convert 5 miles to km"))
        assertEquals("10 kilograms = 22.046226 pounds", UnitConverter.tryConvert("10 kg in lbs"))
        assertEquals("2 gigabytes = 2,048 megabytes", UnitConverter.tryConvert("2 gb to mb"))
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
    }
}
