package com.yash.tracker.ui.products

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.local.entity.ProductEntity
import com.yash.tracker.data.photo.PhotoStore
import com.yash.tracker.data.photo.ProcessedPhoto
import com.yash.tracker.data.remote.GeminiClient
import com.yash.tracker.data.remote.LabelOutcome
import com.yash.tracker.data.remote.ParsedLabelDto
import com.yash.tracker.domain.nutrition.FlagTone
import com.yash.tracker.domain.nutrition.IngredientFlag
import com.yash.tracker.domain.nutrition.IngredientFlags
import com.yash.tracker.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Every field as text, because a nutrition panel is transcribed a digit at a time and a
 * half-typed "12." is not a Double yet.
 */
data class ProductDraft(
    val id: Long = 0,
    val brand: String = "",
    val name: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fibre: String = "",
    val sugar: String = "",
    val sodiumMg: String = "",
    val servingG: String = "",
    val servingLabel: String = "",
    val ingredients: String = "",
    /** Read off the list rather than typed: the user edits the list, not the reading of it. */
    val ingredientFlags: List<IngredientFlag> = emptyList(),
    val ingredientVerdict: String? = null,
    val labelPhotoUri: String? = null,
    val createdAt: Long = 0,
) {
    val isValid: Boolean
        get() = name.isNotBlank() &&
            kcal.toDoubleOrNull() != null &&
            protein.toDoubleOrNull() != null &&
            carbs.toDoubleOrNull() != null &&
            fat.toDoubleOrNull() != null

    fun toEntity() = ProductEntity(
        id = id,
        brand = brand.trim().ifBlank { null },
        name = name.trim(),
        kcal100g = kcal.toDouble(),
        protein100g = protein.toDouble(),
        carbs100g = carbs.toDouble(),
        fat100g = fat.toDouble(),
        fibre100g = fibre.toDoubleOrNull(),
        sugar100g = sugar.toDoubleOrNull(),
        sodiumMg100g = sodiumMg.toDoubleOrNull(),
        servingG = servingG.toDoubleOrNull(),
        servingLabel = servingLabel.trim().ifBlank { null },
        ingredients = ingredients.trim().ifBlank { null },
        ingredientFlags = IngredientFlags.encode(ingredientFlags),
        ingredientVerdict = ingredientVerdict?.trim()?.ifBlank { null },
        labelPhotoUri = labelPhotoUri,
        barcode = null,
        createdAt = createdAt,
        updatedAt = 0,
    )
}

data class ProductsUiState(
    val editing: ProductDraft? = null,
    val capturing: Boolean = false,
    val scanning: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    /**
     * The product just written, for a host that needs it rather than only the shelf. The
     * products screen ignores it; the recognition picker uses it to put the product straight
     * onto the row that sent the user here.
     */
    val lastSaved: ProductEntity? = null,
)

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val products: ProductRepository,
    private val photos: PhotoStore,
    private val gemini: GeminiClient,
    private val saved: SavedStateHandle,
) : ViewModel() {

    val all = products.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(ProductsUiState())
    val state = _state.asStateFlow()

    private var pendingPhoto: ProcessedPhoto? = null

    /**
     * Held in saved state, not a field. Launching the camera puts this app in the background and
     * the system is free to kill it while a heavyweight camera app starts — which is likeliest on
     * the first capture, when that app is cold. A plain field comes back null, the result lands
     * nowhere, and the scan silently does nothing until you try again.
     */
    private var captureUri: Uri?
        get() = saved[CAPTURE_URI]
        set(value) {
            saved[CAPTURE_URI] = value
        }

    /** [name] prefills the form when the product is being added to answer a specific row. */
    fun addNew(name: String = "") = _state.update {
        ProductsUiState(editing = ProductDraft(name = name.trim()))
    }

    fun edit(product: ProductEntity) = _state.update {
        ProductsUiState(editing = product.toDraft())
    }

    fun closeEditor() {
        pendingPhoto = null
        _state.update { ProductsUiState() }
    }

    fun update(change: (ProductDraft) -> ProductDraft) = _state.update {
        it.copy(editing = it.editing?.let(change), error = null)
    }

    fun newCaptureFile(): java.io.File {
        val (_, file) = photos.newCaptureTarget()
        captureUri = android.net.Uri.fromFile(file)
        return file
    }

    fun openCamera() = _state.update { it.copy(capturing = true, error = null, notice = null) }

    fun closeCamera() = _state.update { it.copy(capturing = false) }

    /**
     * Reads the panel and fills in what it found, leaving anything it could not read blank
     * rather than zero — a blank field invites a correction, a zero looks like an answer.
     */
    fun onLabelCaptured(taken: Boolean) {
        val uri = captureUri
        if (!taken || uri == null) {
            _state.update { it.copy(capturing = false) }
            return
        }

        _state.update { it.copy(capturing = false, scanning = true, error = null, notice = null) }

        viewModelScope.launch {
            val photo = runCatching { photos.process(uri, PhotoStore.LABEL_EDGE) }.getOrNull()
            if (photo == null) {
                _state.update {
                    it.copy(
                        scanning = false,
                        editing = it.editing ?: ProductDraft(),
                        error = "That photo could not be read.",
                    )
                }
                return@launch
            }
            pendingPhoto = photo

            when (val outcome = gemini.parseLabel(photo.base64)) {
                // If the process was killed while the camera was open the draft is gone, so the
                // reading starts a fresh one rather than being dropped on the floor.
                is LabelOutcome.Success -> _state.update {
                    it.copy(
                        scanning = false,
                        editing = (it.editing ?: ProductDraft()).filledFrom(outcome.label),
                        notice = noticeFor(outcome.label),
                    )
                }

                is LabelOutcome.NoLabelFound -> _state.update {
                    it.copy(
                        scanning = false,
                        editing = it.editing ?: ProductDraft(),
                        error = "No nutrition panel in that photo. Type the numbers instead.",
                    )
                }

                is LabelOutcome.Failure -> _state.update {
                    it.copy(
                        scanning = false,
                        editing = it.editing ?: ProductDraft(),
                        error = outcome.message,
                    )
                }
            }
            photos.clearCaptureCache()
        }
    }

    fun save() {
        val draft = _state.value.editing ?: return
        if (!draft.isValid) {
            _state.update { it.copy(error = "Name, kcal, protein, carbs and fat are needed.") }
            return
        }

        viewModelScope.launch {
            val id = products.save(draft.toEntity())
            // The label photo is kept only once the product it belongs to exists.
            pendingPhoto?.let { photo ->
                val path = photos.persist(photo, id)
                products.save(products.getById(id)!!.copy(labelPhotoUri = path))
            }
            val stored = products.getById(id)
            closeEditor()
            _state.update { it.copy(lastSaved = stored) }
        }
    }

    /** Taken by whoever was waiting for it, so a later save is told apart from this one. */
    fun consumeSaved() = _state.update { it.copy(lastSaved = null) }

    fun delete(id: Long) {
        viewModelScope.launch {
            products.delete(id)
            closeEditor()
        }
    }

    private companion object {
        const val CAPTURE_URI = "capture_uri"
    }

    private fun noticeFor(label: ParsedLabelDto): String? = when {
        label.convertedFromServing ->
            "The pack only gave per-serving figures, so these were divided down to 100 g. Worth checking."
        else -> null
    }
}

private fun ProductEntity.toDraft() = ProductDraft(
    id = id,
    brand = brand.orEmpty(),
    name = name,
    kcal = kcal100g.clean(),
    protein = protein100g.clean(),
    carbs = carbs100g.clean(),
    fat = fat100g.clean(),
    fibre = fibre100g?.clean().orEmpty(),
    sugar = sugar100g?.clean().orEmpty(),
    sodiumMg = sodiumMg100g?.clean().orEmpty(),
    servingG = servingG?.clean().orEmpty(),
    servingLabel = servingLabel.orEmpty(),
    ingredients = ingredients.orEmpty(),
    ingredientFlags = IngredientFlags.decode(ingredientFlags),
    ingredientVerdict = ingredientVerdict,
    labelPhotoUri = labelPhotoUri,
    createdAt = createdAt,
)

/** Only fills the blanks it actually read, so a re-scan never wipes a correction. */
private fun ProductDraft.filledFrom(label: ParsedLabelDto) = copy(
    brand = label.brand?.takeIf { it.isNotBlank() } ?: brand,
    name = label.name?.takeIf { it.isNotBlank() } ?: name,
    kcal = label.kcal100g?.clean() ?: kcal,
    protein = label.protein100g?.clean() ?: protein,
    carbs = label.carbs100g?.clean() ?: carbs,
    fat = label.fat100g?.clean() ?: fat,
    fibre = label.fibre100g?.clean() ?: fibre,
    sugar = label.sugar100g?.clean() ?: sugar,
    sodiumMg = label.sodiumMg100g?.clean() ?: sodiumMg,
    servingG = label.servingG?.clean() ?: servingG,
    servingLabel = label.servingLabel?.takeIf { it.isNotBlank() } ?: servingLabel,
    ingredients = label.ingredients?.takeIf { it.isNotBlank() } ?: ingredients,
    // The reading belongs to the list that was read: a scan that found no list must not leave
    // the previous packet's flags standing under a new one's ingredients.
    ingredientFlags = if (label.ingredients.isNullOrBlank()) {
        ingredientFlags
    } else {
        label.ingredientFlags.map {
            IngredientFlag(
                label = it.label.trim(),
                detail = it.detail?.trim()?.ifBlank { null },
                tone = FlagTone.parse(it.tone),
            )
        }
    },
    ingredientVerdict = label.ingredientVerdict?.takeIf { it.isNotBlank() } ?: ingredientVerdict,
)

/** 400.0 reads as "400" on a label, not "400.0". */
private fun Double.clean(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()
