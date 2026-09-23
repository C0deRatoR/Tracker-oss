package com.yash.tracker.data.repository

import com.yash.tracker.data.local.dao.CorrectionDao
import com.yash.tracker.data.local.dao.FoodDao
import com.yash.tracker.data.local.dao.LogDao
import com.yash.tracker.data.local.dao.ProductDao
import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.LogEntryEntity
import com.yash.tracker.data.local.entity.LogItemEntity
import com.yash.tracker.data.local.entity.PortionMeasureEntity
import com.yash.tracker.data.local.entity.ProductEntity
import com.yash.tracker.data.remote.GeminiClient
import com.yash.tracker.data.remote.GeminiConfig
import com.yash.tracker.data.remote.RecognitionOutcome
import com.yash.tracker.data.remote.dto.RecognizedItemDto
import com.yash.tracker.data.remote.dto.RecognizedMealDto
import com.yash.tracker.data.remote.prompts.LearnedCorrection
import com.yash.tracker.data.remote.prompts.MealRecognition
import com.yash.tracker.domain.diary.CorrectionLessons
import com.yash.tracker.domain.diary.DiaryDate
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.diary.MealWindows
import com.yash.tracker.domain.nutrition.MealTextParser
import com.yash.tracker.domain.nutrition.Micros
import com.yash.tracker.domain.nutrition.NutritionPer100g
import com.yash.tracker.domain.nutrition.ParsedPortion
import com.yash.tracker.domain.nutrition.PortionResolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One row on the confirm screen. Everything here is editable before anything is written, so
 * the row carries its own values rather than pointing back at the model's reply.
 */
data class DraftItem(
    val id: Long,
    val name: String,
    /** What the model originally called this, so a rename can be learned from. */
    val originalName: String,
    /** And what it originally weighed, so a portion correction can be told from a guess. */
    val originalGrams: Double,
    val quantity: Double,
    val unit: String,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fibreG: Double? = null,
    val sugarG: Double? = null,
    val sodiumMg: Double? = null,
    val confidence: Double?,
    val basis: String?,
    val sourceNote: String?,
    val foodId: Long? = null,
    /** Set when the row was swapped for one of the user's products, which makes it exact. */
    val productId: Long? = null,
    val edited: Boolean = false,
) {
    /** Low confidence rows are expanded first so the user never hunts for what needs fixing. */
    val needsAttention: Boolean get() = !isExact && !edited && (confidence ?: 1.0) < 0.5

    /** A product's numbers came off a packet, so they are not an estimate and never flagged. */
    val isExact: Boolean get() = productId != null

    val micros: Micros get() = Micros(fibreG = fibreG, sugarG = sugarG, sodiumMg = sodiumMg)
}

/** Replaces the row's fibre, sugar and sodium, leaving everything else alone. */
fun DraftItem.withMicros(micros: Micros): DraftItem =
    copy(fibreG = micros.fibreG, sugarG = micros.sugarG, sodiumMg = micros.sodiumMg)

data class DraftMeal(
    val items: List<DraftItem>,
    val mealType: MealType,
    val notes: String?,
    val fromCache: Boolean,
) {
    val totalKcal: Double get() = items.sumOf { it.kcal }
    val totalProtein: Double get() = items.sumOf { it.proteinG }
    val totalCarbs: Double get() = items.sumOf { it.carbsG }
    val totalFat: Double get() = items.sumOf { it.fatG }
    val totalMicros: Micros get() = items.fold(Micros.UNKNOWN) { sum, item -> sum + item.micros }

    /** The calories covered by rows that stated anything beyond the four macros. */
    val microKcal: Double get() = items.filter { it.micros.isKnown }.sumOf { it.kcal }
}

/**
 * Replaces a recognised row's estimate with a product's own label figures.
 *
 * The weight does not change: the amount on the plate is what the user already said it was, and
 * only whose numbers describe it is different. A row with no weight at all falls back to the
 * pack's serving, which is the only other weight anyone has stated.
 *
 * A plain function rather than a method on the ViewModel so the arithmetic can be tested without
 * Hilt, a database or a network client in the way.
 */
fun DraftItem.swappedFor(product: ProductEntity, defaultGrams: Double = 100.0): DraftItem {
    val weight = grams.takeIf { it > 0 } ?: product.servingG?.takeIf { it > 0 } ?: defaultGrams
    val macros = PortionResolver.macrosFor(product, weight)
    val micros = PortionResolver.microsFor(product, weight)

    return copy(
        name = product.displayName(),
        productId = product.id,
        // The catalogue match no longer applies: this row is the user's own product now.
        foodId = null,
        grams = weight,
        kcal = macros.kcal,
        proteinG = macros.proteinG,
        carbsG = macros.carbsG,
        fatG = macros.fatG,
        fibreG = micros.fibreG,
        sugarG = micros.sugarG,
        sodiumMg = micros.sodiumMg,
        // Nothing here is a guess any more, so it carries no confidence and no basis.
        confidence = null,
        basis = null,
        sourceNote = "your product",
    )
}

/**
 * Replaces a recognised row's estimate with the catalogue's own reference numbers, at the
 * weight already on the row. Same idea as [swappedFor] above, but for the bundled table
 * rather than one of the user's own products — this is how a misidentified item (the model
 * said "paneer", it was actually "tofu") gets both its name and its macros corrected together.
 */
fun DraftItem.swappedFor(food: FoodEntity, defaultGrams: Double = 100.0): DraftItem {
    val weight = grams.takeIf { it > 0 } ?: food.defaultPortionG.takeIf { it > 0 } ?: defaultGrams
    val macros = PortionResolver.macrosFor(food, weight)
    val micros = PortionResolver.microsFor(food, weight)

    return copy(
        name = food.name,
        foodId = food.id,
        // A catalogue match isn't the user's own product, so an earlier product swap no
        // longer applies.
        productId = null,
        grams = weight,
        kcal = macros.kcal,
        proteinG = macros.proteinG,
        carbsG = macros.carbsG,
        fatG = macros.fatG,
        fibreG = micros.fibreG,
        sugarG = micros.sugarG,
        sodiumMg = micros.sodiumMg,
        confidence = null,
        basis = null,
        sourceNote = "from the food catalogue",
    )
}

/**
 * The same food, a different amount of it.
 *
 * Every figure is linear in weight, so one ratio moves all of them. Whose numbers they are does
 * not change — a product row stays exact, an estimate stays an estimate, and a row that never
 * had a sugar figure still does not have one.
 */
fun DraftItem.rescaledTo(newGrams: Double): DraftItem {
    val ratio = if (grams > 0) newGrams / grams else 0.0

    return withMicros(micros * ratio).copy(
        grams = newGrams,
        kcal = kcal * ratio,
        proteinG = proteinG * ratio,
        carbsG = carbsG * ratio,
        fatG = fatG * ratio,
    )
}

/**
 * What one of this row's units weighs, taken from the row itself: the model said both "2 roti"
 * and "80 g", and the ratio between them is its own estimate of the portion. Falls back to
 * treating the row as ungrouped when there is no quantity to divide by.
 */
val DraftItem.gramsPerUnit: Double
    get() = if (quantity > 0 && grams > 0) grams / quantity else 0.0

/**
 * Applies a matched food's numbers to a row the user renamed by hand, keeping the name they
 * typed.
 *
 * [swappedFor] renames the row to whatever was picked, because there the user chose the food
 * itself. A rename is the other way round: they have already said what to call it, and it is
 * only the macros that are still describing the wrong thing. So the typed name stays and the
 * note records what the numbers actually came from.
 */
fun DraftItem.rematchedTo(product: ProductEntity): DraftItem = swappedFor(product).copy(
    name = this.name,
    sourceNote = "your product — ${product.displayName()}",
)

fun DraftItem.rematchedTo(food: FoodEntity): DraftItem = swappedFor(food).copy(
    name = this.name,
    sourceNote = "from the food catalogue — ${food.name}",
)

private val RecognizedItemDto.micros: Micros
    get() = Micros(fibreG = fibreG, sugarG = sugarG, sodiumMg = sodiumMg)

@Singleton
class RecognitionRepository @Inject constructor(
    private val client: GeminiClient,
    private val config: GeminiConfig,
    private val profiles: ProfileRepository,
    private val foodDao: FoodDao,
    private val productDao: ProductDao,
    private val corrections: CorrectionDao,
    private val logDao: LogDao,
    private val io: CoroutineDispatcher,
) {
    /**
     * Reads a typed meal, asking the model only for what the tables cannot already answer.
     *
     * Every food a confirmed meal named is written into the catalogue (see [rememberFoods]),
     * so the second time someone types what they usually eat, nothing leaves the device.
     */
    suspend fun recognizeText(text: String): RecognitionOutcome = withContext(io) {
        resolveLocally(text)?.let {
            return@withContext RecognitionOutcome.Success(it, fromCache = true)
        }
        client.recognizeText(text, systemPrompt(), grounded = wantsGrounding(text))
    }

    /**
     * A photograph always goes to the model: nothing can be looked up before something has
     * said what is in the frame. What the reading teaches is kept, so the same meal typed
     * tomorrow is answered from [resolveLocally] instead.
     */
    suspend fun recognizePhoto(jpegBase64: String, hint: String?): RecognitionOutcome =
        withContext(io) {
            client.recognizePhoto(jpegBase64, hint, systemPrompt(), grounded = wantsGrounding(hint))
        }

    /**
     * PRD §5.4: an explicit "look it up" grounds the call, regardless of confidence — unless
     * the user has turned grounding off in Settings, which is the only way to say that a
     * search-backed answer is not worth what it costs.
     */
    private suspend fun wantsGrounding(text: String?): Boolean {
        val lower = text?.lowercase() ?: return false
        if (!config.isGroundingEnabled()) return false
        return "look it up" in lower || "verify" in lower
    }

    private suspend fun systemPrompt(): String {
        val profile = profiles.observeProfileOnce()
        return MealRecognition.systemPrompt(
            dietaryNotes = profile?.notes,
            eatingStyle = profile?.eatingStyle,
            // Past corrections ride along, so recognition converges on how this user
            // actually eats rather than repeating the same mistake (PRD §5.8).
            topCorrections = corrections.top(MAX_PROMPT_CORRECTIONS).map {
                LearnedCorrection(it.aiName, it.correctedName, it.typicalGrams)
            },
        )
    }

    suspend fun toDraft(
        items: List<RecognizedItemDto>,
        mealTypeGuess: String?,
        notes: String?,
        fromCache: Boolean,
    ): DraftMeal = withContext(io) {
        DraftMeal(
            items = items.mapIndexed { index, dto ->
                DraftItem(
                    id = index.toLong(),
                    name = dto.name,
                    originalName = dto.name,
                    originalGrams = dto.gramsEstimate,
                    quantity = dto.quantity,
                    unit = dto.unit,
                    grams = dto.gramsEstimate,
                    kcal = dto.kcal,
                    proteinG = dto.proteinG,
                    carbsG = dto.carbsG,
                    fatG = dto.fatG,
                    confidence = dto.confidence,
                    basis = dto.basis,
                    sourceNote = dto.sourceNote,
                    // A row answered from the tables already knows which row answered it;
                    // only the model's own rows have to be matched back by name.
                    foodId = dto.foodId ?: matchLocally(dto.name),
                    productId = dto.productId,
                ).withMicros(microsFor(dto))
            },
            mealType = runCatching { MealType.valueOf(mealTypeGuess.orEmpty()) }
                .getOrElse { MealType.SNACK },
            notes = notes,
            fromCache = fromCache,
        )
    }

    /**
     * Where a row's fibre, sugar and sodium come from: the tables first, the model second.
     *
     * A row the app resolved itself already carries the catalogue's or the packet's own
     * figures. A row the model read gets them from the catalogue when its name is exactly a
     * row we hold — exactly, not the fuzzy match behind [matchLocally], because that one only
     * has to be a good enough candidate to offer as a swap, whereas this one is silently
     * believed.
     *
     * Field by field, not all-or-nothing. Half this user's catalogue is rows remembered from
     * earlier readings, which state nothing at all; letting a matched row's blanks win would
     * mean the model could answer the question and be ignored, and the blank would never be
     * filled in.
     */
    private suspend fun microsFor(dto: RecognizedItemDto): Micros {
        if (dto.foodId != null || dto.productId != null) return dto.micros
        val matched = foodMatching(dto.name) ?: return dto.micros
        return PortionResolver.microsFor(matched, dto.gramsEstimate).orElse(dto.micros)
    }

    /** A local match lets the user swap an estimate for a catalogue row on the confirm screen. */
    private suspend fun matchLocally(name: String): Long? =
        foodDao.searchByName(name.trim(), limit = 1).firstOrNull()?.id

    /**
     * Answers a typed meal from the tables the app already holds, or not at all.
     *
     * Returns null the moment any part of the sentence cannot be resolved exactly. Half a meal
     * answered here and half by the model would be one entry written from two readings, and a
     * model call is by far the cheaper of the two mistakes available.
     */
    private suspend fun resolveLocally(text: String): RecognizedMealDto? {
        val measures = foodDao.genericPortions()
        val parts = MealTextParser.parse(text, unitVocabulary(measures))
        if (parts.isEmpty()) return null

        val items = parts.map { resolvePart(it, measures) ?: return null }

        return RecognizedMealDto(
            items = items,
            // The model guesses this from the plate; with no plate to look at, the clock is
            // the only thing that knows, and it is what a hand-logged entry defaults to too.
            mealTypeGuess = MealWindows.defaultFor(LocalTime.now().hour).name,
        )
    }

    /** Every measure the tables know, which is the only vocabulary the parser is allowed. */
    private fun unitVocabulary(measures: List<PortionMeasureEntity>): Set<String> =
        measures.mapTo(mutableSetOf()) { it.label.lowercase() }
            .apply { add(MealTextParser.GRAMS); add(MealTextParser.MILLILITRES) }

    /** One named food, resolved against the user's own products first and the catalogue second. */
    private suspend fun resolvePart(
        part: ParsedPortion,
        measures: List<PortionMeasureEntity>,
    ): RecognizedItemDto? {
        productMatching(part.name)?.let { product ->
            val grams = gramsFor(product, part, measures) ?: return null
            return itemFor(
                part = part,
                grams = grams,
                nutrition = product,
                name = product.displayName(),
                confidence = 1.0,
                basis = "branded",
                sourceNote = "your product",
                productId = product.id,
            )
        }

        val food = foodMatching(part.name) ?: return null
        val grams = gramsFor(food, part) ?: return null
        val remembered = food.source == FoodEntity.SOURCE_AI_ESTIMATE

        return itemFor(
            part = part,
            grams = grams,
            nutrition = food,
            name = food.name,
            // A remembered row began life as the model's estimate, and saying so keeps it
            // open to correction rather than presenting a guess as a reference figure.
            confidence = if (remembered) 0.7 else 1.0,
            basis = if (remembered) "estimate" else "reference",
            sourceNote = if (remembered) {
                "remembered from an earlier reading"
            } else {
                "from the food catalogue"
            },
            foodId = food.id,
        )
    }

    private fun itemFor(
        part: ParsedPortion,
        grams: Double,
        nutrition: NutritionPer100g,
        name: String,
        confidence: Double,
        basis: String,
        sourceNote: String,
        foodId: Long? = null,
        productId: Long? = null,
    ): RecognizedItemDto {
        val macros = PortionResolver.macrosFor(nutrition, grams)
        val micros = PortionResolver.microsFor(nutrition, grams)

        return RecognizedItemDto(
            name = name,
            // With no measure named, the weight is the count: "dal" is 150 g, not 1 of a unit
            // nothing on the row can explain.
            quantity = if (part.unit == null) grams else part.quantity,
            unit = part.unit ?: MealTextParser.GRAMS,
            gramsEstimate = grams,
            kcal = macros.kcal,
            proteinG = macros.proteinG,
            carbsG = macros.carbsG,
            fatG = macros.fatG,
            fibreG = micros.fibreG,
            sugarG = micros.sugarG,
            sodiumMg = micros.sodiumMg,
            confidence = confidence,
            basis = basis,
            sourceNote = sourceNote,
            foodId = foodId,
            productId = productId,
        )
    }

    /** Exact on the name or one of its transliterations; a near-miss belongs to the model. */
    private suspend fun foodMatching(name: String): FoodEntity? =
        foodDao.findByExactName(name) ?: singular(name)?.let { foodDao.findByExactName(it) }

    private suspend fun productMatching(name: String): ProductEntity? {
        val candidates = productDao.search(name.trim(), limit = 10)
        val wanted = listOfNotNull(name.trim(), singular(name))

        return candidates.firstOrNull { product ->
            wanted.any { it.equals(product.name, true) || it.equals(product.displayName(), true) }
        }
    }

    private fun singular(name: String): String? =
        name.trim().removeSuffix("s").takeIf { it.length > 2 && it != name.trim() }

    /** What the named amount of a catalogue food weighs, or null when nothing here knows. */
    private suspend fun gramsFor(food: FoodEntity, part: ParsedPortion): Double? {
        val unit = part.unit?.lowercase() ?: return part.quantity * food.defaultPortionG
        if (unit == MealTextParser.GRAMS || unit == MealTextParser.MILLILITRES) return part.quantity

        // Food-specific measures win over generic ones, and a food's own serving beats both.
        val measures = foodDao.portionsFor(food.id)
        val gramsPerUnit = measures.firstOrNull { it.foodId != null && it.label.equals(unit, true) }?.grams
            ?: food.defaultPortionG.takeIf { servingLabel(food.portionLabel) == unit }
            ?: measures.firstOrNull { it.foodId == null && it.label.equals(unit, true) }?.grams
            ?: return null

        return part.quantity * gramsPerUnit
    }

    private fun gramsFor(
        product: ProductEntity,
        part: ParsedPortion,
        measures: List<PortionMeasureEntity>,
    ): Double? {
        val serving = product.servingG?.takeIf { it > 0 }
        val unit = part.unit?.lowercase() ?: return part.quantity * (serving ?: DEFAULT_GRAMS)
        if (unit == MealTextParser.GRAMS || unit == MealTextParser.MILLILITRES) return part.quantity

        if (servingLabel(product.servingLabel) == unit && serving != null) {
            return part.quantity * serving
        }

        val generic = measures.firstOrNull { it.label.equals(unit, true) }?.grams ?: return null
        return part.quantity * generic
    }

    /** Portion labels are written for a person to read: "1 tea cup", not "tea cup". */
    private fun servingLabel(raw: String?): String? =
        raw?.trim()?.removePrefix("1")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    /**
     * Writes anything a confirmed meal named but the tables did not know into the catalogue,
     * and points the row at it.
     *
     * Only on save. A reading the user threw away taught us nothing, and PRD §7.3 says a
     * discarded recognition leaves no catalogue row behind — so what lands here is what they
     * confirmed, corrections included, which is also the better set of numbers.
     */
    private suspend fun rememberFoods(draft: DraftMeal, typed: String?, now: Long): DraftMeal {
        // Before anything is added: whatever this reading knew that a row we already remember
        // did not. Paying for the same answer twice is the thing this whole file exists to
        // avoid, and a remembered row with no sugar figure would otherwise never gain one.
        draft.items.forEach { topUpLearnedFood(it) }

        val items = draft.items.map { item ->
            if (item.foodId != null || item.productId != null) return@map item
            if (item.grams <= 0 || item.kcal <= 0) return@map item

            val name = item.name.trim()
            if (name.isEmpty()) return@map item

            val id = foodMatching(name)?.id
                ?: foodDao.insert(learnedFood(name, item, now)).takeIf { it > 0 }

            item.copy(foodId = id)
        }

        val remembered = draft.copy(items = items)
        typed?.let { teachPhrases(it, remembered) }

        return remembered
    }

    /**
     * Teaches each food the words this user reached for.
     *
     * The model renames things — you type "panner" and it answers "Heritage Nourish High
     * Protein Paneer" — so without this the catalogue learns the model's vocabulary and your
     * own phrasing keeps paying for a call. Written as an alternate name, which is what
     * [FoodDao.findByExactName] already searches.
     *
     * Only when the sentence you typed has exactly as many parts as the reading has rows, so
     * the pairing is positional and not a guess. Anything else is left alone.
     */
    private suspend fun teachPhrases(typed: String, draft: DraftMeal) {
        val parts = MealTextParser.parse(typed, unitVocabulary(foodDao.genericPortions()))
        if (parts.isEmpty() || parts.size != draft.items.size) return

        parts.zip(draft.items).forEach { (part, item) ->
            item.foodId?.let { learnAlias(it, part.name) }
        }
    }

    /** Adds one word to what a food answers to, if it is safe to and not already there. */
    private suspend fun learnAlias(foodId: Long, phrase: String) {
        val alias = phrase.trim()
        if (alias.length < MIN_ALIAS_LENGTH || alias.none(Char::isLetter)) return

        val food = foodDao.getById(foodId) ?: return
        if (alias.equals(food.name, ignoreCase = true)) return

        val existing = food.altNames.orEmpty()
        if (" $existing ".contains(" $alias ", ignoreCase = true)) return

        // A word that already names some other food would make both of them ambiguous, and
        // the wrong one would win on nothing better than how often it had been logged.
        val taken = foodDao.findByExactName(alias)
        if (taken != null && taken.id != foodId) return

        foodDao.setAltNames(foodId, listOf(existing, alias).filter(String::isNotBlank).joinToString(" "))
    }

    /**
     * Teaches a food we remembered earlier the figures a later reading supplied.
     *
     * Only rows we wrote ourselves. A bundled INDB or USDA row whose source never measured
     * sugar keeps its blank: the row says where its numbers come from, and quietly mixing a
     * model's guess into one labelled USDA would make that claim false. An AI_ESTIMATE row is
     * already an estimate, so filling its gaps from the same origin changes nothing about what
     * it is.
     *
     * Matched on the name exactly, the way the rest of this file establishes identity — the
     * fuzzy candidate behind [matchLocally] is good enough to offer as a swap and nowhere near
     * good enough to write into the catalogue on.
     */
    private suspend fun topUpLearnedFood(item: DraftItem) {
        if (item.productId != null || !item.micros.isKnown || item.grams <= 0) return
        val food = foodMatching(item.name.trim()) ?: return
        if (food.source != FoodEntity.SOURCE_AI_ESTIMATE) return

        val ratio = 100.0 / item.grams
        foodDao.fillMissingMicros(
            id = food.id,
            fibre100g = item.fibreG?.times(ratio),
            sugar100g = item.sugarG?.times(ratio),
            sodiumMg100g = item.sodiumMg?.times(ratio),
            source = FoodEntity.SOURCE_AI_ESTIMATE,
        )
    }

    /** One confirmed row as a catalogue entry: macros per 100 g, and one serving's weight. */
    private fun learnedFood(name: String, item: DraftItem, now: Long): FoodEntity {
        val ratio = 100.0 / item.grams
        val counted = item.unit.lowercase() !in BASE_UNITS && item.quantity > 0

        return FoodEntity(
            name = name,
            altNames = null,
            category = null,
            source = FoodEntity.SOURCE_AI_ESTIMATE,
            sourceRef = null,
            kcal100g = item.kcal * ratio,
            protein100g = item.proteinG * ratio,
            carbs100g = item.carbsG * ratio,
            fat100g = item.fatG * ratio,
            // Whatever the row stated scales up with the macros. Usually nothing, because
            // the rows that land here are the ones no table could answer.
            fibre100g = item.fibreG?.times(ratio),
            sugar100g = item.sugarG?.times(ratio),
            sodiumMg100g = item.sodiumMg?.times(ratio),
            // What one of them weighs when they were counted, and the whole helping when they
            // were weighed — either way, what a bare mention of this food should mean next time.
            defaultPortionG = if (counted) item.grams / item.quantity else item.grams,
            portionLabel = if (counted) "1 ${item.unit.lowercase()}" else null,
            isVerified = false,
            createdAt = now,
        )
    }

    /**
     * Writes the confirmed meal. Called only from an explicit save — a discarded recognition
     * must leave no entry, no photo and no catalogue row behind (PRD §7.3).
     */
    suspend fun save(
        draft: DraftMeal,
        date: LocalDate,
        photoUri: String?,
        userHint: String?,
    ): Long = withContext(io) {
        val now = System.currentTimeMillis()
        // Only a typed meal teaches words. A photo's hint describes a plate rather than
        // naming one food, and pairing it row by row would attach the wrong word.
        val typed = userHint?.trim()?.takeIf { photoUri == null && it.isNotEmpty() }
        val remembered = rememberFoods(draft, typed, now)
        val provenance = remembered.items
            .mapNotNull { it.sourceNote?.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString("; ")
            .takeIf { it.isNotBlank() }

        val entryId = logDao.insertEntryWithItems(
            entry = LogEntryEntity(
                date = DiaryDate.format(date),
                loggedAt = now,
                mealType = remembered.mealType.name,
                source = if (photoUri != null) "PHOTO" else "TEXT",
                photoUri = photoUri,
                userHint = userHint,
                note = remembered.notes,
                groundingSource = provenance,
                kcal = remembered.totalKcal,
                proteinG = remembered.totalProtein,
                carbsG = remembered.totalCarbs,
                fatG = remembered.totalFat,
            ),
            items = remembered.rows(),
        )

        remembered.items
            .mapNotNull { it.foodId }
            .distinct()
            .forEach { foodDao.incrementTimesLogged(it) }

        learnFrom(remembered, now)

        // The reading behind a saved meal stops ageing out: the same sentence typed again
        // next month should not cost a second call.
        typed?.let { client.keepTextReading(it) }

        entryId
    }

    /** The draft's rows as diary rows. Shared so a saved entry and an edited one match. */
    private fun DraftMeal.rows(): List<LogItemEntity> = items.map { item ->
        LogItemEntity(
            entryId = 0,
            foodId = item.foodId,
            productId = item.productId,
            name = item.name,
            quantity = item.quantity,
            unit = item.unit.uppercase(),
            grams = item.grams,
            kcal = item.kcal,
            proteinG = item.proteinG,
            carbsG = item.carbsG,
            fatG = item.fatG,
            fibreG = item.fibreG,
            sugarG = item.sugarG,
            sodiumMg = item.sodiumMg,
            aiConfidence = item.confidence,
            wasEdited = item.edited,
        )
    }

    /**
     * Reopens a saved entry in the editor that wrote it, so correcting yesterday's lunch is the
     * same screen as confirming today's — rather than a second editor to build and keep in step.
     */
    suspend fun draftFor(entryId: Long): DraftMeal? = withContext(io) {
        val saved = logDao.getEntryWithItems(entryId) ?: return@withContext null

        DraftMeal(
            items = saved.items.mapIndexed { index, row ->
                DraftItem(
                    // Row ids are positional, as they are for a fresh reading: the editor only
                    // needs them to tell one row from another while it is open.
                    id = index.toLong(),
                    name = row.name,
                    // A saved row is its own baseline. What the model first guessed was already
                    // corrected once, and re-teaching that here would double-count the lesson.
                    originalName = row.name,
                    originalGrams = row.grams,
                    quantity = row.quantity,
                    unit = row.unit.lowercase(),
                    grams = row.grams,
                    kcal = row.kcal,
                    proteinG = row.proteinG,
                    fibreG = row.fibreG,
                    sugarG = row.sugarG,
                    sodiumMg = row.sodiumMg,
                    carbsG = row.carbsG,
                    fatG = row.fatG,
                    confidence = row.aiConfidence,
                    basis = null,
                    sourceNote = null,
                    foodId = row.foodId,
                    productId = row.productId,
                    edited = row.wasEdited,
                )
            },
            mealType = runCatching { MealType.valueOf(saved.entry.mealType) }
                .getOrElse { MealType.SNACK },
            notes = saved.entry.note,
            fromCache = false,
        )
    }

    /**
     * Rewrites a saved entry in place.
     *
     * The day it belongs to, the time it was logged and its photo are the record of *when* you
     * ate, and correcting *what* you ate does not move them — so an edit keeps its place in the
     * diary rather than jumping to the bottom of today.
     */
    suspend fun update(entryId: Long, draft: DraftMeal) = withContext(io) {
        val existing = logDao.getEntryWithItems(entryId) ?: return@withContext
        val now = System.currentTimeMillis()
        // An edit has no typed sentence behind it, so there are no words to learn from it.
        val remembered = rememberFoods(draft, typed = null, now)
        val provenance = remembered.items
            .mapNotNull { it.sourceNote?.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString("; ")
            .takeIf { it.isNotBlank() }

        logDao.replaceEntryItems(
            entry = existing.entry.copy(
                mealType = remembered.mealType.name,
                note = remembered.notes,
                groundingSource = provenance ?: existing.entry.groundingSource,
                kcal = remembered.totalKcal,
                proteinG = remembered.totalProtein,
                carbsG = remembered.totalCarbs,
                fatG = remembered.totalFat,
            ),
            items = remembered.rows(),
        )

        remembered.items
            .mapNotNull { it.foodId }
            .distinct()
            .forEach { foodDao.incrementTimesLogged(it) }

        learnFrom(remembered, now)
    }

    /** The rule for what an edited row teaches lives in [CorrectionLessons]. */
    private suspend fun learnFrom(draft: DraftMeal, now: Long) {
        draft.items
            .mapNotNull {
                CorrectionLessons.from(
                    aiName = it.originalName,
                    finalName = it.name,
                    aiGrams = it.originalGrams,
                    finalGrams = it.grams,
                )
            }
            .forEach { corrections.record(it.aiName, it.correctedName, it.grams, now) }
    }

    suspend fun attachPhoto(entryId: Long, path: String) = withContext(io) {
        logDao.attachPhoto(entryId, path)
    }

    fun observeCorrections() = corrections.observeAll()

    suspend fun forgetCorrection(id: Long) = withContext(io) { corrections.delete(id) }

    suspend fun dayStartHour(): Int = profiles.dayStartHour()

    private companion object {
        const val MAX_PROMPT_CORRECTIONS = 12

        /** What a product with no serving weight is assumed to be served in. */
        const val DEFAULT_GRAMS = 100.0

        /** Below this a "word" is an abbreviation or a stray letter, not a name for a food. */
        const val MIN_ALIAS_LENGTH = 3

        /** The two units that are a weight rather than a count of something. */
        val BASE_UNITS = setOf(MealTextParser.GRAMS, MealTextParser.MILLILITRES)
    }
}
