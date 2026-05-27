package org.example.project.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.config.SchemaFeatures
import org.example.project.domain.transaction.TransactionFormEffect
import org.example.project.domain.transaction.TransactionFormEvent
import org.example.project.model.PaymentMode
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.viewmodel.TransactionViewModel

// Semantic income/expense accents, tuned to sit on the earthy brand palette:
// a forest-leaning positive green for inflow, a warm terracotta for outflow.
private val IncomeColor = Color(0xFF2E7D52)
private val ExpenseColor = Color(0xFFC0492B)
private val PillCorner = RoundedCornerShape(50.dp)
private val FieldCorner = RoundedCornerShape(14.dp)

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

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is TransactionFormEffect.FormCleared) {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        }
    }

    // Emotional anchor color — drives the amount tint, segmented indicator, save CTA.
    val accentColor by animateColorAsState(
        targetValue = if (formState.isIncome) IncomeColor else ExpenseColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accentColor",
    )

    Column(
        modifier = modifier
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
                onTypeChanged = { isIncome ->
                    viewModel.onEvent(TransactionFormEvent.TransactionTypeChanged(isIncome))
                },
            )
        }

        // 2. Hero amount field — large typography, tinted prefix follows the type.
        HeroAmountField(
            amount = formState.amount,
            isIncome = formState.isIncome,
            isEnabled = !formState.isLoading,
            accentColor = accentColor,
            onAmountChanged = { viewModel.onEvent(TransactionFormEvent.AmountChanged(it)) },
            onImeNext = { focusManager.moveFocus(FocusDirection.Down) },
        )

        val descriptionBounce = rememberPressBounce(pressedScale = 0.98f)
        OutlinedTextField(
            value = formState.description,
            onValueChange = { viewModel.onEvent(TransactionFormEvent.DescriptionChanged(it)) },
            label = { Text("Description") },
            placeholder = { Text("e.g. Groceries at SM") },
            modifier = Modifier.fillMaxWidth().then(descriptionBounce.modifier),
            enabled = !formState.isLoading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
            colors = fieldColors(),
            shape = FieldCorner,
            interactionSource = descriptionBounce.interactionSource,
            singleLine = true,
        )

        // Income uses its own short source list (e.g. Renz/Gen); expense uses the full category set.
        val useIncomeCategories = schemaFeatures.showIncomeOption && formState.isIncome
        val categoryPickerTitle =
            if (useIncomeCategories) schemaFeatures.incomeCategoryPickerTitle else schemaFeatures.categoryPickerTitle
        val categoryOptions =
            if (useIncomeCategories) schemaFeatures.incomeCategoryOptions else schemaFeatures.categoryOptions

        ChoiceField(
            label = schemaFeatures.categoryLabel,
            placeholder = "Select ${schemaFeatures.categoryLabel.lowercase()}",
            selected = formState.selectedCategory,
            isExpanded = formState.showCategoryDropdown,
            isEnabled = !formState.isLoading,
            accentColor = accentColor,
            onToggle = { viewModel.onEvent(TransactionFormEvent.CategoryDropdownToggled) },
        )

        ChoiceField(
            label = "Mode of Payment",
            placeholder = "How was it paid?",
            selected = formState.selectedPaymentMode,
            isExpanded = formState.showPaymentDropdown,
            isEnabled = !formState.isLoading,
            accentColor = accentColor,
            onToggle = { viewModel.onEvent(TransactionFormEvent.PaymentDropdownToggled) },
        )

        if (formState.showCategoryDropdown) {
            ChoicePickerSheet(
                title = categoryPickerTitle,
                options = categoryOptions,
                selected = formState.selectedCategory,
                accentColor = accentColor,
                onDismiss = { viewModel.onEvent(TransactionFormEvent.CategoryDropdownToggled) },
                onSelect = { viewModel.onEvent(TransactionFormEvent.CategorySelected(it)) },
            )
        }

        if (formState.showPaymentDropdown) {
            ChoicePickerSheet(
                title = "Pick a payment mode",
                options = PaymentMode.entries.map { it.displayName },
                selected = formState.selectedPaymentMode,
                accentColor = accentColor,
                onDismiss = { viewModel.onEvent(TransactionFormEvent.PaymentDropdownToggled) },
                onSelect = { viewModel.onEvent(TransactionFormEvent.PaymentModeSelected(it)) },
            )
        }

        if (schemaFeatures.showPaidToggle) {
            PaidToggleRow(
                isPaid = formState.isPaid,
                isEnabled = !formState.isLoading,
                accentColor = accentColor,
                onPaidChanged = { viewModel.onEvent(TransactionFormEvent.IsPaidChanged(it)) },
            )
        }

        SaveButton(
            isLoading = formState.isLoading,
            isEnabled = formState.isValid && !formState.isLoading,
            onClick = {
                keyboardController?.hide()
                focusManager.clearFocus()
                viewModel.onEvent(TransactionFormEvent.FormSubmitted)
            },
            modifier = Modifier.fillMaxWidth(),
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
            .clip(PillCorner)
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
                    .clip(PillCorner)
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
        targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroAmountField(
    amount: String,
    isIncome: Boolean,
    isEnabled: Boolean,
    accentColor: Color,
    onAmountChanged: (String) -> Unit,
    onImeNext: () -> Unit,
) {
    val bounce = rememberPressBounce(pressedScale = 0.98f)
    val isInvalid = amount.isNotEmpty() && amount.toDoubleOrNull() == null
    OutlinedTextField(
        value = amount,
        onValueChange = onAmountChanged,
        label = { Text(if (isIncome) "Inflow Amount" else "Outflow Amount") },
        placeholder = { Text("0.00", style = LocalTextStyle.current.copy(fontSize = 28.sp)) },
        prefix = {
            // No bundled font ships the ₱ glyph (renders as "no glyph" on web), so draw it.
            PesoGlyph(
                color = accentColor,
                modifier = Modifier.padding(end = 2.dp),
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onNext = { onImeNext() }),
        modifier = Modifier.fillMaxWidth().then(bounce.modifier),
        textStyle = LocalTextStyle.current.copy(
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        enabled = isEnabled,
        isError = isInvalid,
        supportingText = if (isInvalid) {
            { Text("Enter a valid number, e.g. 250.00") }
        } else null,
        singleLine = true,
        colors = fieldColors(),
        shape = FieldCorner,
        interactionSource = bounce.interactionSource,
    )
}

/** Hand-drawn peso sign (₱). No bundled font carries U+20B1, so we vector it for cross-platform parity. */
@Composable
private fun PesoGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 17.dp, height = 24.dp)) {
        val w = size.width
        val h = size.height
        val stemW = 2.4.dp.toPx()
        val barW = 2.0.dp.toPx()
        val stemX = w * 0.24f
        // Vertical stem of the "P".
        drawLine(color, Offset(stemX, h * 0.08f), Offset(stemX, h * 0.92f), stemW, cap = StrokeCap.Round)
        // Upper bowl.
        val bowl = Path().apply {
            moveTo(stemX, h * 0.08f)
            cubicTo(w * 1.02f, h * 0.02f, w * 1.02f, h * 0.56f, stemX, h * 0.50f)
        }
        drawPath(bowl, color, style = Stroke(width = stemW, cap = StrokeCap.Round))
        // The two horizontal bars that distinguish ₱ from P.
        drawLine(color, Offset(w * 0.02f, h * 0.28f), Offset(w * 0.66f, h * 0.28f), barW, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.02f, h * 0.46f), Offset(w * 0.66f, h * 0.46f), barW, cap = StrokeCap.Round)
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldCorner)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = FieldCorner,
            )
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                enabled = isEnabled,
                onClick = onToggle,
            )
            .then(bounce.modifier)
            .graphicsLayer { alpha = containerAlpha }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(50))
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChoicePickerSheet(
    title: String,
    options: List<String>,
    selected: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
) {
    val bounce = rememberPressBounce(pressedScale = 0.92f)
    val container by animateColorAsState(
        targetValue = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipContent",
    )
    Box(
        modifier = Modifier
            .clip(PillCorner)
            .background(container)
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                onClick = onClick,
            )
            .then(bounce.modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
        )
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
            .clip(FieldCorner)
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
                checkedThumbColor = Color.White,
            ),
        )
    }
}

@Composable
private fun SaveButton(
    isLoading: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounce = rememberPressBounce(pressedScale = 0.96f)
    // The submit CTA is always the brand golden — predictable regardless of income/expense.
    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier
            .height(56.dp)
            .then(bounce.modifier),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        interactionSource = bounce.interactionSource,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(10.dp))
            Text("Saving…", fontWeight = FontWeight.SemiBold)
        } else {
            Text(
                text = "Save Transaction",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.secondary,
    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledBorderColor = MaterialTheme.colorScheme.outline,
    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
