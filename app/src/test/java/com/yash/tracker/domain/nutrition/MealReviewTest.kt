package com.yash.tracker.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scoring, checked against plates whose answer is not in dispute.
 *
 * The case that matters most is the banana: it is mostly sugar by the numbers and is not a
 * problem, and any rule that cannot tell it from a sweet is the wrong rule.
 */
class MealReviewTest {

    private fun review(
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        fibre: Double? = null,
        sugar: Double? = null,
        sodium: Double? = null,
        covered: Double = kcal,
    ) = MealReviewer.review(
        macros = Macros(kcal, protein, carbs, fat),
        micros = Micros(fibreG = fibre, sugarG = sugar, sodiumMg = sodium),
        microKcal = covered,
    )

    private fun rated(review: MealReview): MealReview.Rated =
        review as? MealReview.Rated ?: error("expected a rating, got $review")

    @Test
    fun `barfi scores badly and says sugar is why`() {
        val result = rated(
            review(kcal = 284.0, protein = 5.4, carbs = 40.4, fat = 11.4, fibre = 0.4, sugar = 37.0, sodium = 71.0),
        )

        assertEquals(Verdict.POOR, result.verdict)

        val sugar = result.notes.first()
        assertEquals("Sugar", sugar.label)
        assertEquals("37 g", sugar.amount)
        assertEquals("Poor", sugar.verdict)
        assertEquals(ReviewTone.CONCERN, sugar.tone)
    }

    @Test
    fun `a banana is mostly sugar and is not marked down for it`() {
        // 120 g: the sugar is high against the carbs and low against the fibre, which is the
        // whole point of scoring the ratio rather than the share.
        val result = rated(review(kcal = 107.0, protein = 1.3, carbs = 27.0, fat = 0.4, fibre = 3.1, sugar = 14.4, sodium = 1.0))

        val sugarNote = result.notes.single { it.label == "Sugar" }
        assertTrue("sugar should not be the concern here", sugarNote.tone != ReviewTone.CONCERN)
        assertEquals("14 g", sugarNote.amount)
    }

    @Test
    fun `a sweet drink is all sugar and no fibre`() {
        val result = rated(review(kcal = 139.0, protein = 0.0, carbs = 35.0, fat = 0.0, fibre = 0.0, sugar = 35.0, sodium = 30.0))

        assertEquals(Verdict.POOR, result.verdict)

        // All sugar and nothing with it: the worst case for the ratio, not a skipped one.
        assertEquals("Poor", result.notes.single { it.label == "Sugar" }.verdict)
        assertEquals("0 g", result.notes.single { it.label == "Fibre" }.amount)
    }

    @Test
    fun `dal and rice comes out well`() {
        val result = rated(review(kcal = 290.0, protein = 11.7, carbs = 52.0, fat = 3.0, fibre = 5.0, sugar = 1.0, sodium = 400.0))

        assertEquals(Verdict.GOOD, result.verdict)
        assertTrue(result.score >= 70)
    }

    @Test
    fun `with no fibre figure sugar falls back to its share of the carbs`() {
        val result = rated(review(kcal = 284.0, protein = 5.4, carbs = 40.4, fat = 11.4, sugar = 37.0))

        assertEquals("Poor", result.notes.single { it.label == "Sugar" }.verdict)
        assertEquals("the plain amount, whatever the scoring had to work from", "37 g",
            result.notes.single { it.label == "Sugar" }.amount)
        assertEquals(Verdict.POOR, result.verdict)
    }

    @Test
    fun `a plate we barely have figures for is not rated at all`() {
        val result = review(
            kcal = 500.0,
            protein = 20.0,
            carbs = 60.0,
            fat = 15.0,
            fibre = 2.0,
            sugar = 3.0,
            covered = 100.0,
        )

        assertEquals(MealReview.Unrated(0.2), result)
    }

    @Test
    fun `a partly measured plate is rated and says so`() {
        val result = rated(
            review(kcal = 400.0, protein = 20.0, carbs = 40.0, fat = 10.0, fibre = 6.0, sugar = 2.0, sodium = 300.0, covered = 320.0),
        )

        assertTrue(result.isPartial)
        assertEquals(0.8, result.coverage, 0.001)
    }

    @Test
    fun `a verdict never describes the amount beside it`() {
        // Sugar that arrived with plenty of fibre: oats, banana and milk. The score is right
        // to pass it, and "38 g — Low" would be a claim about the number rather than the meal.
        val result = rated(
            review(kcal = 516.0, protein = 26.0, carbs = 58.0, fat = 21.0, fibre = 6.4, sugar = 38.0, sodium = 113.0),
        )

        val sugar = result.notes.single { it.label == "Sugar" }
        assertEquals("38 g", sugar.amount)
        assertEquals("Good", sugar.verdict)
    }

    @Test
    fun `a trace is shown as a trace rather than rounded away to nothing`() {
        val result = rated(
            review(kcal = 60.0, protein = 1.1, carbs = 8.5, fat = 2.0, fibre = 0.08, sugar = 7.8, sodium = 15.0),
        )

        assertEquals("0.1 g", result.notes.single { it.label == "Fibre" }.amount)
        assertEquals("7.8 g", result.notes.single { it.label == "Sugar" }.amount)
        assertEquals("15 mg", result.notes.single { it.label == "Salt" }.amount)
    }

    @Test
    fun `a plate with no figures at all has nothing to draw`() {
        val nothing = review(kcal = 500.0, protein = 20.0, carbs = 60.0, fat = 15.0, covered = 0.0)
        val partial = review(
            kcal = 500.0,
            protein = 20.0,
            carbs = 60.0,
            fat = 15.0,
            sugar = 4.0,
            covered = 100.0,
        )

        assertFalse("nothing to say and nothing to act on", nothing.isWorthShowing)
        assertTrue("worth saying: swapping a row would finish it", partial.isWorthShowing)
    }

    @Test
    fun `an empty entry is not scored`() {
        assertTrue(review(kcal = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0) is MealReview.Unrated)
    }

    @Test
    fun `protein alone still scores when nothing else was measured enough`() {
        // Sugar, fibre and salt are all absent, so only the protein part can be judged — and
        // the coverage gate is what decides whether that is allowed to stand as a verdict.
        val result = review(kcal = 330.0, protein = 62.0, carbs = 0.0, fat = 7.0, covered = 0.0)

        assertTrue(result is MealReview.Unrated)
    }
}
