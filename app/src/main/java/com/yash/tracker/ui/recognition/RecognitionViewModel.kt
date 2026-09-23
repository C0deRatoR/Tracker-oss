package com.yash.tracker.ui.recognition

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.photo.PhotoStore
import com.yash.tracker.data.photo.ProcessedPhoto
import com.yash.tracker.data.remote.FailureKind
import com.yash.tracker.data.remote.RecognitionOutcome
import com.yash.tracker.data.local.entity.ProductEntity
import com.yash.tracker.data.repository.DraftItem
import com.yash.tracker.data.repository.DraftMeal
import com.yash.tracker.data.repository.FoodRepository
import com.yash.tracker.data.repository.LogRepository
import com.yash.tracker.data.repository.ProductRepository
import com.yash.tracker.data.repository.gramsPerUnit
import com.yash.tracker.data.repository.rematchedTo
import com.yash.tracker.data.repository.rescaledTo
import com.yash.tracker.data.repository.swappedFor
import com.yash.tracker.data.repository.RecognitionRepository
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.nutrition.PortionChoice
import com.yash.tracker.ui.foods.SearchHit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface RecognitionStep {
    data object Input : RecognitionStep
    data object Working : RecognitionStep
    /**
     * Saying what the photo actually was, after seeing what was made of it. [returnTo] is the
     * reading being corrected, so backing out lands on it rather than at the beginning.
     */
    data class Describe(val photo: ProcessedPhoto, val returnTo: DraftMeal?) : RecognitionStep
    data class Confirm(val draft: DraftMeal) : RecognitionStep
    data class Problem(val kind: FailureKind, val message: String) : RecognitionStep
    data object NothingFound : RecognitionStep
    data class Saved(val kcal: Int, val edited: Boolean) : RecognitionStep
}

/** Choosing which food — the user's own product, or one from the catalogue — a recognised row really was. */
data class ProductPicker(
    val itemId: Long,
    val query: String = "",
    val results: List<SearchHit> = emptyList(),
    /** True while the typed description is being read by the model. */
    val describing: Boolean = false,
    /** Why the description came back with nothing, if it did. */
    val describeError: String? = null,
)

data class RecognitionUiState(
    val step: RecognitionStep = RecognitionStep.Input,
    val text: String = "",
    val saving: Boolean = false,
    val photo: ProcessedPhoto? = null,
    val productPicker: ProductPicker? = null,
    /** Measures a row can be counted in, loaded once from the seeded portion table. */
    val units: List<PortionChoice> = emptyList(),
    /** The row waiting on a product the user is adding right now, if any. */
    val awaitingProductFor: Long? = null,
    /** Set when this screen is correcting an entry already in the diary rather than adding one. */
    val editingEntryId: Long? = null,
    /** The day a new entry is saved to. Null means today (PRD default); set when opened from a
     *  past day on the dashboard, so a missed meal is logged onto the day it was actually eaten. */
    val logDate: LocalDate? = null,
)

@HiltViewModel
class RecognitionViewModel @Inject constructor(
    private val recognition: RecognitionRepository,
    private val photos: PhotoStore,
    private val log: LogRepository,
    private val products: ProductRepository,
    private val foods: FoodRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecognitionUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val measures = foods.genericPortions()
                .associate { it.label.lowercase() to it.grams }
            _state.update { state ->
                state.copy(
                    units = COUNTABLE_UNITS.mapNotNull { label ->
                        val grams = BASE_UNITS[label]
                            ?: measures[label]
                            ?: return@mapNotNull null
                        PortionChoice(label = label, unit = label.uppercase(), gramsPerUnit = grams)
                    },
                )
            }
        }
    }

    /** Cancelled on every keystroke, so only the name the user settled on is looked up. */
    private var renameLookup: Job? = null

    fun setText(value: String) = _state.update { it.copy(text = value) }

    /** Called once when the screen is reached with a past day picked on the dashboard. */
    fun setLogDate(date: LocalDate) {
        if (_state.value.logDate == date) return
        _state.update { it.copy(logDate = date) }
    }

    /**
     * Opens a saved diary entry for correction. Called once when the screen is reached with an
     * entry id; re-entering with the same id must not discard edits already in progress.
     */
    fun editEntry(entryId: Long) {
        if (_state.value.editingEntryId == entryId) return

        viewModelScope.launch {
            _state.update { it.copy(editingEntryId = entryId, step = RecognitionStep.Working) }
            val draft = recognition.draftFor(entryId)

            _state.update {
                it.copy(
                    step = draft
                        ?.let(RecognitionStep::Confirm)
                        ?: RecognitionStep.Problem(
                            FailureKind.MALFORMED,
                            "That entry is no longer in the diary.",
                        ),
                )
            }
        }
    }

    fun newCaptureTarget() = photos.newCaptureTarget()

    fun recognize() {
        val text = _state.value.text.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(step = RecognitionStep.Working) }
            _state.update { it.copy(step = outcomeToStep(recognition.recognizeText(text))) }
        }
    }

    /**
     * Reads the photo straight away.
     *
     * Asking what is on the plate before anything has been read makes every meal pay for the
     * ones that come back wrong. Most readings are right, so the fast path stays one tap and
     * the description is offered afterwards, against a reading the user can actually judge.
     */
    fun recognizePhoto(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(step = RecognitionStep.Working) }

            val processed = photos.process(uri)
            if (processed == null) {
                _state.update {
                    it.copy(
                        step = RecognitionStep.Problem(
                            FailureKind.MALFORMED,
                            "That image couldn't be read. Try another photo.",
                        ),
                    )
                }
                return@launch
            }

            val hint = _state.value.text.trim().takeIf { it.isNotBlank() }
            val outcome = recognition.recognizePhoto(processed.base64, hint)
            _state.update { it.copy(photo = processed, step = outcomeToStep(outcome)) }
            photos.clearCaptureCache()
        }
    }

    /** Reached from the reading itself: the plate was misread, and the user will say what it was. */
    fun describePhoto() {
        val photo = _state.value.photo ?: return
        val reading = (_state.value.step as? RecognitionStep.Confirm)?.draft

        _state.update { it.copy(step = RecognitionStep.Describe(photo, returnTo = reading)) }
    }

    /** Reads the same photo again, with what the user said about it riding along (PRD §5.4). */
    fun identifyPhoto() {
        val photo = (_state.value.step as? RecognitionStep.Describe)?.photo ?: return

        viewModelScope.launch {
            _state.update { it.copy(step = RecognitionStep.Working) }
            val hint = _state.value.text.trim().takeIf { it.isNotBlank() }
            val outcome = recognition.recognizePhoto(photo.base64, hint)
            _state.update { it.copy(step = outcomeToStep(outcome)) }
            photos.clearCaptureCache()
        }
    }

    /** Backing out of describing keeps the reading that was already on screen. */
    fun cancelDescribe() {
        val step = _state.value.step as? RecognitionStep.Describe ?: return

        _state.update {
            it.copy(step = step.returnTo?.let(RecognitionStep::Confirm) ?: RecognitionStep.Input)
        }
    }

    /** Throws the shot and its reading away and goes back to the start. Nothing was written. */
    fun discardPhoto() = _state.update {
        it.copy(photo = null, step = RecognitionStep.Input)
    }

    private suspend fun outcomeToStep(outcome: RecognitionOutcome): RecognitionStep = when (outcome) {
        is RecognitionOutcome.Success -> RecognitionStep.Confirm(
            recognition.toDraft(
                items = outcome.meal.items,
                mealTypeGuess = outcome.meal.mealTypeGuess,
                notes = outcome.meal.notes,
                fromCache = outcome.fromCache,
            ),
        )

        RecognitionOutcome.NoFoodFound -> RecognitionStep.NothingFound

        is RecognitionOutcome.Failure -> RecognitionStep.Problem(outcome.kind, outcome.message)
    }

    // --- editing the draft -------------------------------------------------------------

    private fun editDraft(transform: (DraftMeal) -> DraftMeal) {
        _state.update { current ->
            val step = current.step
            if (step is RecognitionStep.Confirm) {
                current.copy(step = RecognitionStep.Confirm(transform(step.draft)))
            } else {
                current
            }
        }
    }

    private fun editItem(id: Long, transform: (DraftItem) -> DraftItem) = editDraft { draft ->
        draft.copy(
            items = draft.items.map { if (it.id == id) transform(it).copy(edited = true) else it },
        )
    }

    /**
     * Renaming a row corrects its numbers too. "Muesli with milk" renamed to "oats" that keeps
     * muesli's macros is simply wrong data, so once typing stops the name is looked up — the
     * user's own products first, then the catalogue — and a match's figures are applied at the
     * weight already on the row. No match leaves the numbers alone; there is nothing to apply.
     */
    fun setItemName(id: Long, name: String) {
        editItem(id) { it.copy(name = name) }

        renameLookup?.cancel()
        renameLookup = viewModelScope.launch {
            delay(RENAME_SETTLE_MS)
            applyRenameMatch(id, name.trim())
        }
    }

    private suspend fun applyRenameMatch(id: Long, typed: String) {
        if (typed.length < MIN_RENAME_LOOKUP) return

        val product = products.search(typed).firstOrNull()
        val food = if (product == null) foods.search(typed).firstOrNull() else null

        editItem(id) { item ->
            // The row may have been typed into again while the lookup was in flight; applying
            // a match for a name that is no longer there would overwrite the newer one.
            if (item.name.trim() != typed) {
                item
            } else {
                product?.let(item::rematchedTo) ?: food?.let(item::rematchedTo) ?: item
            }
        }
    }

    // --- swapping a row for the user's own product, or a catalogue match -----------------

    fun openProductPicker(itemId: Long) {
        _state.update { it.copy(productPicker = ProductPicker(itemId)) }
        searchProducts("")
    }

    fun closeProductPicker() = _state.update { it.copy(productPicker = null) }

    fun searchProducts(query: String) {
        val picker = _state.value.productPicker ?: return
        _state.update { it.copy(productPicker = picker.copy(query = query)) }

        viewModelScope.launch {
            // A blank box shows only products: with a handful of them, browsing beats typing,
            // but the ~8,500-row catalogue is only worth showing once there's something to match.
            val hits = if (query.isBlank()) {
                products.all().map(SearchHit::Product)
            } else {
                products.search(query).map(SearchHit::Product) +
                    foods.search(query).map(SearchHit::Catalogue)
            }
            _state.update { state ->
                state.productPicker
                    ?.takeIf { it.itemId == picker.itemId }
                    ?.let { state.copy(productPicker = it.copy(results = hits)) }
                    ?: state
            }
        }
    }

    /**
     * Replaces the model's estimate with the picked food's own numbers, at the weight already
     * on the row — the amount on the plate did not change, only whose figures describe it. This
     * is what fixes a misidentified item: the name AND the macros are corrected together.
     */
    /**
     * Leaves the picker for the product form, remembering which row asked.
     *
     * A packaged food you own is worth recording once and reusing forever, and the moment you
     * notice it is missing is the moment you are looking at it on a plate — not a later trip to
     * the products tab, by which time the meal is logged with an estimate.
     */
    fun addProductFor(itemId: Long) = _state.update {
        it.copy(productPicker = null, awaitingProductFor = itemId)
    }

    /** The new product goes straight onto the row that sent the user off to add it. */
    fun applyNewProduct(product: ProductEntity) {
        val itemId = _state.value.awaitingProductFor ?: return

        editItem(itemId) { it.swappedFor(product) }
        _state.update { it.copy(awaitingProductFor = null) }
    }

    /** Backing out of the form leaves the row exactly as the model read it. */
    fun cancelNewProduct() = _state.update { it.copy(awaitingProductFor = null) }

    /** What the row is currently called, to prefill the product's name. */
    fun nameOf(itemId: Long): String =
        (_state.value.step as? RecognitionStep.Confirm)
            ?.draft?.items?.firstOrNull { it.id == itemId }
            ?.name
            .orEmpty()

    fun pickHit(hit: SearchHit) {
        val itemId = _state.value.productPicker?.itemId ?: return

        when (hit) {
            is SearchHit.Product -> editItem(itemId) { it.swappedFor(hit.product) }
            is SearchHit.Catalogue -> editItem(itemId) { it.swappedFor(hit.food) }
        }
        closeProductPicker()
    }

    /**
     * Changing the weight rescales the macros with it, because the model's numbers were for its
     * own estimated weight. The count follows too: a row reading "2 roti · 80 g" that is dragged
     * to 120 g is three rotis, and leaving it saying two would be a lie on the face of the row.
     * Editing a macro directly (below) leaves the others alone.
     */
    fun setItemGrams(id: Long, grams: Double) = editItem(id) { item ->
        val perUnit = item.gramsPerUnit
        item.rescaledTo(grams).copy(
            quantity = if (perUnit > 0) grams / perUnit else item.quantity,
        )
    }

    /**
     * Changing the count is the other way into the same pair. Most portions are naturally said
     * in servings rather than grams — three rotis, half a katori — and until now the confirm
     * screen could only be told about grams.
     */
    fun setItemQuantity(id: Long, quantity: Double) = editItem(id) { item ->
        val perUnit = item.gramsPerUnit
        if (perUnit <= 0) {
            item.copy(quantity = quantity)
        } else {
            item.rescaledTo(quantity * perUnit).copy(quantity = quantity)
        }
    }

    /**
     * Switching the measure keeps the food on the plate exactly as it was and only restates it:
     * 150 g logged as katori is 1 katori, and as roti it is 3.75 of them. Recomputing the count
     * rather than the weight is what stops a tap on a chip from silently changing the meal.
     */
    fun setItemUnit(id: Long, choice: PortionChoice) = editItem(id) { item ->
        item.copy(
            unit = choice.label,
            quantity = if (choice.gramsPerUnit > 0) item.grams / choice.gramsPerUnit else item.quantity,
        )
    }

    fun setItemKcal(id: Long, kcal: Double) = editItem(id) { it.copy(kcal = kcal) }

    fun setItemProtein(id: Long, grams: Double) = editItem(id) { it.copy(proteinG = grams) }

    fun setItemCarbs(id: Long, grams: Double) = editItem(id) { it.copy(carbsG = grams) }

    fun setItemFat(id: Long, grams: Double) = editItem(id) { it.copy(fatG = grams) }

    fun removeItem(id: Long) = editDraft { draft ->
        draft.copy(items = draft.items.filterNot { it.id == id })
    }

    /**
     * Fills a row in from what the user typed, rather than from a catalogue match.
     *
     * The search box is the description: the food that is not on the shelf and not in the
     * catalogue is exactly the one worth describing — "two rotis and a katori of dal" — and
     * the model already reads free text for the main flow. One row can come back as several,
     * because that is what a sentence often names.
     *
     * The row that asked is replaced rather than appended, so the blank that opened this
     * picker does not survive as an empty line beside the answer.
     */
    fun describeItem() {
        val picker = _state.value.productPicker ?: return
        val described = picker.query.trim()
        if (described.isEmpty() || picker.describing) return

        viewModelScope.launch {
            _state.update {
                it.copy(productPicker = picker.copy(describing = true, describeError = null))
            }

            val outcome = recognition.recognizeText(described)
            val rows = when (outcome) {
                is RecognitionOutcome.Success -> recognition.toDraft(
                    items = outcome.meal.items,
                    mealTypeGuess = outcome.meal.mealTypeGuess,
                    notes = outcome.meal.notes,
                    fromCache = outcome.fromCache,
                ).items

                RecognitionOutcome.NoFoodFound -> emptyList()
                is RecognitionOutcome.Failure -> null
            }

            val failure = when {
                rows == null -> (outcome as RecognitionOutcome.Failure).message
                rows.isEmpty() -> "Couldn't tell what that is. Try naming the food and roughly how much."
                else -> null
            }

            if (rows.isNullOrEmpty()) {
                _state.update { current ->
                    val open = current.productPicker ?: return@update current
                    current.copy(
                        productPicker = open.copy(describing = false, describeError = failure),
                    )
                }
                return@launch
            }

            replaceItem(picker.itemId, rows)
            closeProductPicker()
        }
    }

    /** Swaps one row for the rows a description produced, keeping every id unique. */
    private fun replaceItem(itemId: Long, rows: List<DraftItem>) = editDraft { draft ->
        var nextId = (draft.items.maxOfOrNull { it.id } ?: -1L) + 1
        val renumbered = rows.map { row -> row.copy(id = nextId++, edited = true) }

        draft.copy(
            items = draft.items.flatMap { existing ->
                if (existing.id == itemId) renumbered else listOf(existing)
            },
        )
    }

    /**
     * A hand-added row starts with no food behind it, so the picker opens on top of it: typing
     * four macros by hand is the fallback, not the first thing asked of the user.
     */
    fun addBlankItem() {
        val nextId = nextItemId() ?: return
        addBlankItemRow(nextId)
        openProductPicker(nextId)
    }

    private fun nextItemId(): Long? {
        val step = _state.value.step as? RecognitionStep.Confirm ?: return null
        return (step.draft.items.maxOfOrNull { it.id } ?: -1L) + 1
    }

    private fun addBlankItemRow(nextId: Long) = editDraft { draft ->
        draft.copy(
            items = draft.items + DraftItem(
                id = nextId,
                name = "",
                originalName = "",
                // A row the user added themselves has no model guess to be corrected against.
                originalGrams = 0.0,
                quantity = 1.0,
                unit = "g",
                grams = 100.0,
                kcal = 0.0,
                proteinG = 0.0,
                carbsG = 0.0,
                fatG = 0.0,
                confidence = null,
                basis = null,
                sourceNote = null,
                edited = true,
            ),
        )
    }

    fun setMealType(meal: MealType) = editDraft { it.copy(mealType = meal) }

    // --- leaving ----------------------------------------------------------------------

    /** Discarding writes nothing at all. That is the whole trust mechanism (PRD §7.3). */
    fun discard() = _state.update { RecognitionUiState() }

    fun save() {
        val step = _state.value.step as? RecognitionStep.Confirm ?: return
        val draft = step.draft
        if (draft.items.isEmpty()) return
        val editing = _state.value.editingEntryId

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }

            if (editing != null) {
                recognition.update(editing, draft)
            } else {
                val entryId = recognition.save(
                    draft = draft,
                    date = _state.value.logDate ?: log.today(recognition.dayStartHour()),
                    photoUri = null,
                    userHint = _state.value.text.takeIf { it.isNotBlank() },
                )
                // The photo is written only now, so a discarded recognition leaves no file behind.
                _state.value.photo?.let {
                    recognition.attachPhoto(entryId, photos.persist(it, entryId))
                }
            }

            _state.update {
                it.copy(
                    saving = false,
                    step = RecognitionStep.Saved(draft.totalKcal.toInt(), edited = editing != null),
                )
            }
        }
    }

    fun reset() = _state.update { RecognitionUiState() }

    private companion object {
        /** Long enough that a lookup does not fire mid-word, short enough to feel immediate. */
        const val RENAME_SETTLE_MS = 450L

        /** Two letters match half the catalogue; a match that loose is worse than none. */
        const val MIN_RENAME_LOOKUP = 3

        /**
         * The measures worth putting on a row, in the order they are offered. The seed carries
         * twenty-five; the rest (pakora, samosa, vada) are dish names rather than measures and
         * belong to the food, not to a chip row the user has to read past.
         */
        val COUNTABLE_UNITS = listOf(
            "g", "ml", "piece", "katori", "bowl", "cup", "glass", "tbsp", "tsp",
            "roti", "slice", "ladle", "scoop",
        )

        /**
         * The two units that are not portions at all but the scales everything else resolves
         * to. Millilitres count as grams throughout the app — a label for a liquid prints per
         * 100 ml and its figures are stored as per 100 g, so converting here would double-count
         * a density the numbers already assume.
         */
        val BASE_UNITS = mapOf("g" to 1.0, "ml" to 1.0)
    }
}
