package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.PortionMeasureEntity
import com.yash.tracker.data.local.entity.ProductEntity
import com.yash.tracker.data.remote.AppIdentity
import com.yash.tracker.data.remote.GeminiAuth
import com.yash.tracker.data.remote.GeminiClient
import com.yash.tracker.data.remote.GeminiConfig
import com.yash.tracker.data.remote.GeminiService
import com.yash.tracker.data.remote.RecognitionOutcome
import com.yash.tracker.data.remote.dto.GenerateContentRequest
import com.yash.tracker.data.remote.dto.GenerateContentResponse
import com.yash.tracker.domain.diary.MealType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.time.LocalDate

/**
 * The point of the whole local-first path: a meal the tables can already answer never reaches
 * Gemini, and a meal that did reach it only reaches it once.
 */
@RunWith(RobolectricTestRunner::class)
class LocalRecognitionTest {

    private lateinit var db: AppDatabase
    private lateinit var recognition: RecognitionRepository
    private lateinit var service: CountingService

    /** Answers with whatever [reply] holds, and counts how often it was asked. */
    private class CountingService : GeminiService {
        var calls = 0
        var reply: String = REPLY

        override suspend fun generateContent(
            url: String,
            headers: Map<String, String>,
            request: GenerateContentRequest,
        ): Response<GenerateContentResponse> {
            calls++
            return Response.success(
                Json.decodeFromString<GenerateContentResponse>(
                    """
                    {"candidates":[{"content":{"parts":[{"text":${Json.encodeToString(reply)}}]}}]}
                    """.trimIndent(),
                ),
            )
        }

        companion object {
            val REPLY = """
                {"items":[
                  {"name":"Bajra khichdi","quantity":1,"unit":"katori","grams_estimate":200,
                   "kcal":260,"protein_g":9,"carbs_g":44,"fat_g":5,"confidence":0.6,
                   "basis":"estimate"}
                ],"meal_type_guess":"DINNER"}
            """.trimIndent()

            /** The same row, with the rest of the panel the prompt now asks for. */
            val REPLY_WITH_PANEL = """
                {"items":[
                  {"name":"Bajra khichdi","quantity":1,"unit":"katori","grams_estimate":200,
                   "kcal":260,"protein_g":9,"carbs_g":44,"fat_g":5,
                   "fibre_g":6,"sugar_g":3,"sodium_mg":400,
                   "confidence":0.6,"basis":"estimate"}
                ],"meal_type_guess":"DINNER"}
            """.trimIndent()
        }
    }

    private class FakeConfig(var grounding: Boolean = true) : GeminiConfig {
        override suspend fun apiKey() = "test-key"
        override suspend fun model() = "gemini-3.5-flash-lite"
        override suspend fun isGroundingEnabled() = grounding
    }

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // The measures the real seed ships, and nothing else: an empty catalogue keeps each
        // test's assertions about what was found down to the rows that test put there.
        db.foodDao().insertPortions(
            listOf("roti" to 40.0, "katori" to 150.0, "cup" to 240.0, "scoop" to 30.0)
                .map { (label, grams) -> PortionMeasureEntity(foodId = null, label = label, grams = grams) },
        )

        service = CountingService()
        recognition = RecognitionRepository(
            client = GeminiClient(
                service,
                FakeConfig(),
                GeminiAuth(FakeConfig(), object : AppIdentity {
                    override suspend fun token() = "test-token"
                }),
                db.aiCacheDao(),
                Dispatchers.Unconfined,
            ),
            config = FakeConfig(),
            profiles = ProfileRepository(db.profileDao(), db.weightDao(), Dispatchers.Unconfined),
            foodDao = db.foodDao(),
            productDao = db.productDao(),
            corrections = db.correctionDao(),
            logDao = db.logDao(),
            io = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun draftFor(text: String): DraftMeal {
        val outcome = recognition.recognizeText(text)
        assertTrue("expected a reading, got $outcome", outcome is RecognitionOutcome.Success)
        val success = outcome as RecognitionOutcome.Success

        return recognition.toDraft(
            items = success.meal.items,
            mealTypeGuess = success.meal.mealTypeGuess,
            notes = success.meal.notes,
            fromCache = success.fromCache,
        )
    }

    @Test
    fun `a catalogue food is logged without asking the model`() = runTest {
        db.foodDao().insert(
            FoodEntity(
                name = "Roti",
                altNames = null,
                category = "DISH",
                source = "INDB",
                sourceRef = null,
                kcal100g = 300.0,
                protein100g = 10.0,
                carbs100g = 57.5,
                fat100g = 3.75,
                fibre100g = null,
                sugar100g = null,
                sodiumMg100g = null,
                defaultPortionG = 40.0,
                portionLabel = "1 roti",
                createdAt = 0L,
            ),
        )

        val draft = draftFor("2 roti")

        assertEquals(0, service.calls)
        assertEquals(1, draft.items.size)
        assertEquals(80.0, draft.items.single().grams, 0.01)
        assertEquals(240.0, draft.items.single().kcal, 0.01)
        assertTrue(draft.fromCache)
    }

    @Test
    fun `one unknown food in the sentence sends the whole sentence to the model`() = runTest {
        draftFor("2 roti and a katori of bajra khichdi")

        assertEquals(1, service.calls)
    }

    @Test
    fun `a food the model named is answered from the tables the next time`() = runTest {
        val first = draftFor("a katori of bajra khichdi")
        assertEquals(1, service.calls)

        recognition.save(first, LocalDate.of(2026, 9, 17), photoUri = null, userHint = null)

        val learned = db.foodDao().findByExactName("Bajra khichdi")
        assertNotNull("saving should have written the food it named", learned)
        assertEquals(FoodEntity.SOURCE_AI_ESTIMATE, learned!!.source)
        assertEquals(130.0, learned.kcal100g, 0.01)
        assertEquals(200.0, learned.defaultPortionG, 0.01)

        val second = draftFor("a katori of bajra khichdi")

        assertEquals("the second telling should cost nothing", 1, service.calls)
        assertEquals(200.0, second.items.single().grams, 0.01)
        assertEquals(260.0, second.items.single().kcal, 0.01)
    }

    @Test
    fun `the words you typed become words the food answers to`() = runTest {
        // The model renames things: you type "khichdi", it answers "Bajra khichdi". Without
        // this the catalogue learns its vocabulary and your own keeps paying for a call.
        val typed = "a katori of khichdi"
        val draft = draftFor(typed)
        assertEquals(1, service.calls)

        recognition.save(draft, LocalDate.of(2026, 9, 17), photoUri = null, userHint = typed)

        val learned = db.foodDao().findByExactName("khichdi")
        assertNotNull("your word should now find the food", learned)
        assertEquals("Bajra khichdi", learned!!.name)
    }

    @Test
    fun `a sentence whose parts do not line up with the rows teaches nothing`() = runTest {
        // One part typed, one row back is safe to pair. Two parts and one row is a guess, and
        // a guess here attaches a word to the wrong food for good.
        val typed = "a katori of khichdi and 2 roti"
        val draft = draftFor(typed)

        recognition.save(draft, LocalDate.of(2026, 9, 17), photoUri = null, userHint = typed)

        assertEquals(null, db.foodDao().findByExactName("khichdi"))
    }

    @Test
    fun `a saved reading is not thrown away when the cache expires`() = runTest {
        val typed = "a katori of bajra khichdi"
        val draft = draftFor(typed)
        assertEquals(1, service.calls)

        recognition.save(draft, LocalDate.of(2026, 9, 17), photoUri = null, userHint = typed)

        val cached = db.aiCacheDao().let { dao ->
            // Age every unpinned row past the window; the saved one must survive it.
            dao.evictOlderThan(System.currentTimeMillis() + 1)
            dao.count()
        }

        assertEquals("the saved reading should still be there", 1, cached)
    }

    @Test
    fun `the user's own product answers before the model does`() = runTest {
        db.productDao().upsert(
            ProductEntity(
                brand = null,
                name = "Whey isolate",
                kcal100g = 380.0,
                protein100g = 80.0,
                carbs100g = 8.0,
                fat100g = 3.0,
                fibre100g = null,
                sugar100g = null,
                sodiumMg100g = null,
                servingG = 30.0,
                servingLabel = "1 scoop",
                ingredients = null,
                labelPhotoUri = null,
                barcode = null,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )

        val draft = draftFor("2 scoop whey isolate")

        assertEquals(0, service.calls)
        assertEquals(60.0, draft.items.single().grams, 0.01)
        assertTrue("a product row is exact", draft.items.single().isExact)
    }

    @Test
    fun `a locally answered meal still lands on a sensible plate`() = runTest {
        db.foodDao().insert(
            FoodEntity(
                name = "Poha",
                altNames = null,
                category = "DISH",
                source = "INDB",
                sourceRef = null,
                kcal100g = 130.0,
                protein100g = 2.0,
                carbs100g = 27.0,
                fat100g = 2.0,
                fibre100g = null,
                sugar100g = null,
                sodiumMg100g = null,
                defaultPortionG = 150.0,
                portionLabel = null,
                createdAt = 0L,
            ),
        )

        val draft = draftFor("poha")

        assertEquals(0, service.calls)
        assertEquals(150.0, draft.items.single().grams, 0.01)
        assertTrue(draft.mealType in MealType.entries)
    }

    @Test
    fun `a reading that states its panel teaches that to the food it names`() = runTest {
        service.reply = CountingService.REPLY_WITH_PANEL

        val draft = draftFor("a katori of bajra khichdi")
        recognition.save(draft, LocalDate.of(2026, 9, 17), photoUri = null, userHint = null)

        // 200 g was read, so the row keeps half of each figure per 100 g.
        val learned = db.foodDao().findByExactName("Bajra khichdi")!!
        assertEquals(3.0, learned.fibre100g!!, 0.01)
        assertEquals(1.5, learned.sugar100g!!, 0.01)
        assertEquals(200.0, learned.sodiumMg100g!!, 0.01)
    }

    @Test
    fun `a food we remembered without a panel gains one from a later reading`() = runTest {
        // First telling: the model said nothing about sugar, so neither does the row.
        recognition.save(
            draftFor("a katori of bajra khichdi"),
            LocalDate.of(2026, 9, 17),
            photoUri = null,
            userHint = null,
        )
        assertNull(db.foodDao().findByExactName("Bajra khichdi")!!.sugar100g)

        // A later sentence the tables cannot finish goes to the model, which now answers with
        // the panel. The remembered row must take it rather than let its own blank win.
        service.reply = CountingService.REPLY_WITH_PANEL
        val second = draftFor("bajra khichdi and something we have never heard of")
        assertEquals(1.5, second.items.single().sugarG!! / 2, 0.01)

        recognition.save(second, LocalDate.of(2026, 9, 18), photoUri = null, userHint = null)

        assertEquals(
            "the row now answers for itself, and the next telling costs nothing",
            1.5,
            db.foodDao().findByExactName("Bajra khichdi")!!.sugar100g!!,
            0.01,
        )
    }

    @Test
    fun `a bundled reference row is not quietly given the model's guess`() = runTest {
        db.foodDao().insert(
            FoodEntity(
                name = "Bajra khichdi",
                altNames = null,
                category = "DISH",
                source = "INDB",
                sourceRef = "INDB:1",
                kcal100g = 130.0,
                protein100g = 4.5,
                carbs100g = 22.0,
                fat100g = 2.5,
                fibre100g = null,
                sugar100g = null,
                sodiumMg100g = null,
                defaultPortionG = 200.0,
                portionLabel = null,
                createdAt = 0L,
            ),
        )
        service.reply = CountingService.REPLY_WITH_PANEL

        val draft = draftFor("bajra khichdi and something we have never heard of")
        recognition.save(draft, LocalDate.of(2026, 9, 17), photoUri = null, userHint = null)

        val reference = db.foodDao().findByExactName("Bajra khichdi")!!
        assertNull("INDB says it never measured this, and still says so", reference.sugar100g)
        assertNotNull("the meal still carries what the reading knew", draft.items.single().sugarG)
    }
}
