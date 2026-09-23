package com.yash.tracker.domain.progress

import kotlin.math.abs
import kotlin.math.sqrt

/** How two series move together, with how many pairs the number rests on. */
data class Correlation(val r: Double, val n: Int) {

    /** In words, because "r = 0.34" means nothing to most people reading a phone. */
    val strength: Strength
        get() = when (abs(r)) {
            in 0.0..<WEAK -> Strength.NONE
            in WEAK..<MODERATE -> Strength.WEAK
            in MODERATE..<STRONG -> Strength.MODERATE
            else -> Strength.STRONG
        }

    enum class Strength { NONE, WEAK, MODERATE, STRONG }

    private companion object {
        const val WEAK = 0.2
        const val MODERATE = 0.4
        const val STRONG = 0.7
    }
}

object Stats {

    /**
     * Pearson's r, or null when there is too little to say anything.
     *
     * Five pairs is the floor. Below it, a single odd week decides the answer, and a number
     * that flips sign when one more week is logged is not an insight.
     */
    fun pearson(pairs: List<Pair<Double, Double>>, minPairs: Int = MIN_PAIRS): Correlation? {
        if (pairs.size < minPairs) return null
        val meanX = pairs.sumOf { it.first } / pairs.size
        val meanY = pairs.sumOf { it.second } / pairs.size
        var cov = 0.0
        var varX = 0.0
        var varY = 0.0
        for ((x, y) in pairs) {
            cov += (x - meanX) * (y - meanY)
            varX += (x - meanX) * (x - meanX)
            varY += (y - meanY) * (y - meanY)
        }
        // A series that never moved has nothing to correlate with.
        if (varX == 0.0 || varY == 0.0) return null
        return Correlation(cov / sqrt(varX * varY), pairs.size)
    }

    /** Least-squares slope of y on x, or null for fewer than two distinct x values. */
    fun slope(points: List<Pair<Double, Double>>): Double? {
        if (points.map { it.first }.distinct().size < 2) return null
        val meanX = points.sumOf { it.first } / points.size
        val meanY = points.sumOf { it.second } / points.size
        val num = points.sumOf { (x, y) -> (x - meanX) * (y - meanY) }
        val den = points.sumOf { (x, _) -> (x - meanX) * (x - meanX) }
        return num / den
    }

    private const val MIN_PAIRS = 5
}
