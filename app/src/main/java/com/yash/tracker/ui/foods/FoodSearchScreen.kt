package com.yash.tracker.ui.foods

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.tracker.data.local.dao.MealWithItems
import com.yash.tracker.data.local.entity.ProductEntity
import com.yash.tracker.data.repository.displayName
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.ui.components.ActionTone
import com.yash.tracker.ui.components.EmptyNote
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.GroupHeader
import com.yash.tracker.ui.components.IconPlate
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.LuxTextField
import com.yash.tracker.ui.components.LuxTopBar
import com.yash.tracker.ui.components.Metric
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillButton
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SearchField
import com.yash.tracker.ui.components.SelectableChip
import com.yash.tracker.ui.components.SmallAction
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.StaggerIn
import com.yash.tracker.ui.theme.EyebrowStyle
import kotlin.math.roundToInt

/**
 * The library: what you own, what you have saved, and the catalogue behind both. Typing
 * collapses all three into one ranked result list — your own products first, because if you
 * added your oats, those are the oats you mean.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(
    onOpenProducts: () -> Unit = {},
    viewModel: FoodSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val savedMeals by viewModel.savedMeals.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(state.justSaved) {
        state.justSaved?.let {
            snackbars.showSnackbar(it)
            viewModel.clearSavedMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            LuxTopBar(
                eyebrow = "Library",
                title = "Foods",
                actions = {
                    Pill(
                        text = "%,d items".format(state.catalogueSize),
                        tone = PillTone.Quiet,
                        leadingDot = true,
                    )
                },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    StaggerIn(0) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PillButton(
                                text = "Add product",
                                onClick = onOpenProducts,
                                tone = ActionTone.Accent,
                                leadingIcon = Icons.Default.Add,
                                height = 48.dp,
                                modifier = Modifier.weight(1f),
                            )
                            SmallAction(
                                text = "My shelf",
                                icon = Icons.Outlined.Inventory2,
                                onClick = onOpenProducts,
                            )
                        }
                    }
                }

                item {
                    StaggerIn(1) {
                        SearchField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            placeholder = "Search catalogue, products, meals",
                        )
                    }
                }

                if (state.query.isNotBlank()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FoodFilter.entries.forEach { filter ->
                                SelectableChip(
                                    label = filter.label,
                                    selected = filter == state.filter,
                                    onClick = { viewModel.setFilter(filter) },
                                )
                            }
                        }
                    }
                }

                when {
                    state.query.isBlank() -> library(
                        products = products,
                        savedMeals = savedMeals,
                        onOpenProducts = onOpenProducts,
                        onPickProduct = { viewModel.select(SearchHit.Product(it)) },
                        onLogMeal = { viewModel.logSavedMeal(it.template.id, it.template.name) },
                    )

                    state.searching && state.results.isEmpty() -> item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(Modifier.size(28.dp)) }
                    }

                    state.visibleResults.isEmpty() -> item {
                        EmptyNote("Nothing matched \"${state.query}\". Try a shorter word.")
                    }

                    else -> {
                        item { GroupHeader("Results", count = "${state.visibleResults.size}") }
                        // No item animation here: a new query replaces every row at once, and
                        // animating a wholesale swap leaves the list holding a gap where the old
                        // rows were until it is scrolled.
                        items(state.visibleResults, key = { it.key }) { hit ->
                            HitRow(hit) { viewModel.select(hit) }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbars,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) { data ->
            Snackbar(
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) { Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    state.selected?.let { selected ->
        ModalBottomSheet(
            onDismissRequest = viewModel::dismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            LogFoodSheet(selected, viewModel)
        }
    }
}

/** The blank-query view: your own shelf and your own meals, never the whole catalogue. */
private fun androidx.compose.foundation.lazy.LazyListScope.library(
    products: List<ProductEntity>,
    savedMeals: List<MealWithItems>,
    onOpenProducts: () -> Unit,
    onPickProduct: (ProductEntity) -> Unit,
    onLogMeal: (MealWithItems) -> Unit,
) {
    item {
        Spacer(Modifier.height(2.dp))
        GroupHeader(
            title = "My products",
            count = "${products.size}",
            action = if (products.isEmpty()) null else "View all",
            onAction = onOpenProducts,
        )
    }

    if (products.isEmpty()) {
        item {
            EmptyNote(
                "Nothing added yet. Photograph a nutrition panel and the form fills itself in.",
            )
        }
    } else {
        items(products.take(3), key = { "product-${it.id}" }) { product ->
            ProductRow(product) { onPickProduct(product) }
        }
    }

    if (savedMeals.isNotEmpty()) {
        item {
            Spacer(Modifier.height(6.dp))
            GroupHeader("Saved meals", count = "${savedMeals.size}")
        }
        items(savedMeals, key = { "meal-${it.template.id}" }) { meal ->
            SavedMealRow(meal) { onLogMeal(meal) }
        }
    }

    item {
        Spacer(Modifier.height(6.dp))
        GroupHeader("Bundled catalogue")
    }
    item {
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("INDB / USDA", tone = PillTone.Solid)
                    Spacer(Modifier.width(8.dp))
                    Eyebrow("Standardised")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Indian dishes and staples, plus the USDA reference tables. " +
                        "Search above — roti, dal, paneer, oats, eggs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// --- rows -----------------------------------------------------------------------------------

@Composable
private fun ProductRow(product: ProductEntity, onClick: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            product.displayName(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Outlined.Verified,
                            contentDescription = "From the packet",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(
                            product.brand?.takeIf { it.isNotBlank() },
                            product.servingLabel?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ").ifBlank { "Your product" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Metric("${product.kcal100g.roundToInt()}", unit = "kcal", large = false)
            }

            Spacer(Modifier.height(12.dp))
            MacroChips(
                protein = product.protein100g,
                carbs = product.carbs100g,
                fat = product.fat100g,
                trailing = "per 100 g",
            )
        }
    }
}

@Composable
private fun SavedMealRow(meal: MealWithItems, onLog: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        meal.template.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        meal.items.joinToString(" · ") { it.name }.ifBlank { "No items" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Metric("${meal.template.kcal.roundToInt()}", unit = "kcal", large = false)
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MacroChips(
                    protein = meal.template.proteinG,
                    carbs = meal.template.carbsG,
                    fat = meal.template.fatG,
                    modifier = Modifier.weight(1f),
                )
                SmallAction(
                    text = "Log meal",
                    icon = Icons.Default.Bolt,
                    onClick = onLog,
                    tone = ActionTone.Ink,
                )
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, modifier: Modifier = Modifier, onClick: () -> Unit) {
    LuxCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPlate(
                icon = if (hit is SearchHit.Product) Icons.Outlined.Inventory2 else Icons.Outlined.Restaurant,
                size = 42.dp,
                tone = if (hit is SearchHit.Product) PillTone.Accent else PillTone.Quiet,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        hit.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (hit.isExact) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Outlined.Verified,
                            contentDescription = "Exact figures",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    hit.subtitle.ifBlank { "catalogue" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${hit.per100g.kcal100g.roundToInt()}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Eyebrow("per 100 g")
            }
        }
    }
}

@Composable
private fun MacroChips(
    protein: Double,
    carbs: Double,
    fat: Double,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            Pill("P ${protein.roundToInt()}g", tone = PillTone.Solid)
            Pill("C ${carbs.roundToInt()}g", tone = PillTone.Solid)
            Pill("F ${fat.roundToInt()}g", tone = PillTone.Solid)
        }
        if (trailing != null) {
            Text(
                trailing,
                style = EyebrowStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- log sheet ------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogFoodSheet(selected: SelectedFood, viewModel: FoodSearchViewModel) {
    val macros = selected.macros

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Eyebrow(if (selected.hit.isExact) "Exact figures" else "Catalogue estimate")
        Spacer(Modifier.height(4.dp))
        Text(
            selected.hit.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LuxTextField(
                value = selected.quantityText,
                onValueChange = { viewModel.setQuantity(it.filter { c -> c.isDigit() || c == '.' }) },
                label = "Amount",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(130.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            selected.choices.forEachIndexed { index, choice ->
                SelectableChip(
                    label = choice.label,
                    selected = index == selected.choiceIndex,
                    onClick = { viewModel.setChoice(index) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        LuxCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Metric("${macros.kcal.roundToInt()}", unit = "kcal")
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${selected.grams.roundToInt()} g",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                MacroChips(macros.proteinG, macros.carbsG, macros.fatG)
            }
        }

        Spacer(Modifier.height(18.dp))
        Eyebrow("Meal")
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MealType.entries.forEach { meal ->
                SelectableChip(
                    label = meal.name.lowercase().replaceFirstChar(Char::uppercase),
                    selected = meal == selected.mealType,
                    onClick = { viewModel.setMeal(meal) },
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        PillButton(
            text = "Save entry",
            onClick = viewModel::save,
            enabled = selected.isValid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
