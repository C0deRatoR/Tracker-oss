package com.yash.tracker.domain.model

import kotlin.math.roundToInt

/** Storage is always metric (TRD §3); these convert only at the UI boundary. */
object Units {

    private const val LB_PER_KG = 2.20462262
    private const val CM_PER_INCH = 2.54
    private const val INCHES_PER_FOOT = 12

    fun kgFromLb(lb: Double): Double = lb / LB_PER_KG

    fun lbFromKg(kg: Double): Double = kg * LB_PER_KG

    fun cmFromFeetInches(feet: Int, inches: Double): Double =
        (feet * INCHES_PER_FOOT + inches) * CM_PER_INCH

    fun feetInchesFromCm(cm: Double): Pair<Int, Int> {
        val totalInches = (cm / CM_PER_INCH).roundToInt()
        return totalInches / INCHES_PER_FOOT to totalInches % INCHES_PER_FOOT
    }
}

enum class HeightUnit { CM, FT_IN }

enum class WeightUnit { KG, LB }
