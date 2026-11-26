package com.blueprintnerd.openassistant.speech

import java.io.File
import java.util.Locale

/**
 * Converts plain wake phrases (e.g. "HEY GOOGLE", "OK GOOGLE") into Sherpa-ONNX
 * SentencePiece BPE keyword lines using the model's tokens.txt vocabulary.
 */
object BpeKeywordTokenizer {
    private const val SPIECE_UNDERLINE = "\u2581"

    fun loadVocab(tokensFile: File): Set<String> {
        if (!tokensFile.exists()) return emptySet()
        return tokensFile.readLines()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                parts.firstOrNull()?.takeIf { it.isNotEmpty() }
            }
            .toSet()
    }

    fun tokenizePhrase(phrase: String, vocab: Set<String>): List<String> {
        val words = phrase
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9'\\s]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        val result = mutableListOf<String>()
        for (word in words) {
            result += segmentWord(SPIECE_UNDERLINE + word, vocab)
        }
        return result
    }

    private fun segmentWord(word: String, vocab: Set<String>): List<String> {
        if (vocab.isEmpty()) return listOf(word)
        val tokens = mutableListOf<String>()
        var index = 0
        while (index < word.length) {
            var matched: String? = null
            var end = word.length
            while (end > index) {
                val candidate = word.substring(index, end)
                if (candidate in vocab) {
                    matched = candidate
                    break
                }
                end--
            }
            if (matched != null) {
                tokens += matched
                index += matched.length
            } else {
                // Skip unknown character if not in vocab
                index++
            }
        }
        return tokens
    }

    fun buildKeywordsFileContent(
        phrases: List<String>,
        vocab: Set<String>,
        boostScore: Float = 1.5f,
        threshold: Float = 0.25f,
    ): String {
        val sb = StringBuilder()
        for (phrase in phrases) {
            val clean = phrase.trim()
            if (clean.isEmpty()) continue
            val tokens = tokenizePhrase(clean, vocab)
            if (tokens.isEmpty()) continue
            sb.append(tokens.joinToString(" "))
            sb.append(" :").append(String.format(Locale.US, "%.2f", boostScore))
            sb.append(" #").append(String.format(Locale.US, "%.2f", threshold))
            sb.append(" @").append(clean.replace(" ", "_"))
            sb.append('\n')
        }
        return sb.toString()
    }
}
