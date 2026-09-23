package com.yash.tracker.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.tracker.data.local.entity.ProductEntity
import com.yash.tracker.domain.nutrition.FlagTone
import com.yash.tracker.domain.nutrition.IngredientFlag
import com.yash.tracker.domain.nutrition.IngredientFlags
import com.yash.tracker.ui.components.ActionTone
import com.yash.tracker.ui.components.CameraCapture
import com.yash.tracker.ui.components.DetailTopBar
import com.yash.tracker.ui.components.EmptyNote
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.GroupHeader
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.LuxTextField
import com.yash.tracker.ui.components.Metric
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillButton
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.StaggerIn
import com.yash.tracker.ui.components.TextAction
import com.yash.tracker.ui.theme.EyebrowStyle
import kotlin.math.roundToInt

/**
 * Packaged food with the numbers off its own label, so "oats" can be *my* oats.
 *
 * The label photo fills the form in; it never saves on its own. Every field stays editable
 * because a panel photographed at an angle is read wrong often enough to matter, and the
 * whole point of a product is that its numbers are exact.
 */
@Composable
fun ProductsScreen(
    onBack: () -> Unit = {},
    viewModel: ProductsViewModel = hiltViewModel(),
) {
    val products by viewModel.all.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProductEditorHost(viewModel)
    // The label camera takes the whole screen; the shelf behind it would only be composed to
    // sit unseen underneath.
    if (state.capturing) return

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailTopBar(
            title = "My products",
            onBack = onBack,
            actions = {
                TextAction("Add", onClick = viewModel::addNew, color = MaterialTheme.colorScheme.primary)
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StaggerIn(0) {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Pill("Verified", tone = PillTone.Accent, icon = Icons.Outlined.Verified)
                                Spacer(Modifier.width(8.dp))
                                Eyebrow("${products.size} on the shelf")
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Numbers taken from the packet. These are treated as exact and " +
                                    "never replaced by an estimate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                StaggerIn(1) {
                    PillButton(
                        text = "Add a product",
                        onClick = viewModel::addNew,
                        leadingIcon = Icons.Default.Add,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Spacer(Modifier.height(2.dp))
                GroupHeader("Packaged products", count = "${products.size}")
            }

            if (products.isEmpty()) {
                item {
                    EmptyNote(
                        "Nothing added yet. Photograph a nutrition panel and the form fills " +
                            "itself in.",
                    )
                }
            } else {
                items(products, key = { it.id }) { product ->
                    ProductCard(product, Modifier.animateItem()) { viewModel.edit(product) }
                }
            }
        }
    }
}

/**
 * Adding or editing a product: the label camera and the form it fills in.
 *
 * Public and free of its own screen so it can be raised wherever a product turns out to be
 * missing — including from the recognition picker, where "that paneer is mine" is realised
 * with a plate in front of you rather than on a later visit to the products tab.
 */
@Composable
fun ProductEditorHost(viewModel: ProductsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.capturing) {
        val target = remember { viewModel.newCaptureFile() }
        CameraCapture(
            target = target,
            onCaptured = { viewModel.onLabelCaptured(taken = true) },
            onCancel = viewModel::closeCamera,
            title = "Photograph the panel",
            hint = "Get the numbers square on and close — small print read at an angle " +
                "comes back blank.",
        )
        return
    }

    if (state.editing != null) ProductEditor(state, viewModel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductCard(
    product: ProductEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    LuxCard(modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    product.brand?.takeIf { it.isNotBlank() }?.let { Eyebrow(it) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            product.name,
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
                    product.servingLabel?.takeIf { it.isNotBlank() }?.let { label ->
                        Text(
                            "$label${product.servingG?.let { " · ${it.roundToInt()} g" }.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Metric("${product.kcal100g.roundToInt()}", unit = "kcal", large = false)
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    Pill("P ${product.protein100g.roundToInt()}g")
                    Pill("C ${product.carbs100g.roundToInt()}g")
                    Pill("F ${product.fat100g.roundToInt()}g")
                }
                Text(
                    "PER 100 G",
                    style = EyebrowStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Only what a shopper would want to avoid reaches the card. The reassurances and
            // the neutral notes are worth reading once, inside; they are not worth a row here.
            val concerns = IngredientFlags.decode(product.ingredientFlags)
                .filter { it.tone == FlagTone.CONCERN }

            if (concerns.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    concerns.forEach {
                        Pill(it.label, tone = PillTone.Warning, icon = Icons.Outlined.ErrorOutline)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditor(state: ProductsUiState, viewModel: ProductsViewModel) {
    val draft = state.editing ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = viewModel::closeEditor,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Eyebrow(if (draft.id == 0L) "New entry" else "Editing")
                Spacer(Modifier.height(4.dp))
                Text(
                    if (draft.id == 0L) "New product" else draft.name.ifBlank { "Edit product" },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(14.dp))

                if (state.scanning) {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Reading the label…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    PillButton(
                        text = "Photograph the panel",
                        onClick = viewModel::openCamera,
                        tone = ActionTone.Accent,
                        leadingIcon = Icons.Outlined.CameraAlt,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                state.notice?.let { Notice(it, error = false) }
                state.error?.let { Notice(it, error = true) }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Field(draft.brand, "Brand") { v -> viewModel.update { it.copy(brand = v) } }
            }
            item { Field(draft.name, "Product name") { v -> viewModel.update { it.copy(name = v) } } }

            item {
                Spacer(Modifier.height(8.dp))
                Eyebrow("Per 100 g")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Number(draft.kcal, "kcal", Modifier.weight(1f)) { v -> viewModel.update { it.copy(kcal = v) } }
                    Number(draft.protein, "Protein", Modifier.weight(1f)) { v -> viewModel.update { it.copy(protein = v) } }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Number(draft.carbs, "Carbs", Modifier.weight(1f)) { v -> viewModel.update { it.copy(carbs = v) } }
                    Number(draft.fat, "Fat", Modifier.weight(1f)) { v -> viewModel.update { it.copy(fat = v) } }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Number(draft.fibre, "Fibre", Modifier.weight(1f)) { v -> viewModel.update { it.copy(fibre = v) } }
                    Number(draft.sugar, "Sugar", Modifier.weight(1f)) { v -> viewModel.update { it.copy(sugar = v) } }
                }
            }
            item {
                Number(draft.sodiumMg, "Sodium (mg), optional") { v ->
                    viewModel.update { it.copy(sodiumMg = v) }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Eyebrow("Serving")
            }
            item {
                Number(draft.servingG, "Serving size (g)") { v -> viewModel.update { it.copy(servingG = v) } }
            }
            item {
                Field(draft.servingLabel, "Serving label, e.g. 1 scoop") { v ->
                    viewModel.update { it.copy(servingLabel = v) }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Eyebrow("Ingredients")
                Spacer(Modifier.height(4.dp))
                Text(
                    "In the order printed on the pack — that order is by weight, so it says " +
                        "more than the sugar figure does.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                LuxTextField(
                    value = draft.ingredients,
                    onValueChange = { v -> viewModel.update { it.copy(ingredients = v) } },
                    label = "Ingredients list",
                    singleLine = false,
                    minLines = 3,
                )
            }

            if (draft.ingredientFlags.isNotEmpty() || !draft.ingredientVerdict.isNullOrBlank()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    IngredientAnalysis(draft.ingredientFlags, draft.ingredientVerdict)
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                PillButton(
                    text = "Save product",
                    onClick = viewModel::save,
                    enabled = draft.isValid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextAction("Cancel", onClick = viewModel::closeEditor)
                    if (draft.id != 0L) {
                        Spacer(Modifier.width(8.dp))
                        TextAction(
                            "Delete",
                            onClick = { viewModel.delete(draft.id) },
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, error: Boolean) {
    Spacer(Modifier.height(10.dp))
    SoftCard(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (error) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun Field(value: String, label: String, onChange: (String) -> Unit) {
    LuxTextField(value = value, onValueChange = onChange, label = label)
}

@Composable
private fun Number(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    LuxTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(7)) },
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/**
 * What the ingredients list amounts to.
 *
 * Read off the packet at scan time and stored, so it costs nothing to look at again and works
 * with the phone in aeroplane mode. It describes rather than advises: "sugar is the second
 * ingredient" is a fact about the pack, and what to do about that is not the app's business.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientAnalysis(flags: List<IngredientFlag>, verdict: String?) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Eyebrow("What's in it")

            if (flags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    flags.forEach { flag ->
                        Pill(
                            text = flag.detail
                                ?.takeIf { it.isNotBlank() }
                                ?.let { "${flag.label} · $it" }
                                ?: flag.label,
                            tone = when (flag.tone) {
                                FlagTone.CONCERN -> PillTone.Warning
                                FlagTone.FINE -> PillTone.Accent
                                FlagTone.NOTE -> PillTone.Quiet
                            },
                            // A tone that is only a colour fails for anyone who cannot tell
                            // these hues apart, so the two that mean something carry a glyph.
                            icon = when (flag.tone) {
                                FlagTone.CONCERN -> Icons.Outlined.ErrorOutline
                                FlagTone.FINE -> Icons.Outlined.Verified
                                FlagTone.NOTE -> null
                            },
                        )
                    }
                }
            }

            verdict?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
