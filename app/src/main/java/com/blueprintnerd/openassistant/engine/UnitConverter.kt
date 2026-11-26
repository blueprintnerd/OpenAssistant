package com.blueprintnerd.openassistant.engine

import java.util.Locale

object UnitConverter {
    private enum class Category { LENGTH, MASS, VOLUME, SPEED, DATA, TIME, TEMPERATURE }

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
        add("kilometers", Category.LENGTH, 1000.0, "km", "kilometer", "kilometre", "kilometres")
        add("inches", Category.LENGTH, 0.0254, "in", "inch")
        add("feet", Category.LENGTH, 0.3048, "ft", "foot")
        add("yards", Category.LENGTH, 0.9144, "yd", "yard")
        add("miles", Category.LENGTH, 1609.344, "mi", "mile")

        // Mass (base: kilogram)
        add("milligrams", Category.MASS, 1e-6, "mg", "milligram")
        add("grams", Category.MASS, 0.001, "g", "gram")
        add("kilograms", Category.MASS, 1.0, "kg", "kilo", "kilos", "kilogram")
        add("ounces", Category.MASS, 0.028349523125, "oz", "ounce")
        add("pounds", Category.MASS, 0.45359237, "lb", "lbs", "pound")
        add("tons", Category.MASS, 907.18474, "ton", "short ton")

        // Volume (base: liter)
        add("milliliters", Category.VOLUME, 0.001, "ml", "milliliter", "millilitre", "millilitres")
        add("liters", Category.VOLUME, 1.0, "l", "liter", "litre", "litres")
        add("teaspoons", Category.VOLUME, 0.00492892, "tsp", "teaspoon")
        add("tablespoons", Category.VOLUME, 0.0147868, "tbsp", "tablespoon")
        add("cups", Category.VOLUME, 0.236588, "cup")
        add("pints", Category.VOLUME, 0.473176, "pt", "pint")
        add("quarts", Category.VOLUME, 0.946353, "qt", "quart")
        add("gallons", Category.VOLUME, 3.78541, "gal", "gallon")

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
        add("minutes", Category.TIME, 60.0, "min", "minute")
        add("hours", Category.TIME, 3600.0, "hr", "hrs", "hour")
        add("days", Category.TIME, 86400.0, "day")
        add("weeks", Category.TIME, 604800.0, "week", "wk")

        // Temperature
        add("celsius", Category.TEMPERATURE, 1.0, "c", "deg c", "degrees c", "degrees celsius")
        add("fahrenheit", Category.TEMPERATURE, 1.0, "f", "deg f", "degrees f", "degrees fahrenheit")
        add("kelvin", Category.TEMPERATURE, 1.0, "k")
    }

    private val pattern = Regex(
        """^(?:convert\s+|what(?:'s| is)\s+|how many\s+)?(-?\d+(?:\.\d+)?)\s+([a-z/ ]+?)\s+(?:to|in|into)\s+([a-z/ ]+?)\??$""",
        RegexOption.IGNORE_CASE,
    )

    fun tryConvert(query: String): String? {
        val m = pattern.matchEntire(query.trim()) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        val fromKey = m.groupValues[2].trim().lowercase(Locale.US)
        val toKey = m.groupValues[3].trim().lowercase(Locale.US)

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
