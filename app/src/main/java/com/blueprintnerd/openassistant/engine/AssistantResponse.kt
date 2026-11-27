package com.blueprintnerd.openassistant.engine

enum class CardType {
    TEXT,
    WEATHER,
    CALCULATOR,
    WIKIPEDIA,
    KNOWLEDGE,
    SEARCH_RESULTS,
    LIST,
    ACTION_CONFIRMATION,
    ERROR,
}

data class ForecastDay(
    val dayLabel: String,
    val condition: String,
    val high: String,
    val low: String,
)

data class WeatherCardData(
    val location: String,
    val temperature: String,
    val condition: String,
    val details: String,
    val forecast: List<ForecastDay> = emptyList(),
)

data class KnowledgeCardData(
    val title: String,
    val subtitle: String = "",
    val summary: String,
    val imageUrl: String? = null,
    val sourceLabel: String = "Wikipedia",
    val sourceUrl: String? = null,
    val facts: List<Pair<String, String>> = emptyList(),
)

data class SearchResultItem(
    val title: String,
    val snippet: String,
    val displayUrl: String,
    val url: String,
)

data class ResponseAction(
    val label: String,
    val command: String? = null,
    val url: String? = null,
)

data class AssistantResponse(
    val title: String,
    val body: String,
    val spokenText: String = body,
    val cardType: CardType = CardType.TEXT,
    val weather: WeatherCardData? = null,
    val knowledge: KnowledgeCardData? = null,
    val searchResults: List<SearchResultItem> = emptyList(),
    val listItems: List<String> = emptyList(),
    val actions: List<ResponseAction> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val dismissAfterMs: Long? = null,
    val expectFollowUp: Boolean = false,
)
