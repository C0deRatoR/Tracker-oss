package com.yash.tracker.ui.recognition

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.tracker.data.photo.ProcessedPhoto
import com.yash.tracker.data.repository.DraftItem
import com.yash.tracker.data.repository.DraftMeal
import com.yash.tracker.data.local.entity.ProductEntity
import com.yash.tracker.ui.products.ProductEditorHost
import com.yash.tracker.ui.products.ProductsViewModel
import com.yash.tracker.domain.diary.DiaryDate
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.nutrition.Macros
import com.yash.tracker.domain.nutrition.MealReviewer
import com.yash.tracker.domain.nutrition.PortionChoice
import com.yash.tracker.ui.components.ActionTone
import com.yash.tracker.ui.components.CameraCapture
import com.yash.tracker.ui.components.CircleIconButton
import com.yash.tracker.ui.components.DetailTopBar
import com.yash.tracker.ui.components.EmptyNote
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.Hairline
import com.yash.tracker.ui.components.IconPlate
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.LuxTextField
import com.yash.tracker.ui.components.MealReviewCard
import com.yash.tracker.ui.components.Metric
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillButton
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SearchField
import com.yash.tracker.ui.components.SelectableChip
import com.yash.tracker.ui.components.SmallAction
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.SplitBar
import com.yash.tracker.ui.components.StaggerIn
import com.yash.tracker.ui.components.Stepper
import com.yash.tracker.ui.components.TextAction
import com.yash.tracker.ui.theme.EyebrowStyle
import com.yash.tracker.ui.theme.Motion
import com.yash.tracker.ui.theme.springClick
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM")

@Composable
fun RecognitionScreen(
    onDone: () -> Unit,
    entryId: Long? = null,
    date: String? = null,
    viewModel: RecognitionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing = state.editingEntryId != null

    // Reached with an entry id: load that entry instead of asking what was eaten.
    LaunchedEffect(entryId) { entryId?.let(viewModel::editEntry) }
    // Reached with a past day picked on the dashboard: the new entry lands there, not on today.
    LaunchedEffect(date) { date?.let { viewModel.setLogDate(DiaryDate.parse(it)) } }

    var capturing by rememberSaveable { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::recognizePhoto) }

    if (capturing) {
        val target = remember { viewModel.newCaptureTarget().second }
        CameraCapture(
            target = target,
            onCaptured = { uri ->
                capturing = false
                viewModel.recognizePhoto(uri)
            },
            onCancel = { capturing = false },
            title = "Photograph the plate",
            hint = "Fill the frame from above. Nothing is saved until you have checked it.",
        )
        return
    }

    when (val step = state.step) {
        RecognitionStep.Input -> InputStep(
            text = state.text,
            logDate = state.logDate,
            onText = viewModel::setText,
            onGo = viewModel::recognize,
            onCancel = onDone,
            onCamera = { capturing = true },
            onGallery = {
                pickPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
        RecognitionStep.Working -> WorkingStep()
        is RecognitionStep.Describe -> DescribeStep(
            photo = step.photo,
            reading = step.returnTo,
            text = state.text,
            onText = viewModel::setText,
            onIdentify = viewModel::identifyPhoto,
            onRetake = {
                viewModel.discardPhoto()
                capturing = true
            },
            onCancel = viewModel::cancelDescribe,
        )
        is RecognitionStep.Confirm -> {
            ConfirmStep(
                draft = step.draft,
                saving = state.saving,
                editing = editing,
                logDate = state.logDate,
                units = state.units,
                // Only a photo can be read again; a typed meal is already the user's own words.
                canDescribe = !editing && state.photo != null,
                onDescribe = viewModel::describePhoto,
                onBack = onDone,
                // Backing out of an edit leaves the saved entry exactly as it was; backing out
                // of a new reading throws the reading away and returns to the input step.
                onDiscard = if (editing) onDone else viewModel::discard,
                viewModel = viewModel,
            )
            state.productPicker?.let { ProductPickerSheet(it, viewModel) }

            state.awaitingProductFor?.let { itemId ->
                AddProductSheet(
                    prefillName = viewModel.nameOf(itemId),
                    onAdded = viewModel::applyNewProduct,
                    onCancel = viewModel::cancelNewProduct,
                )
            }
        }
        is RecognitionStep.Problem -> ProblemStep(step.message, viewModel::reset, onDone)
        RecognitionStep.NothingFound -> ProblemStep(
            "I couldn't find any food in that.",
            viewModel::reset,
            onDone,
        )
        is RecognitionStep.Saved -> SavedStep(step.kcal, step.edited) {
            viewModel.reset()
            onDone()
        }
    }
}

// --- input ----------------------------------------------------------------------------------

@Composable
private fun InputStep(
    text: String,
    logDate: LocalDate?,
    onText: (String) -> Unit,
    onGo: () -> Unit,
    onCancel: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailTopBar(
            title = logDate?.let { "Log for ${it.format(LOG_DATE_FORMAT)}" } ?: "Log a meal",
            onBack = onCancel,
        )

        // Scrolls rather than squeezes: with the keyboard up there is not room for the whole
        // form, and the one control that must never be off-screen is the button.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp),
        ) {
            StaggerIn(0) {
                Column {
                    Eyebrow("Intake")
                    Spacer(Modifier.height(4.dp))
                    Text("What did you eat?", style = MaterialTheme.typography.headlineLarge)
                }
            }

            Spacer(Modifier.height(22.dp))
            StaggerIn(1) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(
                        text = "Photograph it",
                        onClick = onCamera,
                        leadingIcon = Icons.Outlined.CameraAlt,
                        modifier = Modifier.weight(1f),
                    )
                    PillButton(
                        text = "Gallery",
                        onClick = onGallery,
                        tone = ActionTone.Soft,
                        leadingIcon = Icons.Outlined.PhotoLibrary,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            StaggerIn(2) {
                Column {
                    Eyebrow("Or describe it")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Plain words are fine — \"2 roti, dal and a katori of sabji\". " +
                            "Typed here, it rides along as a hint with a photo too.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    LuxTextField(
                        value = text,
                        onValueChange = onText,
                        placeholder = "Your meal",
                        singleLine = false,
                        minLines = 4,
                        shape = MaterialTheme.shapes.large,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            PillButton(
                text = "Read it",
                onClick = onGo,
                enabled = text.isNotBlank(),
                trailingIcon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Between the shutter and the model: the shot, and a box to say what is in it.
 *
 * The two halves of the question have different best answers. What the food is, the user knows
 * and the camera can only guess at — white liquid is milk or curd or coconut milk depending on
 * a kitchen the photo does not show. How much of it there is, the photo reads better than
 * anyone types: bowl size, depth, how much of the plate it covers.
 *
 * So this asks only for names and leaves the weights to the image.
 *
 * It is reached only after a reading the user has seen and disagreed with, which is why it can
 * show what was read: correcting "muesli with milk" is a far easier act than describing a bowl
 * from nothing, and every reading that was right never has to come here at all.
 */
@Composable
private fun DescribeStep(
    photo: ProcessedPhoto,
    reading: DraftMeal?,
    text: String,
    onText: (String) -> Unit,
    onIdentify: () -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailTopBar(title = "Put it right", onBack = onCancel)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp),
        ) {
            StaggerIn(0) { CapturePreview(photo) }


            reading?.let { misread ->
                Spacer(Modifier.height(16.dp))
                StaggerIn(1) {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Eyebrow("It read this as")
                            Spacer(Modifier.height(6.dp))
                            Text(
                                misread.items.joinToString(", ") { it.name }
                                    .ifBlank { "nothing it could name" },
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            StaggerIn(2) {
                Column {
                    Eyebrow("Tell it what this actually is")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Just the names \u2014 \"oats, milk, banana\". What you write is taken " +
                            "as what the food is; the photo is used to work out how much of " +
                            "each is there. No need to guess at weights.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    LuxTextField(
                        value = text,
                        onValueChange = onText,
                        placeholder = "oats, milk, banana",
                        singleLine = false,
                        minLines = 3,
                        shape = MaterialTheme.shapes.large,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            PillButton(
                text = "Read it again",
                onClick = onIdentify,
                // Reading it again with nothing added would only return the same answer.
                enabled = text.isNotBlank(),
                trailingIcon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextAction("Retake the photo", onClick = onRetake)
            }
        }
    }
}

/**
 * The shot, as the model will see it — already rotated and scaled to the 1024 px it is sent at.
 * Decoded from the bytes in hand rather than re-read from disk, because nothing has been
 * written to disk yet and nothing will be unless the meal is saved.
 */
@Composable
private fun CapturePreview(photo: ProcessedPhoto) {
    var bitmap by remember(photo) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photo) {
        bitmap = withContext(Dispatchers.Default) {
            BitmapFactory
                .decodeByteArray(photo.jpeg, 0, photo.jpeg.size, BitmapFactory.Options().apply {
                    inSampleSize = 2
                })
                ?.asImageBitmap()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "The photo you just took",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The wait. A pulsing plate rather than a spinner: it is the same object that comes back. */
@Composable
private fun WorkingStep() {
    val pulse = rememberInfiniteTransition(label = "reading")
    val scale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "readingPulse",
    )
    val glow by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "readingGlow",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = glow
                    }
                    // Neutral, not the accent: against a dark viewfinder hand-off a saturated
                    // disc reads as a coin sitting behind the plate rather than as a glow.
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            IconPlate(Icons.Outlined.Restaurant, size = 72.dp, tone = PillTone.Solid)
        }
        Spacer(Modifier.height(26.dp))
        Eyebrow("Intake AI")
        Spacer(Modifier.height(6.dp))
        Text("Reading your meal…", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Nothing is saved until you have checked it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- confirm --------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfirmStep(
    draft: DraftMeal,
    saving: Boolean,
    editing: Boolean,
    logDate: LocalDate?,
    units: List<PortionChoice>,
    canDescribe: Boolean,
    onDescribe: () -> Unit,
    onBack: () -> Unit,
    onDiscard: () -> Unit,
    viewModel: RecognitionViewModel,
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailTopBar(
            title = when {
                editing -> "Edit entry"
                logDate != null -> "Plate detail · ${logDate.format(LOG_DATE_FORMAT)}"
                else -> "Plate detail"
            },
            onBack = onBack,
            actions = { Spacer(Modifier.width(40.dp)) },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Studio ledger · Intake AI")
                    Spacer(Modifier.weight(1f))
                    Eyebrow(
                        when {
                            editing -> "Saved entry"
                            draft.fromCache -> "No AI call"
                            else -> "New reading"
                        },
                    )
                }
            }

            item {
                StaggerIn(0) {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconPlate(Icons.Outlined.Restaurant, size = 52.dp, tone = PillTone.Solid)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (editing) "What you logged" else "Plate identification",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(4.dp))
                                Pill(
                                    "${draft.items.size} " +
                                        if (editing) {
                                            "${if (draft.items.size == 1) "item" else "items"}"
                                        } else {
                                            "${if (draft.items.size == 1) "item" else "items"} detected"
                                        },
                                    tone = PillTone.Accent,
                                    icon = Icons.Outlined.AutoAwesome,
                                )
                            }
                            if (!editing) SmallAction("Retake", onClick = viewModel::discard)
                        }
                    }
                }
            }

            // Offered against a reading rather than before one: most photos come back right,
            // and those never have to answer for themselves.
            if (canDescribe) {
                item {
                    StaggerIn(1) {
                        SoftCard(Modifier.fillMaxWidth(), onClick = onDescribe) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Not what you ate?",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Say what it was and read the photo again.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { StaggerIn(1) { TotalsCard(draft) } }

            // Under the totals, above the rows: the judgement is about the plate as a whole,
            // and it is only worth showing while it can still change what gets logged.
            val review = MealReviewer.review(
                macros = Macros(
                    kcal = draft.totalKcal,
                    proteinG = draft.totalProtein,
                    carbsG = draft.totalCarbs,
                    fatG = draft.totalFat,
                ),
                micros = draft.totalMicros,
                microKcal = draft.microKcal,
            )
            if (review.isWorthShowing) {
                item { StaggerIn(1) { MealReviewCard(review, Modifier.fillMaxWidth()) } }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Items")
                    Spacer(Modifier.weight(1f))
                    Eyebrow("Tap a row to correct it")
                }
            }

            items(draft.items, key = { it.id }) { item ->
                ItemCard(item, units, Modifier.animateItem(), viewModel)
            }

            item {
                SoftCard(Modifier.fillMaxWidth(), onClick = viewModel::addBlankItem) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Add an item it missed",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                LuxCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Eyebrow("Meal slot")
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MealType.entries.forEach { meal ->
                                SelectableChip(
                                    label = meal.name.lowercase().replaceFirstChar(Char::uppercase),
                                    selected = meal == draft.mealType,
                                    onClick = { viewModel.setMealType(meal) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // The running total sits above the buttons so cause and effect stay visible.
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 18.dp),
        ) {
            Hairline()
            Spacer(Modifier.height(14.dp))
            PillButton(
                text = when {
                    saving -> "Saving…"
                    editing -> "Save changes (${draft.totalKcal.roundToInt()} kcal)"
                    else -> "Confirm & log (${draft.totalKcal.roundToInt()} kcal)"
                },
                onClick = viewModel::save,
                enabled = !saving && draft.items.isNotEmpty(),
                leadingIcon = Icons.Default.Check,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextAction(
                    if (editing) "Discard changes" else "Discard plate record",
                    onClick = onDiscard,
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(draft: DraftMeal) {
    val scheme = MaterialTheme.colorScheme

    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Estimated total")
                Spacer(Modifier.weight(1f))
                Eyebrow("Provisional")
            }
            Spacer(Modifier.height(8.dp))
            Metric("${draft.totalKcal.roundToInt()}", unit = "kcal")

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                MacroLabel("Protein", draft.totalProtein)
                Spacer(Modifier.width(18.dp))
                MacroLabel("Carbs", draft.totalCarbs)
                Spacer(Modifier.width(18.dp))
                MacroLabel("Fat", draft.totalFat)
            }

            Spacer(Modifier.height(12.dp))
            // Weighted by the energy each macro actually carries, so the bar shows where the
            // calories came from rather than which macro happens to weigh most.
            SplitBar(
                parts = listOf(
                    (draft.totalProtein * 4).toFloat() to scheme.onSurface,
                    (draft.totalCarbs * 4).toFloat() to scheme.onSurfaceVariant,
                    (draft.totalFat * 9).toFloat() to scheme.outline,
                ),
                height = 7.dp,
            )
        }
    }
}

@Composable
private fun MacroLabel(label: String, grams: Double) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            label.uppercase(),
            style = EyebrowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text("${grams.roundToInt()}g", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ItemCard(
    item: DraftItem,
    units: List<PortionChoice>,
    modifier: Modifier,
    viewModel: RecognitionViewModel,
) {
    // Low-confidence rows open by default so the thing needing attention is already visible.
    var expanded by remember(item.id) { mutableStateOf(item.needsAttention) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.settle,
        label = "itemChevron",
    )

    LuxCard(modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .springClick(pressedScale = 0.99f) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.name.ifBlank { "New item" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        ConfidenceChip(item)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${trim(item.quantity)} ${item.unit} · ${item.grams.roundToInt()} g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${item.kcal.roundToInt()}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Swapping the row for the right food is the correction people reach for most,
                // so it sits on the closed row: buried one expansion down, it went unfound.
                CircleIconButton(
                    icon = Icons.Outlined.SwapHoriz,
                    contentDescription = "Choose the food for ${item.name.ifBlank { "this row" }}",
                    onClick = { viewModel.openProductPicker(item.id) },
                    size = 36.dp,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(20.dp)
                        .rotate(chevron),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(200)) + expandVertically(tween(280)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(220)),
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    SoftCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(12.dp)) {
                            Eyebrow("Portion")

                            // Counting comes first: portions are thought of as servings, and
                            // only ever converted to grams because a database wants them that
                            // way. A row already measured by weight or volume would show the
                            // same stepper twice, so it shows only the weight.
                            if (item.unit.lowercase() !in WEIGHT_UNITS) {
                                Spacer(Modifier.height(8.dp))
                                Stepper(
                                    value = trim(item.quantity),
                                    unit = item.unit,
                                    label = item.unit,
                                    onDecrement = {
                                        viewModel.setItemQuantity(
                                            item.id,
                                            (item.quantity - QUANTITY_STEP)
                                                .coerceAtLeast(QUANTITY_STEP),
                                        )
                                    },
                                    onIncrement = {
                                        viewModel.setItemQuantity(
                                            item.id,
                                            item.quantity + QUANTITY_STEP,
                                        )
                                    },
                                )
                            }

                            Spacer(Modifier.height(8.dp))
                            Stepper(
                                value = "${item.grams.roundToInt()}",
                                unit = "g",
                                label = "grams",
                                onDecrement = {
                                    viewModel.setItemGrams(
                                        item.id,
                                        (item.grams - GRAM_STEP).coerceAtLeast(0.0),
                                    )
                                },
                                onIncrement = {
                                    viewModel.setItemGrams(item.id, item.grams + GRAM_STEP)
                                },
                            )

                            if (units.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Eyebrow("Count it in")
                                Spacer(Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    units.forEach { choice ->
                                        SelectableChip(
                                            label = choice.label,
                                            selected = item.unit.equals(
                                                choice.label,
                                                ignoreCase = true,
                                            ),
                                            onClick = { viewModel.setItemUnit(item.id, choice) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    LuxTextField(
                        value = item.name,
                        onValueChange = { viewModel.setItemName(item.id, it) },
                        label = "Name",
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField("kcal", item.kcal, Modifier.weight(1f)) {
                            viewModel.setItemKcal(item.id, it)
                        }
                        DecimalField("Protein", item.proteinG, Modifier.weight(1f)) {
                            viewModel.setItemProtein(item.id, it)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField("Carbs", item.carbsG, Modifier.weight(1f)) {
                            viewModel.setItemCarbs(item.id, it)
                        }
                        DecimalField("Fat", item.fatG, Modifier.weight(1f)) {
                            viewModel.setItemFat(item.id, it)
                        }
                    }

                    item.sourceNote?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SmallAction(
                            text = if (item.isExact) "Use a different food" else "Swap product",
                            icon = Icons.Outlined.SwapHoriz,
                            onClick = { viewModel.openProductPicker(item.id) },
                        )
                        Spacer(Modifier.weight(1f))
                        CircleIconButton(
                            icon = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove ${item.name}",
                            onClick = { viewModel.removeItem(item.id) },
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Confidence carries a shape and a word as well as a colour, because colour alone fails for
 * anyone who cannot distinguish these hues (DESIGN-PRD §11). A product's numbers came off a
 * packet, and that is a different kind of claim from a guess — so it gets a different mark.
 */
@Composable
private fun ConfidenceChip(item: DraftItem) {
    val confidence = item.confidence

    when {
        item.isExact -> Pill(
            "Exact",
            tone = PillTone.Accent,
            icon = Icons.Outlined.Verified,
        )
        item.edited -> Pill("Edited", tone = PillTone.Quiet)
        confidence == null -> Pill("Est.", tone = PillTone.Quiet)
        confidence >= 0.8 -> Pill("Confident", tone = PillTone.Quiet, leadingDot = true)
        confidence >= 0.5 -> Pill("Fairly sure", tone = PillTone.Warning, leadingDot = true)
        else -> Pill("Verify portion", tone = PillTone.Danger, leadingDot = true)
    }
}

@Composable
private fun DecimalField(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    onChange: (Double) -> Unit,
) {
    var text by remember(value) { mutableStateOf(trim(value)) }
    LuxTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter { it.isDigit() || it == '.' }
            text.toDoubleOrNull()?.let(onChange)
        },
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

// --- outcomes -------------------------------------------------------------------------------

@Composable
private fun ProblemStep(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconPlate(Icons.Outlined.Restaurant, size = 64.dp, tone = PillTone.Solid)
        Spacer(Modifier.height(22.dp))
        Text(
            message,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        PillButton(
            text = "Try again",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PillButton(
            text = "Log by hand",
            onClick = onCancel,
            tone = ActionTone.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SavedStep(kcal: Int, edited: Boolean, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.inverseSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Eyebrow(if (edited) "Updated" else "Logged")
        Spacer(Modifier.height(6.dp))
        Metric("$kcal", unit = if (edited) "kcal for that entry" else "kcal added to today")
        Spacer(Modifier.height(30.dp))
        PillButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

private fun trim(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private const val GRAM_STEP = 10.0

/** Half a roti and half a katori are both real portions; a whole-number step is not enough. */
private const val QUANTITY_STEP = 0.5

/** Units that already count in the same tens the gram stepper does. */
private val WEIGHT_UNITS = setOf("g", "ml")

/**
 * The product form, raised from the confirm screen and reporting back what it wrote.
 *
 * It borrows the products screen's own editor and view model rather than growing a second one:
 * the label camera, the panel parsing and the validation are all the same job here, and the
 * only thing this adds is telling the row which product it ended up with.
 */
@Composable
private fun AddProductSheet(
    prefillName: String,
    onAdded: (ProductEntity) -> Unit,
    onCancel: () -> Unit,
    products: ProductsViewModel = hiltViewModel(),
) {
    val state by products.state.collectAsStateWithLifecycle()
    var opened by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        products.addNew(prefillName)
        opened = true
    }

    LaunchedEffect(state.lastSaved) {
        state.lastSaved?.let {
            products.consumeSaved()
            onAdded(it)
        }
    }

    // Dismissed rather than saved: the editor closed, nothing was written, and the row should
    // go back to what it was. Guarded on lastSaved so a save is not read as a cancellation.
    LaunchedEffect(opened, state.editing, state.capturing, state.lastSaved) {
        if (opened && state.editing == null && !state.capturing && state.lastSaved == null) {
            onCancel()
        }
    }

    ProductEditorHost(products)
}

/**
 * Picking which food — the user's own product, or one from the bundled catalogue — a
 * recognised row actually was.
 *
 * Opens showing everything rather than an empty search box: someone owns a handful of
 * products, not a catalogue, so browsing beats typing until a query narrows things down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductPickerSheet(picker: ProductPicker, viewModel: RecognitionViewModel) {
    ModalBottomSheet(
        onDismissRequest = viewModel::closeProductPicker,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Eyebrow("Correction")
            Spacer(Modifier.height(4.dp))
            Text("Which food?", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Replaces the estimate with the right numbers, at the weight already on the row.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))
            SearchField(
                value = picker.query,
                onValueChange = viewModel::searchProducts,
                placeholder = "Your products and the catalogue",
            )
            Spacer(Modifier.height(10.dp))

            if (picker.results.isEmpty()) {
                EmptyNote(
                    if (picker.query.isBlank()) {
                        "You haven't added any products yet. Type to search the catalogue."
                    } else {
                        "Nothing matches that."
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(picker.results, key = { it.key }) { hit ->
                        SoftCard(
                            Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            onClick = { viewModel.pickHit(hit) },
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        hit.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
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
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Eyebrow("per 100 g")
                                }
                            }
                        }
                    }
                }
            }

            // The food that is on neither the shelf nor the catalogue is the one worth
            // describing, so the box already holding what the user typed becomes the
            // description rather than asking them to type it a second time somewhere else.
            if (picker.query.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                SoftCard(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = viewModel::describeItem,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (picker.describing) "Reading it…" else "Describe it instead",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                picker.describeError
                                    ?: "Let the AI read \"${picker.query.trim()}\" and fill the row in.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (picker.describeError != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            // Offered whether or not anything matched: the shelf is small, and the usual
            // reason a food is not on it is that it has never been added.
            Spacer(Modifier.height(12.dp))
            SoftCard(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                onClick = { viewModel.addProductFor(picker.itemId) },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "It's mine — add it as a product",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Photograph the packet and the numbers fill themselves in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextAction("Cancel", onClick = viewModel::closeProductPicker)
            }
        }
    }
}
