package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.domain.transaction.TransactionFormEvent
import org.example.project.domain.transaction.TransactionFormEffect
import org.example.project.model.PaymentMode
import org.example.project.model.TransactionCategory
import org.example.project.viewmodel.TransactionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionInputScreen(
    viewModel: TransactionViewModel,
    modifier: Modifier = Modifier
) {
    val formState by viewModel.formState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is TransactionFormEffect.ShowSuccess -> {
                        // Show success snackbar/toast
                    }
                    is TransactionFormEffect.ShowError -> {
                        // Show error snackbar/toast
                    }
                    TransactionFormEffect.FormCleared -> {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().padding(bottom = 20.dp)) {

        // ── Scrollable form ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Add Transaction",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            OutlinedTextField(
                value = formState.amount,
                onValueChange = { amount ->
                    viewModel.onEvent(TransactionFormEvent.AmountChanged(amount))
                },
                label = { Text(if (formState.isIncome) "Inflow Amount" else "Outflow Amount") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                prefix = { Text("₱") },
                enabled = !formState.isLoading,
                isError = formState.amount.isNotEmpty() && formState.amount.toDoubleOrNull() == null,
                colors = customTextFieldColors(),
                shape = textFieldCornerShape()
            )

            OutlinedTextField(
                value = formState.description,
                onValueChange = { description ->
                    viewModel.onEvent(TransactionFormEvent.DescriptionChanged(description))
                },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !formState.isLoading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = customTextFieldColors(),
                shape = textFieldCornerShape()
            )

            CategoryDropdown(
                selectedCategory = formState.selectedCategory,
                isExpanded = formState.showCategoryDropdown,
                isEnabled = !formState.isLoading,
                onExpandedChange = {
                    viewModel.onEvent(TransactionFormEvent.CategoryDropdownToggled)
                },
                onCategorySelected = { category ->
                    viewModel.onEvent(TransactionFormEvent.CategorySelected(category))
                }
            )

            PaymentModeDropdown(
                selectedMode = formState.selectedPaymentMode,
                isExpanded = formState.showPaymentDropdown,
                isEnabled = !formState.isLoading,
                onExpandedChange = {
                    viewModel.onEvent(TransactionFormEvent.PaymentDropdownToggled)
                },
                onModeSelected = { mode ->
                    viewModel.onEvent(TransactionFormEvent.PaymentModeSelected(mode))
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = formState.isPaid,
                    onCheckedChange = { isPaid ->
                        viewModel.onEvent(TransactionFormEvent.IsPaidChanged(isPaid))
                    },
                    enabled = !formState.isLoading
                )
                Text("Paid", modifier = Modifier.padding(start = 8.dp))
            }

            TransactionTypeCard(
                isIncome = formState.isIncome,
                isEnabled = !formState.isLoading,
                onTypeChanged = { isIncome ->
                    viewModel.onEvent(TransactionFormEvent.TransactionTypeChanged(isIncome))
                }
            )
        }

        // ── Sticky save button — floats above the nav pill ──
        Button(
            onClick = {
                keyboardController?.hide()
                focusManager.clearFocus()
                viewModel.onEvent(TransactionFormEvent.FormSubmitted)
            },
            enabled = formState.isValid && !formState.isLoading,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 80.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (formState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("Saving...")
            } else {
                Text("Save Transaction")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selectedCategory: String,
    isExpanded: Boolean,
    isEnabled: Boolean,
    onExpandedChange: () -> Unit,
    onCategorySelected: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { if (isEnabled) onExpandedChange() }
    ) {
        OutlinedTextField(
            value = selectedCategory,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            enabled = isEnabled,
            colors = customTextFieldColors(),
            shape = textFieldCornerShape()
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = onExpandedChange
        ) {
            TransactionCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.displayName) },
                    onClick = {
                        onCategorySelected(category.displayName)
                    }
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
    onModeSelected: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { if (isEnabled) onExpandedChange() }
    ) {
        OutlinedTextField(
            value = selectedMode,
            onValueChange = {},
            readOnly = true,
            label = { Text("Mode of Payment") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            enabled = isEnabled,
            colors = customTextFieldColors(),
            shape = textFieldCornerShape()
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = onExpandedChange
        ) {
            PaymentMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    onClick = {
                        onModeSelected(mode.displayName)
                    }
                )
            }
        }
    }
}

@Composable
private fun TransactionTypeCard(
    isIncome: Boolean,
    isEnabled: Boolean,
    onTypeChanged: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 100.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Transaction Type", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = !isIncome,
                        onClick = { onTypeChanged(false) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = !isIncome,
                    onClick = { onTypeChanged(false) },
                    enabled = isEnabled
                )
                Text("Expense (Outflow)")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isIncome,
                        onClick = { onTypeChanged(true) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isIncome,
                    onClick = { onTypeChanged(true) },
                    enabled = isEnabled
                )
                Text("Income (Inflow)")
            }
        }
    }
}

// Reusable color configurations
@Composable
private fun customTextFieldColors() = OutlinedTextFieldDefaults.colors(

    focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor =  MaterialTheme.colorScheme.secondary,
    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledBorderColor = MaterialTheme.colorScheme.outline,
    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

private fun textFieldCornerShape() = RoundedCornerShape(12.dp)