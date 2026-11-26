package com.blueprintnerd.openassistant.engine

import java.util.Locale

object UnitConverter {
    private enum class Category { LENGTH, MASS, VOLUME, AREA, SPEED, DATA, TIME, ENERGY, TEMPERATURE }

    private data class UnitDef(
        val canonical: String,
        val category: Category,
        val toBaseFactor: Double,
    )

    private val units: Map<String, UnitDef> = buildMap {
        fun add(canonical: String, cat: Category, factor: Double, vararg aliases: String) {
            val def = UnitDef(canonical, cat, factor)
            put(canonical, def)
            aliases.forEach { put(it, def) }
        }

        // Length (base: meter)
        add("millimeters", Category.LENGTH, 0.001, "mm", "millimeter", "millimetre", "millimetres")
        add("centimeters", Category.LENGTH, 0.01, "cm", "centimeter", "centimetre", "centimetres")
        add("meters", Category.LENGTH, 1.0, "m", "meter", "metre", "metres")
        add("kilometers", Category.LENGTH, 1000.0, "km", "kms", "kilometer", "kilometre", "kilometres")
        add("inches", Category.LENGTH, 0.0254, "in", "inch")
        add("feet", Category.LENGTH, 0.3048, "ft", "foot")
        add("yards", Category.LENGTH, 0.9144, "yd", "yds", "yard")
        add("miles", Category.LENGTH, 1609.344, "mi", "mile")

        // Mass (base: kilogram)
        add("milligrams", Category.MASS, 1e-6, "mg", "milligram")
        add("grams", Category.MASS, 0.001, "g", "gram")
        add("kilograms", Category.MASS, 1.0, "kg", "kgs", "kilo", "kilos", "kilogram")
        add("ounces", Category.MASS, 0.028349523125, "oz", "ounce")
        add("pounds", Category.MASS, 0.45359237, "lb", "lbs", "pound")
        add("tons", Category.MASS, 907.18474, "ton", "short ton")

        // Volume (base: liter)
        add("milliliters", Category.VOLUME, 0.001, "ml", "milliliter", "millilitre", "millilitres")
        add("liters", Category.VOLUME, 1.0, "l", "liter", "litre", "litres")
        add("fluid ounces", Category.VOLUME, 0.0295735, "fl oz", "fluid ounce")
        add("teaspoons", Category.VOLUME, 0.00492892, "tsp", "teaspoon")
        add("tablespoons", Category.VOLUME, 0.0147868, "tbsp", "tablespoon")
        add("cups", Category.VOLUME, 0.236588, "cup")
        add("pints", Category.VOLUME, 0.473176, "pt", "pint")
        add("quarts", Category.VOLUME, 0.946353, "qt", "quart")
        add("gallons", Category.VOLUME, 3.78541, "gal", "gallon")

        // Area (base: square meter)
        add("square meters", Category.AREA, 1.0, "sqm", "sq m", "m2", "square meter")
        add("square feet", Category.AREA, 0.092903, "sqft", "sq ft", "ft2", "square foot")
        add("square miles", Category.AREA, 2_589_988.11, "sq mi", "mi2", "square mile")
        add("square kilometers", Category.AREA, 1_000_000.0, "sq km", "km2", "square kilometer")
        add("acres", Category.AREA, 4046.8564, "acre")
        add("hectares", Category.AREA, 10_000.0, "ha", "hectare")

        // Speed (base: m/s)
        add("m/s", Category.SPEED, 1.0, "mps", "meters per second")
        add("km/h", Category.SPEED, 1.0 / 3.6, "kph", "kmh", "kilometers per hour")
        add("mph", Category.SPEED, 0.44704, "miles per hour")
        add("knots", Category.SPEED, 0.514444, "knot", "kt")

        // Data (base: byte)
        add("bytes", Category.DATA, 1.0, "b", "byte")
        add("kilobytes", Category.DATA, 1024.0, "kb", "kilobyte")
        add("megabytes", Category.DATA, 1024.0 * 1024.0, "mb", "megabyte")
        add("gigabytes", Category.DATA, 1024.0 * 1024.0 * 1024.0, "gb", "gigabyte")
        add("terabytes", Category.DATA, 1024.0 * 1024.0 * 1024.0 * 1024.0, "tb", "terabyte")

        // Time (base: second)
        add("seconds", Category.TIME, 1.0, "s", "sec", "second")
        add("minutes", Category.TIME, 60.0, "min", "mins", "minute")
        add("hours", Category.TIME, 3600.0, "hr", "hrs", "hour")
        add("days", Category.TIME, 86400.0, "day")
        add("weeks", Category.TIME, 604800.0, "week", "wk")

        // Energy (base: joule)
        add("joules", Category.ENERGY, 1.0, "j", "joule")
        add("kilojoules", Category.ENERGY, 1000.0, "kj", "kilojoule")
        add("calories", Category.ENERGY, 4184.0, "kcal", "cal", "calorie")

        // Temperature
        add("celsius", Category.TEMPERATURE, 1.0, "c", "°c", "deg c", "degrees c", "degrees celsius")
        add("fahrenheit", Category.TEMPERATURE, 1.0, "f", "°f", "deg f", "degrees f", "degrees fahrenheit")
        add("kelvin", Category.TEMPERATURE, 1.0, "k", "°k")
    }

    private val currencyMap: Map<String, String> = mapOf(
        "usd" to "USD", "dollar" to "USD", "dollars" to "USD", "us dollar" to "USD", "us dollars" to "USD", "$" to "USD",
        "eur" to "EUR", "euro" to "EUR", "euros" to "EUR", "€" to "EUR",
        "gbp" to "GBP", "pound sterling" to "GBP", "british pound" to "GBP", "british pounds" to "GBP", "£" to "GBP",
        "jpy" to "JPY", "yen" to "JPY", "japanese yen" to "JPY", "¥" to "JPY",
        "cad" to "CAD", "canadian dollar" to "CAD", "canadian dollars" to "CAD",
        "aud" to "AUD", "australian dollar" to "AUD", "australian dollars" to "AUD",
        "chf" to "CHF", "swiss franc" to "CHF", "swiss francs" to "CHF",
        "cny" to "CNY", "yuan" to "CNY", "rmb" to "CNY",
        "inr" to "INR", "rupee" to "INR", "rupees" to "INR",
        "mxn" to "MXN", "peso" to "MXN", "pesos" to "MXN",
        "brl" to "BRL", "real" to "BRL", "reais" to "BRL",
        "krw" to "KRW", "won" to "KRW",
        "sek" to "SEK", "nok" to "NOK", "dkk" to "DKK", "pln" to "PLN", "nzd" to "NZD", "sgd" to "SGD", "hkd" to "HKD",
    )

    private val forwardPattern = Regex(
        """^(?:convert\s+|what(?:'s| is)\s+)?(-?\d+(?:\.\d+)?)\s+([a-z°$€£¥/ ]+?)\s+(?:to|in|into)\s+([a-z°$€£¥/ ]+?)\??$""",
        RegexOption.IGNORE_CASE,
    )

    private val howManyPattern = Regex(
        """^how many\s+([a-z°/ ]+?)\s+(?:are\s+)?in\s+(?:a\s+|an\s+)?(\d+(?:\.\d+)?)?\s*([a-z°/ ]+?)\??$""",
        RegexOption.IGNORE_CASE,
    )

    fun tryConvert(query: String): String? {
        val trimmed = query.trim()
        forwardPattern.matchEntire(trimmed)?.let { m ->
            val value = m.groupValues[1].toDoubleOrNull() ?: return null
            val fromKey = m.groupValues[2].trim().lowercase(Locale.US)
            val toKey = m.groupValues[3].trim().lowercase(Locale.US)
            return convertUnits(value, fromKey, toKey)
        }
        howManyPattern.matchEntire(trimmed)?.let { m ->
            val toKey = m.groupValues[1].trim().lowercase(Locale.US)
            val value = m.groupValues[2].toDoubleOrNull() ?: 1.0
            val fromKey = m.groupValues[3].trim().lowercase(Locale.US)
            return convertUnits(value, fromKey, toKey)
        }
        return null
    }

    fun tryParseCurrencyQuery(query: String): Triple<Double, String, String>? {
        val m = forwardPattern.matchEntire(query.trim()) ?: return null
        val amount = m.groupValues[1].toDoubleOrNull() ?: return null
        val fromKey = m.groupValues[2].trim().lowercase(Locale.US)
        val toKey = m.groupValues[3].trim().lowercase(Locale.US)
        val fromCode = currencyMap[fromKey] ?: return null
        val toCode = currencyMap[toKey] ?: return null
        if (fromCode == toCode) return null
        return Triple(amount, fromCode, toCode)
    }

    private fun convertUnits(value: Double, fromKey: String, toKey: String): String? {
        val from = units[fromKey] ?: return null
        val to = units[toKey] ?: return null
        if (from.category != to.category) return null

        val converted = if (from.category == Category.TEMPERATURE) {
            convertTemperature(value, from.canonical, to.canonical)
        } else {
            val base = value * from.toBaseFactor
            base / to.toBaseFactor
        }

        return "${MathEvaluator.formatNumber(value)} ${from.canonical} = ${MathEvaluator.formatNumber(converted)} ${to.canonical}"
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        val celsius = when (from) {
            "celsius" -> value
            "fahrenheit" -> (value - 32.0) * 5.0 / 9.0
            "kelvin" -> value - 273.15
            else -> value
        }
        return when (to) {
            "celsius" -> celsius
            "fahrenheit" -> celsius * 9.0 / 5.0 + 32.0
            "kelvin" -> celsius + 273.15
            else -> celsius
        }
    }
}
