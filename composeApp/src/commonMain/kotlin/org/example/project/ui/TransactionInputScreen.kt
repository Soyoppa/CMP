package org.example.project.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.domain.transaction.TransactionFormEffect
import org.example.project.domain.transaction.TransactionFormEvent
import org.example.project.model.PaymentMode
import org.example.project.model.TransactionCategory
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.viewmodel.TransactionViewModel

private val IncomeColor = Color(0xFF00C853)
private val ExpenseColor = Color(0xFFE53935)
private val PillCorner = RoundedCornerShape(50.dp)
private val FieldCorner = RoundedCornerShape(14.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionInputScreen(
    viewModel: TransactionViewModel,
    modifier: Modifier = Modifier,
) {
    val formState by viewModel.formState.collectAsState()
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Add Transaction",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            // 1. Type decision first — it changes what the amount means.
            TransactionTypeSegmented(
                isIncome = formState.isIncome,
                isEnabled = !formState.isLoading,
                accentColor = accentColor,
                onTypeChanged = { isIncome ->
                    viewModel.onEvent(TransactionFormEvent.TransactionTypeChanged(isIncome))
                },
            )

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

            CategoryDropdown(
                selectedCategory = formState.selectedCategory,
                isExpanded = formState.showCategoryDropdown,
                isEnabled = !formState.isLoading,
                onExpandedChange = { viewModel.onEvent(TransactionFormEvent.CategoryDropdownToggled) },
                onCategorySelected = { viewModel.onEvent(TransactionFormEvent.CategorySelected(it)) },
            )

            PaymentModeDropdown(
                selectedMode = formState.selectedPaymentMode,
                isExpanded = formState.showPaymentDropdown,
                isEnabled = !formState.isLoading,
                onExpandedChange = { viewModel.onEvent(TransactionFormEvent.PaymentDropdownToggled) },
                onModeSelected = { viewModel.onEvent(TransactionFormEvent.PaymentModeSelected(it)) },
            )

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
            accentColor = accentColor,
            onClick = {
                keyboardController?.hide()
                focusManager.clearFocus()
                viewModel.onEvent(TransactionFormEvent.FormSubmitted)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 96.dp),
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
    val density = LocalDensity.current
    val slotWidthDp = with(density) { slotPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(PillCorner)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .onSizeChanged { widthPx = it.width },
    ) {
        if (slotPx > 0) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetPx, 0) }
                    .width(slotWidthDp)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .clip(PillCorner)
                    .background(accentColor),
            )
        }
        Row(modifier = Modifier.fillMaxSize()) {
            SegmentLabel(
                text = "Expense",
                isSelected = !isIncome,
                isEnabled = isEnabled,
                onClick = { onTypeChanged(false) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SegmentLabel(
                text = "Income",
                isSelected = isIncome,
                isEnabled = isEnabled,
                onClick = { onTypeChanged(true) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SegmentLabel(
    text: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounce = rememberPressBounce(pressedScale = 0.95f)
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "segmentTextColor",
    )
    Box(
        modifier = modifier
            .clip(PillCorner)
            .toggleable(
                value = isSelected,
                enabled = isEnabled,
                interactionSource = bounce.interactionSource,
                indication = null,
                onValueChange = { if (it) onClick() },
            )
            .then(bounce.modifier),
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
            Text(
                text = "₱",
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selectedCategory: String,
    isExpanded: Boolean,
    isEnabled: Boolean,
    onExpandedChange: () -> Unit,
    onCategorySelected: (String) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { if (isEnabled) onExpandedChange() },
    ) {
        OutlinedTextField(
            value = selectedCategory,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            placeholder = { Text("Select a category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            enabled = isEnabled,
            colors = fieldColors(),
            shape = FieldCorner,
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = onExpandedChange,
        ) {
            TransactionCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.displayName) },
                    onClick = { onCategorySelected(category.displayName) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentModeDropdown(
    selectedMode: String,
    isExpanded: Boolean,
    isEnabled: Boolean,
    onExpandedChange: () -> Unit,
    onModeSelected: (String) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { if (isEnabled) onExpandedChange() },
    ) {
        OutlinedTextField(
            value = selectedMode,
            onValueChange = {},
            readOnly = true,
            label = { Text("Mode of Payment") },
            placeholder = { Text("How was it paid?") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            enabled = isEnabled,
            colors = fieldColors(),
            shape = FieldCorner,
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = onExpandedChange,
        ) {
            PaymentMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    onClick = { onModeSelected(mode.displayName) },
                )
            }
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
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounce = rememberPressBounce(pressedScale = 0.96f)
    val containerColor by animateColorAsState(
        targetValue = accentColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "saveContainer",
    )
    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier
            .height(56.dp)
            .then(bounce.modifier),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
        ),
        interactionSource = bounce.interactionSource,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White,
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
