package com.yash.tracker.domain.progress

/**
 * Turns dated values into 0..1 positions for a chart to draw.
 *
 * Kept out of the Composable so the cases that actually break a chart — a single point, every
 * value identical, a long gap between readings — are testable without a screen.
 */
data class ChartScale(
    private val firstDay: Long,
    private val dayRange: Float,
    private val low: Double,
    private val span: Double,
) {
    /** 0 at the left edge, 1 at the right, placed by date so gaps stay visible as gaps. */
    fun x(point: DayPoint): Float = (point.date.toEpochDay() - firstDay) / dayRange

    /** 0 at the bottom, 1 at the top. */
    fun y(value: Double): Float = ((value - low) / span).toFloat()

    companion object {
        /** Breathing room above and below, so the highest point is not pinned to the edge. */
        private const val HEADROOM = 0.1

        /** The narrowest range worth drawing: below this, the chart is only showing noise. */
        private const val MIN_RANGE = 1.0

        fun of(points: List<DayPoint>, extraValues: List<Double> = emptyList()): ChartScale? {
            if (points.isEmpty()) return null

            val days = points.map { it.date.toEpochDay() }
            val values = points.map { it.value } + extraValues
            val low = values.min()
            val high = values.max()

            // A flat series would divide by zero, and a hair-thin range would magnify noise
            // into a mountain range. Either way, give it a whole unit to work with — centred
            // on the readings, so an unchanged weight draws through the middle rather than
            // hugging the floor.
            val range = (high - low).coerceAtLeast(MIN_RANGE)
            val bottom = if (high - low < MIN_RANGE) (low + high) / 2 - range / 2 else low
            val padding = range * HEADROOM

            return ChartScale(
                firstDay = days.min(),
                // One point, or several on the same day, would otherwise divide by zero.
                dayRange = (days.max() - days.min()).coerceAtLeast(1).toFloat(),
                low = bottom - padding,
                span = range + padding * 2,
            )
        }
    }
}
