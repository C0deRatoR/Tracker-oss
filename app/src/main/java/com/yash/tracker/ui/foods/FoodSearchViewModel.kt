package com.yash.tracker.ui.foods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.local.dao.MealWithItems
import com.yash.tracker.data.local.entity.ProductEntity
import com.yash.tracker.data.repository.FoodRepository
import com.yash.tracker.data.repository.LogRepository
import com.yash.tracker.data.repository.MealRepository
import com.yash.tracker.data.repository.ProductRepository
import com.yash.tracker.data.repository.ProfileRepository
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.diary.MealWindows
import com.yash.tracker.domain.nutrition.Macros
import com.yash.tracker.domain.nutrition.PortionChoice
import com.yash.tracker.domain.nutrition.PortionResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class SelectedFood(
    val hit: SearchHit,
    val choices: List<PortionChoice>,
    val choiceIndex: Int = 0,
    val quantityText: String = "1",
    val mealType: MealType,
) {
    val quantity: Double get() = quantityText.trim().toDoubleOrNull() ?: 0.0
    val choice: PortionChoice get() = choices[choiceIndex.coerceIn(choices.indices)]
    val grams: Double get() = PortionResolver.grams(quantity, choice)
    val macros: Macros get() = PortionResolver.macrosFor(hit.per100g, grams)
    val isValid: Boolean get() = quantity > 0
}

/** Which slice of the library the results list is showing. */
enum class FoodFilter(val label: String) {
    ALL("All"),
    MINE("My products"),
    CATALOGUE("Catalogue"),
}

data class FoodSearchUiState(
    val query: String = "",
    val results: List<SearchHit> = emptyList(),
    val searching: Boolean = false,
    val filter: FoodFilter = FoodFilter.ALL,
    val catalogueSize: Int = 0,
    val selected: SelectedFood? = null,
    val justSaved: String? = null,
) {
    /** Filtering happens here rather than in the query: both sources are already in hand. */
    val visibleResults: List<SearchHit>
        get() = when (filter) {
            FoodFilter.ALL -> results
            FoodFilter.MINE -> results.filterIsInstance<SearchHit.Product>()
            FoodFilter.CATALOGUE -> results.filterIsInstance<SearchHit.Catalogue>()
        }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class FoodSearchViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val logRepository: LogRepository,
    private val productRepository: ProductRepository,
    private val profileRepository: ProfileRepository,
    private val mealRepository: MealRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FoodSearchUiState())
    val state = _state.asStateFlow()

    private val queries = MutableStateFlow("")

    /** The library shown when nothing has been typed: your own shelf, then your own meals. */
    val products: StateFlow<List<ProductEntity>> = productRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedMeals: StateFlow<List<MealWithItems>> = mealRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _state.update { it.copy(catalogueSize = foodRepository.count()) }
        }

        viewModelScope.launch {
            // Debounced so a fast typist does not fire a query per keystroke across 8,595 rows.
            queries.debounce(200).collect { query ->
                if (query.isBlank()) {
                    _state.update { it.copy(results = emptyList(), searching = false) }
                    return@collect
                }
                _state.update { it.copy(searching = true) }
                // Products first: if the user has added their own oats, that is the row they
                // mean, not the catalogue's generic one.
                val results = productRepository.search(query).map(SearchHit::Product) +
                    foodRepository.search(query).map(SearchHit::Catalogue)
                _state.update { it.copy(results = results, searching = false) }
            }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, justSaved = null) }
        queries.value = query
    }

    fun setFilter(filter: FoodFilter) = _state.update { it.copy(filter = filter) }

    /** The same two-tap log the dashboard offers, reachable from the library it lives in. */
    fun logSavedMeal(mealId: Long, name: String) {
        viewModelScope.launch {
            val date = logRepository.today(profileRepository.dayStartHour())
            mealRepository.logMeal(mealId, date)
            _state.update { it.copy(justSaved = "$name logged") }
        }
    }

    fun deleteSavedMeal(mealId: Long) {
        viewModelScope.launch { mealRepository.delete(mealId) }
    }

    fun select(hit: SearchHit) {
        viewModelScope.launch {
            val choices = when (hit) {
                is SearchHit.Catalogue ->
                    PortionResolver.choicesFor(hit.food, foodRepository.portionsFor(hit.food.id))
                is SearchHit.Product -> productRepository.choicesFor(hit.product)
            }
            _state.update {
                it.copy(
                    selected = SelectedFood(
                        hit = hit,
                        choices = choices,
                        mealType = MealWindows.defaultFor(LocalTime.now().hour),
                    ),
                )
            }
        }
    }

    fun dismiss() = _state.update { it.copy(selected = null) }

    fun setQuantity(text: String) =
        _state.update { it.copy(selected = it.selected?.copy(quantityText = text)) }

    fun setChoice(index: Int) =
        _state.update { it.copy(selected = it.selected?.copy(choiceIndex = index)) }

    fun setMeal(meal: MealType) =
        _state.update { it.copy(selected = it.selected?.copy(mealType = meal)) }

    fun save() {
        val selected = _state.value.selected ?: return
        if (!selected.isValid) return

        viewModelScope.launch {
            val date = logRepository.today(profileRepository.dayStartHour())
            when (val hit = selected.hit) {
                is SearchHit.Catalogue -> {
                    logRepository.logFood(
                        date = date,
                        mealType = selected.mealType,
                        food = hit.food,
                        quantity = selected.quantity,
                        choice = selected.choice,
                    )
                    foodRepository.markLogged(hit.food.id)
                }

                is SearchHit.Product -> productRepository.log(
                    date = date,
                    mealType = selected.mealType,
                    product = hit.product,
                    quantity = selected.quantity,
                    choice = selected.choice,
                )
            }
            _state.update {
                it.copy(selected = null, justSaved = "${selected.hit.title} logged")
            }
        }
    }

    fun clearSavedMessage() = _state.update { it.copy(justSaved = null) }
}
