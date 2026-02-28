package org.example.project.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.example.project.model.PaymentMode
import org.example.project.model.TransactionCategory
import org.example.project.ui.components.DatePickerDialog
import org.example.project.viewmodel.TransactionViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionInputScreen(
    viewModel: TransactionViewModel,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState
    val formState = viewModel.formState
    var showDatePicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Add Transaction",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // Amount Input
        OutlinedTextField(
            value = formState.amount,
            onValueChange = viewModel::updateAmount,
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
            enabled = !uiState.value.isLoading,
            isError = formState.amount.isNotEmpty() && formState.amount.toDoubleOrNull() == null,
            colors = customTextFieldColors(),
            shape = textFieldCornerShape()
        )

        // Date Input with Picker
        OutlinedTextField(
            value = formState.selectedDate,
            onValueChange = {},
            label = { Text("Date") },
            placeholder = { Text("3/1/2026") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true },
            enabled = false,
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Text("📅")
                }
            },
            colors =customTextFieldColors()
        )

        // Description Input
        OutlinedTextField(
            value = formState.description,
            onValueChange = viewModel::updateDescription,
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.value.isLoading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            ),
            colors = customTextFieldColors(),
            shape = textFieldCornerShape()
        )

        // Category Dropdown
        CategoryDropdown(
            selectedCategory = formState.selectedCategory,
            isExpanded = formState.showCategoryDropdown,
            isEnabled = !uiState.value.isLoading,
            onExpandedChange = viewModel::toggleCategoryDropdown,
            onCategorySelected = viewModel::updateCategory
        )

        // Payment Mode Dropdown
        PaymentModeDropdown(
            selectedMode = formState.selectedPaymentMode,
            isExpanded = formState.showPaymentDropdown,
            isEnabled =!uiState.value.isLoading,
            onExpandedChange = viewModel::togglePaymentDropdown,
            onModeSelected = viewModel::updatePaymentMode
        )

        // Paid Checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = formState.isPaid,
                onCheckedChange = viewModel::updateIsPaid,
                enabled =!uiState.value.isLoading
            )
            Text("Paid", modifier = Modifier.padding(start = 8.dp))
        }

        // Transaction Type Toggle
        TransactionTypeCard(
            isIncome = formState.isIncome,
            isEnabled =!uiState.value.isLoading,
            onTypeChanged = viewModel::updateIsIncome
        )

        // Bottom padding for button bar
        Spacer(modifier = Modifier.height(80.dp))
    }

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            currentDate = formState.selectedDate,
            onDateSelected = viewModel::updateDate,
            onDismiss = { showDatePicker = false }
        )
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
        onExpandedChange = { onExpandedChange() }
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
                    onClick = { onCategorySelected(category.displayName) }
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
        onExpandedChange = { onExpandedChange() }
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
                    onClick = { onModeSelected(mode.displayName) }
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
    Card(modifier = Modifier.fillMaxWidth()) {
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
    focusedBorderColor = Color.LightGray,
    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainer

)

@Composable
private fun disabledTextFieldColors() = OutlinedTextFieldDefaults.colors(
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledBorderColor = MaterialTheme.colorScheme.outline,
    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
)


private fun textFieldCornerShape() = RoundedCornerShape(12.dp)