package org.example.project.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                TextButton(onClick = { showDatePicker = true }) {
                    Text("📅")
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // Description Input
        OutlinedTextField(
            value = formState.description,
            onValueChange = viewModel::updateDescription,
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        )

        // Income/Expense Toggle
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Transaction Type", style = MaterialTheme.typography.labelMedium)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = !formState.isIncome,
                                onClick = { viewModel.updateIsIncome(false) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !formState.isIncome,
                            onClick = { viewModel.updateIsIncome(false) },
                            enabled = !uiState.isLoading
                        )
                        Text("Expense (Outflow)")
                    }
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = formState.isIncome,
                                onClick = { viewModel.updateIsIncome(true) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = formState.isIncome,
                            onClick = { viewModel.updateIsIncome(true) },
                            enabled = !uiState.isLoading
                        )
                        Text("Income (Inflow)")
                    }
                }
            }
        }

        // Amount Input
        OutlinedTextField(
            value = formState.amount,
            onValueChange = viewModel::updateAmount,
            label = { Text(if (formState.isIncome) "Inflow Amount" else "Outflow Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text("₱") },
            enabled = !uiState.isLoading,
            isError = formState.amount.isNotEmpty() && formState.amount.toDoubleOrNull() == null
        )

        // Category Dropdown
        ExposedDropdownMenuBox(
            expanded = formState.showCategoryDropdown,
            onExpandedChange = { viewModel.toggleCategoryDropdown() }
        ) {
            OutlinedTextField(
                value = formState.selectedCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formState.showCategoryDropdown) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                enabled = !uiState.isLoading
            )
            
            ExposedDropdownMenu(
                expanded = formState.showCategoryDropdown,
                onDismissRequest = { viewModel.toggleCategoryDropdown() }
            ) {
                TransactionCategory.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.displayName) },
                        onClick = { viewModel.updateCategory(category.displayName) }
                    )
                }
            }
        }

        // Payment Mode Dropdown
        ExposedDropdownMenuBox(
            expanded = formState.showPaymentDropdown,
            onExpandedChange = { viewModel.togglePaymentDropdown() }
        ) {
            OutlinedTextField(
                value = formState.selectedPaymentMode,
                onValueChange = {},
                readOnly = true,
                label = { Text("Mode of Payment") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formState.showPaymentDropdown) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                enabled = !uiState.isLoading
            )
            
            ExposedDropdownMenu(
                expanded = formState.showPaymentDropdown,
                onDismissRequest = { viewModel.togglePaymentDropdown() }
            ) {
                PaymentMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.displayName) },
                        onClick = { viewModel.updatePaymentMode(mode.displayName) }
                    )
                }
            }
        }

        // Paid Checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = formState.isPaid,
                onCheckedChange = viewModel::updateIsPaid,
                enabled = !uiState.isLoading
            )
            Text("Paid", modifier = Modifier.padding(start = 8.dp))
        }

        // Error message
        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        // Success message
        if (uiState.showSuccessMessage) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = uiState.successMessage ?: "Success!",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        // Add bottom padding to account for bottom bar
        Spacer(modifier = Modifier.height(80.dp))
    }
    
    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            currentDate = formState.selectedDate,
            onDateSelected = { date ->
                viewModel.updateDate(date)
            },
            onDismiss = { showDatePicker = false }
        )
    }
}