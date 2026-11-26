package com.blueprintnerd.openassistant.engine

import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object MathEvaluator {
    private val percentOfRegex = Regex(
        """(\d+(?:\.\d+)?)\s*(?:%|percent)\s+of\s+(\d+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE,
    )

    fun tryEvaluateNaturalQuery(query: String): Pair<String, String>? {
        val cleaned = query
            .lowercase(Locale.US)
            .replace(Regex("""^(?:what(?:'s| is)|calculate|compute|solve|how much is|evaluate)\s+"""), "")
            .replace("?", "")
            .trim()

        if (cleaned.isEmpty()) return null

        percentOfRegex.matchEntire(cleaned)?.let { m ->
            val pct = m.groupValues[1].toDoubleOrNull() ?: return null
            val total = m.groupValues[2].toDoubleOrNull() ?: return null
            val res = (pct / 100.0) * total
            val expr = "${formatNumber(pct)}% of ${formatNumber(total)}"
            return expr to formatNumber(res)
        }

        val normalized = cleaned
            .replace(Regex("""\bsquare root of\s+"""), "sqrt(")
            .let { s ->
                val open = s.count { it == '(' }
                val close = s.count { it == ')' }
                if (open > close) s + ")".repeat(open - close) else s
            }
            .replace(Regex("""\bto the power of\b"""), "^")
            .replace(Regex("""\bmultiplied by\b"""), "*")
            .replace(Regex("""\bdivided by\b"""), "/")
            .replace(Regex("""\bplus\b"""), "+")
            .replace(Regex("""\bminus\b"""), "-")
            .replace(Regex("""\btimes\b"""), "*")
            .replace(Regex("""\bover\b"""), "/")
            .replace(Regex("""\bmod\b"""), "%")
            .replace("x", "*")
            .replace("×", "*")
            .replace("÷", "/")
            .replace(",", "")
            .trim()

        if (!normalized.any { it.isDigit() }) return null
        if (!normalized.matches(Regex("""[0-9+\-*/^%().\s a-z]+"""))) return null
        // Require at least one operator or function so plain numbers like "2026" don't trigger calculator
        val hasOp = normalized.any { it in "+-*/^%" } ||
            listOf("sqrt", "sin", "cos", "tan", "log", "ln", "abs").any { normalized.contains(it) }
        if (!hasOp) return null

        val value = runCatching { Parser(normalized).parse() }.getOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return normalized to formatNumber(value)
    }

    fun formatNumber(value: Double): String {
        val rounded = if (abs(value - value.toLong()) < 1e-9) value.toLong().toDouble() else value
        val df = DecimalFormat("#,##0.######")
        return df.format(rounded)
    }

    private class Parser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw IllegalArgumentException("Unexpected char: ${ch.toChar()}")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> x /= parseFactor()
                    eat('%'.code) -> x %= parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else if (ch in 'a'.code..'z'.code) {
                while (ch in 'a'.code..'z'.code) nextChar()
                val func = str.substring(startPos, pos)
                x = when (func) {
                    "pi" -> Math.PI
                    "e" -> Math.E
                    else -> {
                        val arg = if (eat('('.code)) {
                            val v = parseExpression()
                            if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
                            v
                        } else {
                            parseFactor()
                        }
                        when (func) {
                            "sqrt" -> sqrt(arg)
                            "sin" -> sin(Math.toRadians(arg))
                            "cos" -> cos(Math.toRadians(arg))
                            "tan" -> tan(Math.toRadians(arg))
                            "log" -> log10(arg)
                            "ln" -> ln(arg)
                            "abs" -> abs(arg)
                            else -> throw IllegalArgumentException("Unknown function: $func")
                        }
                    }
                }
            } else {
                throw IllegalArgumentException("Unexpected char: ${ch.toChar()}")
            }

            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}
