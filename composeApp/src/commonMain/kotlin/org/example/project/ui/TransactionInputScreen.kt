package org.example.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.config.SchemaFeatures
import org.example.project.domain.transaction.TransactionFormEffect
import org.example.project.domain.transaction.TransactionFormEvent
import org.example.project.domain.transaction.VoiceAiUsage
import org.example.project.model.PaymentMode
import org.example.project.util.DateUtils
import org.example.project.ui.components.BounceSurface
import org.example.project.ui.components.CategoryGlyph
import org.example.project.ui.components.CategoryGlyphKind
import org.example.project.ui.components.categoryGlyphKind
import org.example.project.ui.components.DatePickerDialog
import org.example.project.ui.components.PaymentBadge
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.ui.theme.AppShapes
import org.example.project.ui.theme.ExpenseTerracotta
import org.example.project.ui.theme.IncomeGreen
import org.example.project.viewmodel.TransactionViewModel
import org.example.project.voice.VoiceStatus
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.TextFieldColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionInputScreen(
    viewModel: TransactionViewModel,
    modifier: Modifier = Modifier,
) {
    val formState by viewModel.formState.collectAsState()
    val schemaFeatures = remember { SchemaFeatures.current() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val amountFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Briefly flips true when a save succeeds so the CTA can confirm with a checkmark
    // before the cleared form settles back to its disabled resting state.
    var saveSuccess by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Open keyboard on the amount field immediately — no extra tap needed.
    LaunchedEffect(Unit) {
        delay(700)
        amountFocusRequester.requestFocus()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TransactionFormEffect.FormCleared -> {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
                is TransactionFormEffect.ShowSuccess -> {
                    saveSuccess = true
                    scope.launch {
                        delay(1300)
                        saveSuccess = false
                    }
                }
                is TransactionFormEffect.ShowError -> Unit
            }
        }
    }

    // Emotional anchor color — drives the amount tint, segmented indicator, save CTA.
    val accentColor by animateColorAsState(
        targetValue = if (formState.isIncome) IncomeGreen else ExpenseTerracotta,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accentColor",
    )

    // Stable event handlers: hoisted so child composables don't recompose on every keystroke.
    val onTypeChanged: (Boolean) -> Unit =
        remember(viewModel) { { viewModel.onEvent(TransactionFormEvent.TransactionTypeChanged(it)) } }
    val onAmountChanged: (String) -> Unit =
        remember(viewModel) { { viewModel.onEvent(TransactionFormEvent.AmountChanged(it)) } }
    val onDescriptionChanged: (String) -> Unit =
        remember(viewModel) { { viewModel.onEvent(TransactionFormEvent.DescriptionChanged(it)) } }
    val onCategoryToggle: () -> Unit =
        remember(viewModel) { { viewModel.onEvent(TransactionFormEvent.CategoryDropdownToggled) } }
    val onPaymentToggle: () -> Unit =
        remember(viewModel) { { viewModel.onEvent(TransactionFormEvent.PaymentDropdownToggled) } }
    val onCategorySelect: (String) -> Unit =
        remember(viewModel) { { viewModel.onEvent(TransactionFormEvent.CategorySelected(it)) } }
    val onPaymentSelect: (String) -> Unit =
        remember(viewModel) { { viewModel.onEvent(TransactionFormEvent.PaymentModeSelected(it)) } }
    val onPaidChanged: (Boolean) -> Unit =
        remember(viewModel) { { viewModel.onEvent(TransactionFormEvent.IsPaidChanged(it)) } }
    val onDateSelected: (String) -> Unit =
        remember(viewModel) { { viewModel.onEvent(TransactionFormEvent.DateChanged(it)) } }
    val onVoiceToggle: () -> Unit =
        remember(viewModel) { { viewModel.onEvent(TransactionFormEvent.VoiceInputToggled) } }
    val onImeNext: () -> Unit =
        remember(focusManager) { { focusManager.moveFocus(FocusDirection.Down) } }
    val onSubmit: () -> Unit = remember(viewModel, focusManager, keyboardController) {
        {
            keyboardController?.hide()
            focusManager.clearFocus()
            viewModel.onEvent(TransactionFormEvent.FormSubmitted)
        }
    }

    // Pull down from the top to wipe the form back to defaults — a quick "start over" without
    // hunting for each field's clear button. The threshold guards against accidental triggers.
    var isClearing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isClearing,
        onRefresh = {
            viewModel.onEvent(TransactionFormEvent.ClearForm)
            // The reset is instant; hold the indicator briefly so the gesture reads as a deliberate action.
            scope.launch {
                isClearing = true
                delay(350)
                isClearing = false
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Add Transaction",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        // 1. Type decision first — it changes what the amount means.
        if (schemaFeatures.showIncomeOption) {
            TransactionTypeSegmented(
                isIncome = formState.isIncome,
                isEnabled = !formState.isLoading,
                accentColor = accentColor,
                onTypeChanged = onTypeChanged,
            )
        }

        // 2. Hero amount field — large typography, tinted prefix follows the type.
        HeroAmountField(
            amount = formState.amount,
            isEnabled = !formState.isLoading,
            accentColor = accentColor,
            focusRequester = amountFocusRequester,
            onAmountChanged = onAmountChanged,
            onImeNext = onImeNext,
        )

        val descriptionBounce = rememberPressBounce(pressedScale = 0.98f)
        val isListening = formState.voiceStatus == VoiceStatus.Listening
        val descriptionFieldColors = fieldColors()
        OutlinedTextField(
            value = formState.description,
            onValueChange = onDescriptionChanged,
            label = { Text("Description") },
            placeholder = {
                Text(if (isListening) "Listening… speak now" else "e.g. Groceries at SM")
            },
            // Voice mic lives on the leading edge — "speak it" affordance, distinct from the
            // trailing clear action. Hidden where the platform has no speech engine.
            leadingIcon = if (formState.isVoiceSupported) {
                {
                    DescriptionMicButton(
                        status = formState.voiceStatus,
                        isEnabled = !formState.isLoading,
                        accentColor = accentColor,
                        onClick = onVoiceToggle,
                    )
                }
            } else null,
            trailingIcon = if (formState.description.isNotEmpty()) {
                {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(enabled = !formState.isLoading) {
                                onDescriptionChanged("")
                            }
                            .semantics {
                                role = Role.Button
                                contentDescription = "Clear description"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(AppShapes.pill)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "×",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth().then(descriptionBounce.modifier),
            enabled = !formState.isLoading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onImeNext() }),
            colors = descriptionFieldColors,
            shape = AppShapes.field,
            interactionSource = descriptionBounce.interactionSource,
            singleLine = true,
        )

        // Per-add token readout — shows whether the last mic add spent a Gemini round-trip.
        // Retain the last value so the exit animation has content to fade/collapse out.
        val lastVoiceUsage = remember { mutableStateOf<VoiceAiUsage?>(null) }
        formState.voiceAiUsage?.let { lastVoiceUsage.value = it }
        AnimatedVisibility(
            visible = formState.voiceAiUsage != null,
            enter = fadeIn(tween(200)) +
                expandVertically(tween(220, easing = EaseOutQuart)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
        ) {
            lastVoiceUsage.value?.let { usage ->
                VoiceUsageInfo(usage = usage, accentColor = accentColor)
            }
        }

        // Date — defaults to today; tap to log a receipt from another day.
        DateField(
            dateText = formState.selectedDate,
            isEnabled = !formState.isLoading,
            accentColor = accentColor,
            onClick = { showDatePicker = true },
        )

        // Income uses its own short source list (e.g. Renz/Gen); expense uses the full category set.
        val useIncomeCategories = schemaFeatures.showIncomeOption && formState.isIncome
        val categoryPickerTitle =
            if (useIncomeCategories) schemaFeatures.incomeCategoryPickerTitle else schemaFeatures.categoryPickerTitle
        val categoryOptions =
            if (useIncomeCategories) schemaFeatures.incomeCategoryOptions else schemaFeatures.categoryOptions
        val paymentOptions = remember { PaymentMode.entries.map { it.displayName } }

        // Category + Payment are the same kind of decision (how to file this transaction),
        // so they sit together as a two-up row — chevron-less tiles matching the Date tile above.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChoiceField(
                label = schemaFeatures.categoryLabel,
                placeholder = "Select ${schemaFeatures.categoryLabel.lowercase()}",
                selected = formState.selectedCategory,
                isExpanded = formState.showCategoryDropdown,
                isEnabled = !formState.isLoading,
                accentColor = accentColor,
                onToggle = onCategoryToggle,
                leadingGlyph = categoryGlyphKind(formState.selectedCategory),
                showChevron = false,
                modifier = Modifier.weight(1f),
            )
            ChoiceField(
                label = "Payment",
                placeholder = "How paid?",
                selected = formState.selectedPaymentMode,
                isExpanded = formState.showPaymentDropdown,
                isEnabled = !formState.isLoading,
                accentColor = accentColor,
                onToggle = onPaymentToggle,
                leading = { PaymentBadge(name = formState.selectedPaymentMode) },
                showChevron = false,
                modifier = Modifier.weight(1f),
            )
        }

        if (formState.showCategoryDropdown) {
            ChoicePickerSheet(
                title = categoryPickerTitle,
                options = categoryOptions,
                selected = formState.selectedCategory,
                accentColor = accentColor,
                onDismiss = onCategoryToggle,
                onSelect = onCategorySelect,
                showGlyphs = true,
            )
        }

        if (formState.showPaymentDropdown) {
            ChoicePickerSheet(
                title = "Pick a payment mode",
                options = paymentOptions,
                selected = formState.selectedPaymentMode,
                accentColor = accentColor,
                onDismiss = onPaymentToggle,
                onSelect = onPaymentSelect,
                showPaymentBadges = true,
            )
        }

        if (schemaFeatures.showPaidToggle) {
            PaidToggleRow(
                isPaid = formState.isPaid,
                isEnabled = !formState.isLoading,
                accentColor = accentColor,
                onPaidChanged = onPaidChanged,
            )
        }

        SaveButton(
            isLoading = formState.isLoading,
            isEnabled = formState.isValid && !formState.isLoading,
            isSuccess = saveSuccess,
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    }

    if (showDatePicker) {
        DatePickerDialog(
            currentDate = formState.selectedDate,
            onDateSelected = onDateSelected,
            onDismiss = { showDatePicker = false },
        )
    }
}

/**
 * Compact mic control for the Description field's leading slot.
 *
 * States — idle: outlined mic (tap to dictate); listening: accent-filled mic with a breathing
 * pulse ring (tap to stop); processing: spinner while the transcript is parsed/AI-classified.
 * 48dp tap target satisfies the Material accessibility minimum; semantics announce the live state.
 */
@Composable
private fun DescriptionMicButton(
    status: VoiceStatus,
    isEnabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val isListening = status == VoiceStatus.Listening
    val isProcessing = status == VoiceStatus.Processing

    val pulse = rememberInfiniteTransition(label = "voicePulse")
    val pulseScaleState = pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Restart),
        label = "voicePulseScale",
    )
    val pulseAlphaState = pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Restart),
        label = "voicePulseAlpha",
    )

    val circleColor by animateColorAsState(
        targetValue = if (isListening) accentColor else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "micCircle",
    )

    // The whole control swells when armed — an unmistakable "I'm on now" cue that
    // settles back the instant you tap it off. Visual only; the 48dp hit area is unchanged.
    val emphasisScale by animateFloatAsState(
        targetValue = if (isListening) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "micEmphasisScale",
    )

    val stateLabel = when (status) {
        VoiceStatus.Idle -> "Tap to talk"
        VoiceStatus.Listening -> "Listening, tap to stop"
        VoiceStatus.Processing -> "Processing voice"
    }
    val bounce = rememberPressBounce(pressedScale = 0.9f)

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = emphasisScale
                scaleY = emphasisScale
            }
            .clip(AppShapes.pill)
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                enabled = isEnabled && !isProcessing,
                onClick = onClick,
            )
            .then(bounce.modifier)
            .semantics {
                role = Role.Button
                contentDescription = "Add by voice"
                stateDescription = stateLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        // Breathing ring while listening.
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer {
                        scaleX = pulseScaleState.value
                        scaleY = pulseScaleState.value
                        alpha = pulseAlphaState.value
                    }
                    .clip(AppShapes.pill)
                    .background(accentColor),
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(AppShapes.pill)
                .background(circleColor)
                // A crisp accent rim while armed sharpens the filled "on" disc.
                .then(
                    if (isListening) {
                        Modifier.border(1.5.dp, accentColor.copy(alpha = 0.9f), AppShapes.pill)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = accentColor,
                )
            } else {
                MicGlyph(
                    color = if (isListening) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Compact, per-add token readout for voice entry.
 *
 * Three honest states: matched on-device (no AI, zero tokens), an AI round-trip with its provider +
 * model + token breakdown, or an AI call whose token data the provider didn't report. Deliberately
 * understated — a `surfaceContainer` chip in the form's filled language, not a loud banner.
 */
@Composable
private fun VoiceUsageInfo(usage: VoiceAiUsage, accentColor: Color) {
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val label: String
    val dotColor: Color
    when {
        !usage.aiInvoked -> {
            label = "Matched on-device · 0 AI tokens"
            dotColor = onVariant
        }
        usage.provider != null -> {
            val model = usage.model?.let { " $it" } ?: ""
            label = "Voice AI · ${usage.provider}$model · ${usage.totalTokens} tokens " +
                "(prompt ${usage.promptTokens} + reply ${usage.responseTokens})"
            dotColor = accentColor
        }
        else -> {
            label = "Voice AI called · token count unavailable"
            dotColor = accentColor
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Accent dot when tokens were spent, muted when the add stayed on-device.
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(AppShapes.pill)
                .background(dotColor),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = onVariant,
        )
    }
}

@Composable
private fun MicGlyph(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        val bodyW = w * 0.34f
        val cx = w / 2f

        // Capsule body
        drawRoundRect(
            color = color,
            topLeft = Offset(cx - bodyW / 2f, h * 0.08f),
            size = androidx.compose.ui.geometry.Size(bodyW, h * 0.48f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyW / 2f, bodyW / 2f),
        )
        // Cradle arc hugging the lower half of the body
        val cradleW = w * 0.6f
        drawArc(
            color = color,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - cradleW / 2f, h * 0.28f),
            size = androidx.compose.ui.geometry.Size(cradleW, h * 0.42f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Stand + base
        drawLine(
            color = color,
            start = Offset(cx, h * 0.72f),
            end = Offset(cx, h * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(cx - bodyW * 0.7f, h * 0.88f),
            end = Offset(cx + bodyW * 0.7f, h * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun TransactionTypeSegmented(
    isIncome: Boolean,
    isEnabled: Boolean,
    accentColor: Color,
    onTypeChanged: (Boolean) -> Unit,
) {
    var widthPx by remember { mutableStateOf(0) }
    val slotPx = widthPx / 2
    val targetOffsetPx = if (isIncome) slotPx else 0
    val animatedOffsetPx by animateIntAsState(
        targetValue = targetOffsetPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "segmentOffset",
    )
    // Squish the thumb while a finger is down — mirrors the nav pill's press feedback.
    var pressed by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "segmentThumbScale",
    )
    val density = LocalDensity.current
    val slotWidthDp = with(density) { slotPx.toDp() }

    // Read the latest type inside the long-lived gesture loop without restarting it mid-drag.
    val currentIsIncome by rememberUpdatedState(isIncome)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(AppShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .onSizeChanged { widthPx = it.width }
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                contentDescription = "Transaction type"
                stateDescription = if (currentIsIncome) "Income" else "Expense"
                onClick(label = "Switch between expense and income") {
                    onTypeChanged(!currentIsIncome); true
                }
            }
            // Press-and-drag like the nav bar: whichever half the finger is over wins,
            // and crossing the midpoint mid-drag flips the type.
            .pointerInput(isEnabled) {
                if (!isEnabled) return@pointerInput
                awaitEachGesture {
                    val half = size.width / 2f
                    if (half <= 0f) return@awaitEachGesture
                    fun incomeAt(x: Float) = x >= half

                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    var current = currentIsIncome
                    incomeAt(down.position.x).let { if (it != current) { current = it; onTypeChanged(it) } }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val next = incomeAt(change.position.x)
                        if (next != current) {
                            current = next
                            onTypeChanged(next)
                        }
                        change.consume()
                    }
                    pressed = false
                }
            },
    ) {
        if (slotPx > 0) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetPx, 0) }
                    .width(slotWidthDp)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                    .clip(AppShapes.pill)
                    .background(accentColor),
            )
        }
        Row(modifier = Modifier.fillMaxSize()) {
            SegmentLabel(
                text = "Expense",
                isSelected = !isIncome,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SegmentLabel(
                text = "Income",
                isSelected = isIncome,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SegmentLabel(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "segmentTextColor",
    )
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

// Allowed money input: optional digits, an optional single decimal point, up to 2 decimal places.
// Matches the validation in TransactionFormReducer so the field can reject anything it would drop.
private val AmountInputRegex = Regex("^\\d*\\.?\\d{0,2}$")

@Composable
private fun HeroAmountField(
    amount: String,
    isEnabled: Boolean,
    accentColor: Color,
    focusRequester: FocusRequester,
    onAmountChanged: (String) -> Unit,
    onImeNext: () -> Unit,
) {
    val isInvalid = amount.isNotEmpty() && amount.toDoubleOrNull() == null

    // TextFieldValue lets us place the cursor at the end on every external update.
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(text = amount, selection = TextRange(amount.length)))
    }
    // Sync when the amount is cleared externally (form reset).
    LaunchedEffect(amount) {
        if (fieldValue.text != amount) {
            fieldValue = TextFieldValue(text = amount, selection = TextRange(amount.length))
        }
    }

    val errorColor = MaterialTheme.colorScheme.error
    val onSurface = MaterialTheme.colorScheme.onSurface
    val textColor = if (isInvalid) errorColor else onSurface
    val clearColor = MaterialTheme.colorScheme.onSurfaceVariant
    val amountStyle = remember(textColor) {
        TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
    val placeholderStyle = remember(onSurface) {
        TextStyle(
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = onSurface.copy(alpha = 0.25f),
        )
    }
    val phpStyle = MaterialTheme.typography.headlineMedium

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Amount",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.card)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = 24.dp, horizontal = 24.dp),
        ) {
            // PHP + amount centered independently of the clear button
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PHP",
                    style = phpStyle,
                    color = accentColor,
                    modifier = Modifier.padding(end = 10.dp),
                )
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { new ->
                        // Accept numeric input only — digits plus at most one decimal point with up
                        // to 2 places. Anything else (letters, symbols, a second dot, pasted junk) is
                        // rejected outright so the field never displays a non-number. Mirrors
                        // TransactionFormReducer's amount rule, keeping the field and VM in lockstep.
                        if (new.text.length <= 20 &&
                            (new.text.isEmpty() || new.text.matches(AmountInputRegex))
                        ) {
                            fieldValue = new
                            onAmountChanged(new.text)
                        }
                    },
                    textStyle = amountStyle,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { onImeNext() }),
                    modifier = Modifier
                        .widthIn(min = 80.dp)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    enabled = isEnabled,
                    cursorBrush = SolidColor(accentColor),
                    decorationBox = { innerTextField ->
                        Box {
                            if (fieldValue.text.isEmpty()) {
                                Text(text = "0.00", style = placeholderStyle)
                            }
                            innerTextField()
                        }
                    },
                )
            }

            // Clear button — anchored to trailing edge, never shifts the centered amount
            if (fieldValue.text.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(48.dp)
                        .clickable(enabled = isEnabled) {
                            fieldValue = TextFieldValue("")
                            onAmountChanged("")
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Clear amount"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(AppShapes.pill)
                            .background(clearColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "×",
                            style = MaterialTheme.typography.titleMedium,
                            color = clearColor,
                        )
                    }
                }
            }
        }

        if (isInvalid) {
            Text(
                text = "Enter a valid number, e.g. 250.00",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ChoiceField(
    label: String,
    placeholder: String,
    selected: String,
    isExpanded: Boolean,
    isEnabled: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    // When set, a leading icon tile is shown — makes the picked value recognisable at a
    // glance, the way every mainstream tracker tags its categories.
    leadingGlyph: CategoryGlyphKind? = null,
    // Overrides [leadingGlyph] with a custom leading slot (e.g. a payment brand badge).
    leading: (@Composable () -> Unit)? = null,
    // The compact two-up tiles drop the chevron to claw back width for long values.
    showChevron: Boolean = true,
) {
    val bounce = rememberPressBounce(pressedScale = 0.98f)
    val hasValue = selected.isNotBlank()
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "chevronRotation",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) accentColor else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "choiceBorder",
    )
    val containerAlpha by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.5f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "choiceAlpha",
    )

    Row(
        modifier = modifier
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = AppShapes.field,
            )
            .semantics {
                role = Role.Button
                contentDescription = if (selected.isNotBlank()) "$label: $selected" else "$label: $placeholder"
                stateDescription = if (isExpanded) "expanded" else "collapsed"
            }
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                enabled = isEnabled,
                onClick = onToggle,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (hasValue) "$label: $selected" else "$label: $placeholder"
            }
            .then(bounce.modifier)
            .graphicsLayer { alpha = containerAlpha }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) {
            leading()
        } else if (leadingGlyph != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(AppShapes.field)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                CategoryGlyph(
                    kind = leadingGlyph,
                    color = accentColor,
                    size = 20.dp,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isExpanded) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (hasValue) selected else placeholder,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (hasValue) FontWeight.SemiBold else FontWeight.Normal,
                color = if (hasValue) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showChevron) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(AppShapes.pill)
                    .background(
                        if (isExpanded) accentColor.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surface
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val chevronColor =
                    if (isExpanded) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                Canvas(
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                ) {
                    val stroke = 2.dp.toPx()
                    val w = size.width
                    val h = size.height
                    // Downward chevron: left -> bottom-center -> right
                    drawLine(
                        color = chevronColor,
                        start = Offset(w * 0.5f, h * 0.7f),
                        end = Offset(w * 0.08f, h * 0.32f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = chevronColor,
                        start = Offset(w * 0.5f, h * 0.7f),
                        end = Offset(w * 0.92f, h * 0.32f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChoicePickerSheet(
    title: String,
    options: List<String>,
    selected: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    showGlyphs: Boolean = false,
    showPaymentBadges: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AppShapes.card,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    ChoiceChip(
                        text = option,
                        isSelected = option == selected,
                        accentColor = accentColor,
                        onClick = { onSelect(option) },
                        showGlyph = showGlyphs,
                        showPaymentBadge = showPaymentBadges,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(
    text: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    showGlyph: Boolean = false,
    showPaymentBadge: Boolean = false,
) {
    val container by animateColorAsState(
        targetValue = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipContent",
    )
    BounceSurface(
        onClick = onClick,
        shape = AppShapes.pill,
        color = container,
        pressedScale = 0.92f,
        contentPadding = PaddingValues(
            start = if (showGlyph) 12.dp else if (showPaymentBadge) 6.dp else 16.dp,
            end = 16.dp,
            top = if (showPaymentBadge) 6.dp else 10.dp,
            bottom = if (showPaymentBadge) 6.dp else 10.dp,
        ),
        modifier = Modifier.semantics {
            role = Role.Button
            contentDescription = text
            stateDescription = if (isSelected) "selected" else "not selected"
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showPaymentBadge) {
                // The badge keeps its own brand colour + white initials, so it stands on both
                // the resting and the accent-filled (selected) chip without extra treatment.
                PaymentBadge(name = text, size = 26.dp)
            } else if (showGlyph) {
                // Selected chips tint the glyph to the on-accent surface so it reads on the
                // filled pill; unselected chips keep the brand accent for a pop of colour.
                CategoryGlyph(
                    kind = categoryGlyphKind(text),
                    color = content,
                    size = 18.dp,
                )
            }
            Text(
                text = text,
                color = content,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PaidToggleRow(
    isPaid: Boolean,
    isEnabled: Boolean,
    accentColor: Color,
    onPaidChanged: (Boolean) -> Unit,
) {
    val bounce = rememberPressBounce(pressedScale = 0.97f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(
                value = isPaid,
                enabled = isEnabled,
                interactionSource = bounce.interactionSource,
                indication = null,
                onValueChange = onPaidChanged,
            )
            .then(bounce.modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Already paid",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Mark on if money has already left your wallet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = isPaid,
            onCheckedChange = null,
            enabled = isEnabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accentColor,
                checkedThumbColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

@Composable
private fun SaveButton(
    isLoading: Boolean,
    isEnabled: Boolean,
    isSuccess: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounce = rememberPressBounce(pressedScale = 0.96f)
    // A small swell as the checkmark lands — a tactile "it worked" before the form resets.
    val successPop by animateFloatAsState(
        targetValue = if (isSuccess) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "savePop",
    )
    // Confirm-green on success; otherwise the brand golden — predictable regardless of type.
    val containerColor by animateColorAsState(
        targetValue = if (isSuccess) IncomeGreen else MaterialTheme.colorScheme.primary,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "saveContainer",
    )
    Button(
        onClick = { if (!isSuccess) onClick() },
        // Stay "enabled" during the success flash so the confirm-green container shows instead
        // of disabled grey while the cleared form settles underneath.
        enabled = isSuccess || isEnabled,
        modifier = modifier
            .height(56.dp)
            .graphicsLayer { scaleX = successPop; scaleY = successPop }
            .semantics {
                contentDescription = when {
                    isSuccess -> "Transaction saved"
                    isLoading -> "Saving transaction"
                    else -> "Save transaction"
                }
            }
            .then(bounce.modifier),
        // Design system: buttons are pill-shaped. A full-round CTA also matches the
        // primary-action language of trending trackers (Monarch, Revolut).
        shape = AppShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            // Disabled: shape stays visible, text is clearly muted — not invisible
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        interactionSource = bounce.interactionSource,
    ) {
        when {
            isSuccess -> {
                CheckGlyph(color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Saved",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text("Saving…", fontWeight = FontWeight.SemiBold)
            }
            else -> {
                Text(
                    text = "Save Transaction",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun CheckGlyph(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val sw = 2.4.dp.toPx()
        drawLine(color, Offset(w * 0.2f, h * 0.55f), Offset(w * 0.42f, h * 0.74f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, h * 0.74f), Offset(w * 0.8f, h * 0.3f), sw, StrokeCap.Round)
    }
}

@Composable
private fun DateField(
    dateText: String,
    isEnabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val bounce = rememberPressBounce(pressedScale = 0.98f)
    val isToday = remember(dateText) { dateText == DateUtils.getCurrentDateFormatted() }
    val friendly = remember(dateText) { friendlyDate(dateText) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(bounce.modifier)
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                enabled = isEnabled,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Date: $friendly"
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(AppShapes.field)
                .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            CalendarGlyph(color = accentColor)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Date",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = friendly,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (isToday) {
            Box(
                modifier = Modifier
                    .clip(AppShapes.pill)
                    .background(accentColor.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CalendarGlyph(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.7.dp.toPx()
        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = sw, cap = StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.20f),
            size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.70f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f, w * 0.10f),
            style = st,
        )
        drawLine(color, Offset(w * 0.12f, h * 0.40f), Offset(w * 0.88f, h * 0.40f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.34f, h * 0.10f), Offset(w * 0.34f, h * 0.28f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.66f, h * 0.10f), Offset(w * 0.66f, h * 0.28f), sw, StrokeCap.Round)
    }
}

private val MonthAbbrev =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "6/24/2026" → "Jun 24, 2026"; falls back to the raw string if it can't be parsed. */
private fun friendlyDate(raw: String): String {
    val d = DateUtils.parseDate(raw) ?: return raw.ifBlank { "Select a date" }
    val m = MonthAbbrev.getOrElse(d.month.ordinal) { "" }
    return "$m ${d.day}, ${d.year}"
}

@Composable
private fun fieldColors(): TextFieldColors {
    val secondary = MaterialTheme.colorScheme.secondary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val onSurface = MaterialTheme.colorScheme.onSurface
    return remember(secondary, onSurfaceVariant, surfaceContainer, onSurface) {
        OutlinedTextFieldDefaults.colors(
            // Filled surface — the field reads as a tappable "fill me" tile, matching
            // ChoiceField and the hero amount box. One consistent affordance across the form.
            focusedContainerColor = surfaceContainer,
            unfocusedContainerColor = surfaceContainer,
            disabledContainerColor = surfaceContainer,
            // Border stays out of the way at rest (the fill is the affordance) and lights
            // up in the accent only on focus — exactly how ChoiceField marks its active state.
            focusedBorderColor = secondary,
            focusedLabelColor = secondary,
            unfocusedBorderColor = Color.Transparent,
            unfocusedLabelColor = onSurfaceVariant,
            disabledTextColor = onSurface,
            disabledBorderColor = Color.Transparent,
            disabledLeadingIconColor = onSurfaceVariant,
            disabledTrailingIconColor = onSurfaceVariant,
            disabledLabelColor = onSurfaceVariant,
            disabledPlaceholderColor = onSurfaceVariant,
        )
    }
}
