package com.blueprintnerd.openassistant.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class WikiSummary(
    val title: String,
    val extract: String,
    val url: String,
)

data class DictionaryResult(
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val definition: String,
)

data class InstantAnswer(
    val heading: String,
    val text: String,
    val url: String?,
)

class WebKnowledgeClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun fetchWeather(city: String, useFahrenheit: Boolean): WeatherCardData? =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(city, "UTF-8")
                val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
                val geoJson = getJson(geoUrl) ?: return@withContext null
                val results = geoJson.optJSONArray("results") ?: return@withContext null
                if (results.length() == 0) return@withContext null
                val place = results.getJSONObject(0)
                val lat = place.getDouble("latitude")
                val lon = place.getDouble("longitude")
                val name = place.optString("name", city)
                val admin = place.optString("admin1", "")
                val locationLabel = if (admin.isNotEmpty() && !admin.equals(name, true)) "$name, $admin" else name

                val tempUnit = if (useFahrenheit) "fahrenheit" else "celsius"
                val windUnit = if (useFahrenheit) "mph" else "kmh"
                val unitSym = if (useFahrenheit) "F" else "C"
                val windSym = if (useFahrenheit) "mph" else "km/h"

                val weatherUrl =
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                        "&temperature_unit=$tempUnit&wind_speed_unit=$windUnit&timezone=auto&forecast_days=4"

                val wJson = getJson(weatherUrl) ?: return@withContext null
                val current = wJson.getJSONObject("current")
                val temp = current.getDouble("temperature_2m").toInt()
                val feels = current.getDouble("apparent_temperature").toInt()
                val humidity = current.getInt("relative_humidity_2m")
                val wind = current.getDouble("wind_speed_10m").toInt()
                val code = current.getInt("weather_code")
                val condition = wmoDescription(code)

                val daily = wJson.optJSONObject("daily")
                val forecast = mutableListOf<ForecastDay>()
                if (daily != null) {
                    val times = daily.optJSONArray("time")
                    val codes = daily.optJSONArray("weather_code")
                    val highs = daily.optJSONArray("temperature_2m_max")
                    val lows = daily.optJSONArray("temperature_2m_min")
                    val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val outFmt = SimpleDateFormat("EEE", Locale.US)
                    val count = minOf(times?.length() ?: 0, 4)
                    for (i in 0 until count) {
                        val dateStr = times?.optString(i) ?: continue
                        val dayLabel = if (i == 0) {
                            "Today"
                        } else {
                            runCatching { outFmt.format(inFmt.parse(dateStr)!!) }.getOrDefault(dateStr)
                        }
                        forecast += ForecastDay(
                            dayLabel = dayLabel,
                            condition = wmoDescription(codes?.optInt(i) ?: 0),
                            high = "${highs?.optDouble(i)?.toInt() ?: 0}°",
                            low = "${lows?.optDouble(i)?.toInt() ?: 0}°",
                        )
                    }
                }

                WeatherCardData(
                    location = locationLabel,
                    temperature = "$temp°$unitSym",
                    condition = condition,
                    details = "Feels like $feels°$unitSym | Humidity $humidity% | Wind $wind $windSym",
                    forecast = forecast,
                )
            }.getOrNull()
        }

    suspend fun fetchWikipediaSummary(topic: String): WikiSummary? = withContext(Dispatchers.IO) {
        runCatching {
            val clean = topic.trim().replace(" ", "_")
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val direct = getJson("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded")
            if (direct != null && direct.optString("type") != "disambiguation" && direct.optString("extract").isNotBlank()) {
                return@withContext WikiSummary(
                    title = direct.optString("title", topic),
                    extract = direct.optString("extract"),
                    url = direct.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")
                        ?: "https://en.wikipedia.org/wiki/$encoded",
                )
            }

            // Fallback to OpenSearch to resolve exact title
            val searchEnc = URLEncoder.encode(topic.trim(), "UTF-8")
            val searchReq = Request.Builder()
                .url("https://en.wikipedia.org/w/api.php?action=opensearch&search=$searchEnc&limit=1&namespace=0&format=json")
                .header("User-Agent", USER_AGENT)
                .build()
            val body = httpClient.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            } ?: return@withContext null

            val arr = JSONArray(body)
            val titles = arr.optJSONArray(1) ?: return@withContext null
            if (titles.length() == 0) return@withContext null
            val resolvedTitle = titles.getString(0)
            val resolvedEnc = URLEncoder.encode(resolvedTitle.replace(" ", "_"), "UTF-8")
            val summary = getJson("https://en.wikipedia.org/api/rest_v1/page/summary/$resolvedEnc")
                ?: return@withContext null
            val extract = summary.optString("extract")
            if (extract.isBlank()) return@withContext null
            WikiSummary(
                title = summary.optString("title", resolvedTitle),
                extract = extract,
                url = summary.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")
                    ?: "https://en.wikipedia.org/wiki/$resolvedEnc",
            )
        }.getOrNull()
    }

    suspend fun fetchDefinition(word: String): DictionaryResult? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(word.trim().lowercase(Locale.US), "UTF-8")
            val req = Request.Builder()
                .url("https://api.dictionaryapi.dev/api/v2/entries/en/$encoded")
                .header("User-Agent", USER_AGENT)
                .build()
            val raw = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            } ?: return@withContext null
            val arr = JSONArray(raw)
            if (arr.length() == 0) return@withContext null
            val entry = arr.getJSONObject(0)
            val meanings = entry.optJSONArray("meanings") ?: return@withContext null
            if (meanings.length() == 0) return@withContext null
            val meaning = meanings.getJSONObject(0)
            val defs = meaning.optJSONArray("definitions") ?: return@withContext null
            if (defs.length() == 0) return@withContext null
            DictionaryResult(
                word = entry.optString("word", word),
                phonetic = entry.optString("phonetic", ""),
                partOfSpeech = meaning.optString("partOfSpeech", ""),
                definition = defs.getJSONObject(0).optString("definition", ""),
            )
        }.getOrNull()
    }

    suspend fun fetchDuckDuckGoInstantAnswer(query: String): InstantAnswer? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val json = getJson("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                ?: return@withContext null
            val answer = json.optString("Answer").takeIf { it.isNotBlank() }
                ?: json.optString("AbstractText").takeIf { it.isNotBlank() }
                ?: json.optString("Definition").takeIf { it.isNotBlank() }
                ?: return@withContext null
            InstantAnswer(
                heading = json.optString("Heading").ifBlank { query },
                text = answer,
                url = json.optString("AbstractURL").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    suspend fun callHomeAssistantService(
        baseUrl: String,
        token: String,
        targetName: String,
        turnOn: Boolean,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val statesReq = Request.Builder()
                .url("$baseUrl/api/states")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()
            val statesRaw = httpClient.newCall(statesReq).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Home Assistant returned HTTP ${resp.code}")
                resp.body?.string() ?: "[]"
            }

            val states = JSONArray(statesRaw)
            val needle = targetName.lowercase(Locale.US).trim()
            var matchedEntityId: String? = null
            var matchedFriendly: String? = null

            for (i in 0 until states.length()) {
                val obj = states.getJSONObject(i)
                val entityId = obj.optString("entity_id")
                val domain = entityId.substringBefore(".")
                if (domain !in setOf("light", "switch", "fan", "media_player", "climate", "scene", "script", "input_boolean")) {
                    continue
                }
                val friendly = obj.optJSONObject("attributes")?.optString("friendly_name", "") ?: ""
                if (friendly.equals(needle, true) || entityId.equals(needle, true)) {
                    matchedEntityId = entityId
                    matchedFriendly = friendly.ifBlank { entityId }
                    break
                }
                if (matchedEntityId == null && (friendly.lowercase(Locale.US).contains(needle) || entityId.lowercase(Locale.US).contains(needle.replace(" ", "_")))) {
                    matchedEntityId = entityId
                    matchedFriendly = friendly.ifBlank { entityId }
                }
            }

            val entityId = matchedEntityId
                ?: throw IllegalStateException("Could not find a Home Assistant device matching '$targetName'.")
            val domain = entityId.substringBefore(".")
            val service = when {
                domain == "scene" || domain == "script" -> "turn_on"
                turnOn -> "turn_on"
                else -> "turn_off"
            }

            val payload = JSONObject().put("entity_id", entityId).toString()
            val postReq = Request.Builder()
                .url("$baseUrl/api/services/$domain/$service")
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(postReq).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Home Assistant service error HTTP ${resp.code}")
            }

            val verb = if (turnOn) "on" else "off"
            "Turned $verb ${matchedFriendly ?: entityId}."
        }
    }

    private fun getJson(url: String): JSONObject? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        val body = httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        } ?: return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

    private fun wmoDescription(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Fair"
    }

    companion object {
        private const val USER_AGENT = "OpenAssistant/1.0 (https://github.com/blueprintnerd/OpenAssistant)"
    }
}
