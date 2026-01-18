package su.kidoz.feature.editor.quickfix

import kotlin.math.min

/**
 * String similarity utilities for finding similar names and suggesting corrections.
 */
object StringSimilarity {
    /**
     * Calculate the Levenshtein distance between two strings.
     * This is the minimum number of single-character edits (insertions, deletions, substitutions)
     * required to change one string into the other.
     */
    fun levenshteinDistance(
        s1: String,
        s2: String,
    ): Int {
        val len1 = s1.length
        val len2 = s2.length

        // Quick checks
        if (len1 == 0) return len2
        if (len2 == 0) return len1
        if (s1 == s2) return 0

        // Use two rows instead of full matrix for memory efficiency
        var prevRow = IntArray(len2 + 1) { it }
        var currRow = IntArray(len2 + 1)

        for (i in 1..len1) {
            currRow[0] = i

            for (j in 1..len2) {
                val cost = if (s1[i - 1].equals(s2[j - 1], ignoreCase = true)) 0 else 1
                currRow[j] =
                    min(
                        min(
                            prevRow[j] + 1, // deletion
                            currRow[j - 1] + 1, // insertion
                        ),
                        prevRow[j - 1] + cost, // substitution
                    )
            }

            // Swap rows
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }

        return prevRow[len2]
    }

    /**
     * Calculate similarity score between 0.0 and 1.0.
     * 1.0 means identical, 0.0 means completely different.
     */
    fun similarity(
        s1: String,
        s2: String,
    ): Double {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val distance = levenshteinDistance(s1, s2)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    /**
     * Find the most similar strings from a list of candidates.
     * @param target The string to match against
     * @param candidates The list of possible matches
     * @param maxResults Maximum number of results to return
     * @param minSimilarity Minimum similarity score (0.0 to 1.0) to include in results
     * @return List of similar strings sorted by similarity (most similar first)
     */
    fun findSimilar(
        target: String,
        candidates: Collection<String>,
        maxResults: Int = 3,
        minSimilarity: Double = 0.5,
    ): List<String> =
        candidates
            .map { it to similarity(target, it) }
            .filter { it.second >= minSimilarity }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }

    /**
     * Find the most similar string from a list of candidates, or null if none are similar enough.
     */
    fun findMostSimilar(
        target: String,
        candidates: Collection<String>,
        minSimilarity: Double = 0.6,
    ): String? = findSimilar(target, candidates, maxResults = 1, minSimilarity = minSimilarity).firstOrNull()

    /**
     * Check if two strings are similar enough to be considered a typo.
     * Uses a threshold based on string length.
     */
    fun isLikelyTypo(
        s1: String,
        s2: String,
    ): Boolean {
        if (s1.equals(s2, ignoreCase = true)) return false

        val distance = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)

        // Allow 1 edit for short strings (<=4 chars)
        // Allow 2 edits for medium strings (5-8 chars)
        // Allow ~25% edit distance for longer strings
        val maxAllowedDistance =
            when {
                maxLen <= 4 -> 1
                maxLen <= 8 -> 2
                else -> maxLen / 4
            }

        return distance <= maxAllowedDistance
    }

    /**
     * Get a similarity description for display purposes.
     */
    fun getSimilarityDescription(similarity: Double): String =
        when {
            similarity >= 0.9 -> "very similar"
            similarity >= 0.7 -> "similar"
            similarity >= 0.5 -> "somewhat similar"
            else -> "different"
        }
}
