package com.blueprintnerd.openassistant.engine

import java.util.Locale

/**
 * Normalizes raw voice transcripts from on-device STT before deterministic routing:
 * - Strips accidental wake-word bleed ("hey google ...", "ok google ...")
 * - Strips polite filler prefixes/suffixes ("can you please ...", "... please")
 * - Converts spoken English numbers and clock times into digits ("five minutes" -> "5 minutes",
 *   "seven thirty am" -> "7:30 am", "half an hour" -> "30 minutes")
 */
object SpeechNormalizer {
    private val wakePrefixRegex = Regex(
        """^(?:(?:hey|ok|okay)\s+(?:google|assistant|open\s+assistant)[,\s]*)+""",
        RegexOption.IGNORE_CASE,
    )

    private val fillerPrefixRegex = Regex(
        """^(?:(?:um+|uh+|well|so|please|can you|could you|would you|will you|i want you to|i need you to|go ahead and)\s+)+""",
        RegexOption.IGNORE_CASE,
    )

    private val fillerSuffixRegex = Regex(
        """[\s,]+(?:please|thanks|thank you)$""",
        RegexOption.IGNORE_CASE,
    )

    private val units = mapOf(
        "zero" to 0, "oh" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )

    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    fun normalize(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return s

        // 1. Strip wake word bleed and filler wrappers
        s = wakePrefixRegex.replace(s, "").trim()
        s = fillerPrefixRegex.replace(s, "").trim()
        s = fillerSuffixRegex.replace(s, "").trim()

        // 2. Common duration/time idioms
        s = s
            .replace(Regex("""\bhalf an hour\b""", RegexOption.IGNORE_CASE), "30 minutes")
            .replace(Regex("""\ba quarter (?:of an )?hour\b""", RegexOption.IGNORE_CASE), "15 minutes")
            .replace(Regex("""\ban hour\b""", RegexOption.IGNORE_CASE), "1 hour")
            .replace(Regex("""\ba minute\b""", RegexOption.IGNORE_CASE), "1 minute")
            .replace(Regex("""\ba second\b""", RegexOption.IGNORE_CASE), "1 second")
            .replace(Regex("""\ba couple(?: of)?\b""", RegexOption.IGNORE_CASE), "2")
            .replace(Regex("""\bnoon\b""", RegexOption.IGNORE_CASE), "12:00 pm")
            .replace(Regex("""\bmidnight\b""", RegexOption.IGNORE_CASE), "12:00 am")

        // 3. Spoken clock times like "seven thirty am", "six o'clock", "eight oh five"
        s = convertSpokenClockTimes(s)

        // 4. Convert remaining spoken numbers to digits
        s = convertNumberWords(s)

        return s.replace(Regex("""\s+"""), " ").trim()
    }

    private fun convertSpokenClockTimes(input: String): String {
        var result = input
        // "eight o'clock" -> "8:00"
        val oClockRegex = Regex(
            """\b(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|\d{1,2})\s+o[' ]?clock\b""",
            RegexOption.IGNORE_CASE,
        )
        result = oClockRegex.replace(result) { m ->
            val h = units[m.groupValues[1].lowercase(Locale.US)] ?: m.groupValues[1].toIntOrNull()
            if (h != null && h in 1..12) "$h:00" else m.value
        }

        // "seven thirty" / "seven forty five" / "eight oh five" when followed by am/pm or preceded by at/for
        val hourWord = "(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"
        val minWord = "(?:oh\\s+(?:one|two|three|four|five|six|seven|eight|nine)|(?:twenty|thirty|forty|fifty)(?:[\\s-]+(?:one|two|three|four|five|six|seven|eight|nine))?|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen)"
        val clockRegex = Regex(
            """\b(at|for)\s+($hourWord)\s+($minWord)\b""",
            RegexOption.IGNORE_CASE,
        )
        result = clockRegex.replace(result) { m ->
            val prep = m.groupValues[1]
            val h = units[m.groupValues[2].lowercase(Locale.US)] ?: return@replace m.value
            val min = parseTwoWordMinute(m.groupValues[3]) ?: return@replace m.value
            String.format(Locale.US, "%s %d:%02d", prep, h, min)
        }

        val clockAmPmRegex = Regex(
            """\b($hourWord)\s+($minWord)\s*(a\.?m\.?|p\.?m\.?)\b""",
            RegexOption.IGNORE_CASE,
        )
        result = clockAmPmRegex.replace(result) { m ->
            val h = units[m.groupValues[1].lowercase(Locale.US)] ?: return@replace m.value
            val min = parseTwoWordMinute(m.groupValues[2]) ?: return@replace m.value
            val amPm = m.groupValues[3]
            String.format(Locale.US, "%d:%02d %s", h, min, amPm)
        }
        return result
    }

    private fun parseTwoWordMinute(raw: String): Int? {
        val parts = raw.lowercase(Locale.US).replace("-", " ").trim().split(Regex("\\s+"))
        return when (parts.size) {
            1 -> units[parts[0]] ?: tens[parts[0]]
            2 -> {
                if (parts[0] == "oh") {
                    units[parts[1]]?.takeIf { it in 1..9 }
                } else {
                    val t = tens[parts[0]] ?: return null
                    val u = units[parts[1]]?.takeIf { it in 1..9 } ?: return null
                    t + u
                }
            }
            else -> null
        }?.takeIf { it in 0..59 }
    }

    private fun convertNumberWords(input: String): String {
        val tokens = input.split(" ")
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val rawTok = tokens[i]
            val clean = rawTok.lowercase(Locale.US).trimEnd(',', '.', '?', '!')
            val suffixPunct = rawTok.substring(clean.length)

            // Handle "a hundred" / "one hundred"
            if ((clean == "a" || clean == "one") && i + 1 < tokens.size &&
                tokens[i + 1].lowercase(Locale.US).trimEnd(',', '.', '?', '!') == "hundred"
            ) {
                var value = 100
                i += 2
                if (i < tokens.size && tokens[i].equals("and", true)) i++
                if (i < tokens.size) {
                    val (added, consumed) = parseUpTo99(tokens, i)
                    if (consumed > 0) {
                        value += added
                        i += consumed
                    }
                }
                out += value.toString()
                continue
            }

            // Don't convert standalone "oh" outside clock times
            if (clean == "oh") {
                out += rawTok
                i++
                continue
            }

            val (num, consumed) = parseUpTo99(tokens, i)
            if (consumed > 0) {
                val lastRaw = tokens[i + consumed - 1]
                val lastClean = lastRaw.lowercase(Locale.US).trimEnd(',', '.', '?', '!')
                val endPunct = lastRaw.substring(lastClean.length)
                // Check for "hundred" after a single unit (e.g. "two hundred")
                if (num in 1..9 && i + consumed < tokens.size &&
                    tokens[i + consumed].lowercase(Locale.US).trimEnd(',', '.', '?', '!') == "hundred"
                ) {
                    var value = num * 100
                    i += consumed + 1
                    if (i < tokens.size && tokens[i].equals("and", true)) i++
                    val (rem, remConsumed) = parseUpTo99(tokens, i)
                    if (remConsumed > 0) {
                        value += rem
                        i += remConsumed
                    }
                    out += value.toString()
                } else {
                    out += "$num$endPunct"
                    i += consumed
                }
            } else {
                out += if (suffixPunct.isEmpty()) rawTok else rawTok
                i++
            }
        }
        return out.joinToString(" ")
    }

    private fun parseUpTo99(tokens: List<String>, start: Int): Pair<Int, Int> {
        if (start >= tokens.size) return 0 to 0
        val w1 = tokens[start].lowercase(Locale.US).trimEnd(',', '.', '?', '!')
        // Hyphenated like "twenty-five"
        if (w1.contains("-")) {
            val parts = w1.split("-")
            if (parts.size == 2) {
                val t = tens[parts[0]]
                val u = units[parts[1]]?.takeIf { it in 1..9 }
                if (t != null && u != null) return (t + u) to 1
            }
        }
        tens[w1]?.let { t ->
            if (start + 1 < tokens.size) {
                val w2 = tokens[start + 1].lowercase(Locale.US).trimEnd(',', '.', '?', '!')
                val u = units[w2]?.takeIf { it in 1..9 }
                if (u != null) return (t + u) to 2
            }
            return t to 1
        }
        if (w1 != "oh") {
            units[w1]?.let { u -> return u to 1 }
        }
        return 0 to 0
    }
}
