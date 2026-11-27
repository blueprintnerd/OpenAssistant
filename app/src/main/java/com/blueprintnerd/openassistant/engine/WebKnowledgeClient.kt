package com.blueprintnerd.openassistant.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class WikiSummary(
    val title: String,
    val description: String = "",
    val extract: String,
    val url: String,
    val thumbnailUrl: String? = null,
)

data class DictionaryResult(
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val definition: String,
    val example: String = "",
    val synonyms: List<String> = emptyList(),
)

data class InstantAnswer(
    val heading: String,
    val text: String,
    val url: String?,
    val imageUrl: String? = null,
    val facts: List<Pair<String, String>> = emptyList(),
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
                fetchWeatherInternal(lat, lon, locationLabel, useFahrenheit)
            }.getOrNull()
        }

    suspend fun fetchWeatherByCoords(lat: Double, lon: Double, useFahrenheit: Boolean): WeatherCardData? =
        withContext(Dispatchers.IO) {
            runCatching {
                val locationLabel = reverseGeocodeCity(lat, lon) ?: "Current Location"
                fetchWeatherInternal(lat, lon, locationLabel, useFahrenheit)
            }.getOrNull()
        }

    private fun reverseGeocodeCity(lat: Double, lon: Double): String? {
        val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&zoom=10"
        val json = getJson(url) ?: return null
        val addr = json.optJSONObject("address") ?: return null
        val city = addr.optString("city").ifBlank {
            addr.optString("town").ifBlank {
                addr.optString("village").ifBlank { addr.optString("county") }
            }
        }
        val state = addr.optString("state")
        return when {
            city.isNotBlank() && state.isNotBlank() -> "$city, $state"
            city.isNotBlank() -> city
            else -> null
        }
    }

    private fun fetchWeatherInternal(
        lat: Double,
        lon: Double,
        locationLabel: String,
        useFahrenheit: Boolean,
    ): WeatherCardData? {
        val tempUnit = if (useFahrenheit) "fahrenheit" else "celsius"
        val windUnit = if (useFahrenheit) "mph" else "kmh"
        val unitSym = if (useFahrenheit) "F" else "C"
        val windSym = if (useFahrenheit) "mph" else "km/h"

        val weatherUrl =
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&temperature_unit=$tempUnit&wind_speed_unit=$windUnit&timezone=auto&forecast_days=4"

        val wJson = getJson(weatherUrl) ?: return null
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

        return WeatherCardData(
            location = locationLabel,
            temperature = "$temp°$unitSym",
            condition = condition,
            details = "Feels like $feels°$unitSym | Humidity $humidity% | Wind $wind $windSym",
            forecast = forecast,
        )
    }

    suspend fun fetchWikipediaSummary(topic: String): WikiSummary? = withContext(Dispatchers.IO) {
        runCatching {
            val clean = topic.trim().replace(" ", "_")
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val direct = getJson("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded")
            if (direct != null && direct.optString("type") != "disambiguation" && direct.optString("extract").isNotBlank()) {
                return@withContext WikiSummary(
                    title = direct.optString("title", topic),
                    description = direct.optString("description", ""),
                    extract = direct.optString("extract"),
                    url = direct.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")
                        ?: "https://en.wikipedia.org/wiki/$encoded",
                    thumbnailUrl = direct.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() },
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
                description = summary.optString("description", ""),
                extract = extract,
                url = summary.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")
                    ?: "https://en.wikipedia.org/wiki/$resolvedEnc",
                thumbnailUrl = summary.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() },
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
            val firstDef = defs.getJSONObject(0)
            val synArray = meaning.optJSONArray("synonyms")
            val synonyms = buildList {
                if (synArray != null) {
                    for (i in 0 until minOf(synArray.length(), 4)) {
                        add(synArray.getString(i))
                    }
                }
            }
            DictionaryResult(
                word = entry.optString("word", word),
                phonetic = entry.optString("phonetic", ""),
                partOfSpeech = meaning.optString("partOfSpeech", ""),
                definition = firstDef.optString("definition", ""),
                example = firstDef.optString("example", ""),
                synonyms = synonyms,
            )
        }.getOrNull()
    }

    suspend fun fetchCurrencyConversion(amount: Double, from: String, to: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://api.frankfurter.app/latest?amount=$amount&from=$from&to=$to"
                val json = getJson(url) ?: return@withContext null
                val rates = json.optJSONObject("rates") ?: return@withContext null
                if (!rates.has(to)) return@withContext null
                val converted = rates.getDouble(to)
                "${MathEvaluator.formatNumber(amount)} $from = ${String.format(Locale.US, "%.2f", converted)} $to"
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

            val rawImg = json.optString("Image")
            val imageUrl = when {
                rawImg.startsWith("http") -> rawImg
                rawImg.startsWith("/") -> "https://duckduckgo.com$rawImg"
                else -> null
            }

            val facts = mutableListOf<Pair<String, String>>()
            val infobox = json.optJSONObject("Infobox")?.optJSONArray("content")
            if (infobox != null) {
                for (i in 0 until infobox.length()) {
                    if (facts.size >= 4) break
                    val item = infobox.optJSONObject(i) ?: continue
                    val dataType = item.optString("data_type")
                    val label = item.optString("label").trim()
                    val value = item.optString("value").trim()
                    if (dataType == "string" && label.isNotBlank() && value.isNotBlank() && value.length <= 60) {
                        facts += label to value
                    }
                }
            }

            InstantAnswer(
                heading = json.optString("Heading").ifBlank { query },
                text = answer,
                url = json.optString("AbstractURL").takeIf { it.isNotBlank() },
                imageUrl = imageUrl,
                facts = facts,
            )
        }.getOrNull()
    }

    suspend fun fetchWebSearchResults(query: String, maxResults: Int = 4): List<SearchResultItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val req = Request.Builder()
                    .url("https://lite.duckduckgo.com/lite/?q=$encoded")
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                val html = httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                } ?: return@withContext emptyList()
                parseDuckDuckGoLiteHtml(html, maxResults)
            }.getOrDefault(emptyList())
        }

    internal fun parseDuckDuckGoLiteHtml(html: String, maxResults: Int = 4): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()
        val rowRegex = Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val linkRegex = Regex("""<a[^>]*href="([^"]+)"[^>]*class=['"]result-link['"][^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val snipRegex = Regex("""class=['"]result-snippet['"][^>]*>(.*?)</td>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val urlTextRegex = Regex("""class=['"]link-text['"][^>]*>(.*?)</span>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        val rows = rowRegex.findAll(html).map { it.groupValues[1] }.toList()
        var idx = 0
        while (idx < rows.size && results.size < maxResults) {
            val linkMatch = linkRegex.find(rows[idx])
            if (linkMatch != null) {
                val rawHref = unescapeHtml(linkMatch.groupValues[1])
                val directUrl = extractDirectUrl(rawHref)
                val title = stripHtml(linkMatch.groupValues[2])
                val isAd = directUrl.contains("ad_domain=") ||
                    directUrl.contains("duckduckgo.com/y.js") ||
                    directUrl.contains("duckduckgo-help-pages") ||
                    rawHref.contains("ad_domain")

                if (!isAd && title.isNotBlank()) {
                    var snippet = ""
                    var displayUrl = directUrl.removePrefix("https://").removePrefix("http://").substringBefore("/")
                    for (lookAhead in 1..3) {
                        if (idx + lookAhead >= rows.size) break
                        val nextRow = rows[idx + lookAhead]
                        if (linkRegex.containsMatchIn(nextRow)) break
                        if (snippet.isEmpty()) {
                            snipRegex.find(nextRow)?.let { snippet = stripHtml(it.groupValues[1]) }
                        }
                        urlTextRegex.find(nextRow)?.let {
                            val d = stripHtml(it.groupValues[1])
                            if (d.isNotBlank()) displayUrl = d
                        }
                    }
                    if (directUrl.startsWith("http")) {
                        results += SearchResultItem(
                            title = title,
                            snippet = snippet,
                            displayUrl = displayUrl,
                            url = directUrl,
                        )
                    }
                }
            }
            idx++
        }
        return results
    }

    private fun extractDirectUrl(href: String): String {
        val uddgMatch = Regex("""[?&]uddg=([^&]+)""").find(href)
        if (uddgMatch != null) {
            return runCatching { URLDecoder.decode(uddgMatch.groupValues[1], "UTF-8") }.getOrDefault(href)
        }
        return when {
            href.startsWith("http") -> href
            href.startsWith("//") -> "https:$href"
            else -> href
        }
    }

    private fun stripHtml(raw: String): String {
        val noTags = raw.replace(Regex("<[^>]+>"), " ")
        return unescapeHtml(noTags).replace(Regex("\\s+"), " ").trim()
    }

    private fun unescapeHtml(text: String): String = text
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")

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
        private const val USER_AGENT = "OpenAssistant/1.1 (https://github.com/blueprintnerd/OpenAssistant)"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
