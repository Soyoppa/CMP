package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.example.project.model.PaymentMode
import org.example.project.model.Transaction
import org.example.project.model.TransactionCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionInputScreen(
    onTransactionSaved: (Transaction) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(false) } // true for income, false for expense
    var selectedCategory by remember { mutableStateOf(TransactionCategory.OTHER) }
    var selectedPaymentMode by remember { mutableStateOf(PaymentMode.OTHER) }
    var selectedDate by remember { mutableStateOf("2026-01-26") }
    var isPaid by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showPaymentDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Add Transaction",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // Date Input
        OutlinedTextField(
            value = selectedDate,
            onValueChange = { selectedDate = it },
            label = { Text("Date (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )

        // Description Input
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )

        // Income/Expense Toggle
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Transaction Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = !isIncome,
                                onClick = { isIncome = false }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isIncome,
                            onClick = { isIncome = false }
                        )
                        Text("Expense (Outflow)")
                    }
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = isIncome,
                                onClick = { isIncome = true }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isIncome,
                            onClick = { isIncome = true }
                        )
                        Text("Income (Inflow)")
                    }
                }
            }
        }

        // Amount Input
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text(if (isIncome) "Inflow Amount" else "Outflow Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text("₱") }
        )

        // Category Dropdown
        ExposedDropdownMenuBox(
            expanded = showCategoryDropdown,
            onExpandedChange = { showCategoryDropdown = it }
        ) {
            OutlinedTextField(
                value = selectedCategory.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            
            ExposedDropdownMenu(
                expanded = showCategoryDropdown,
                onDismissRequest = { showCategoryDropdown = false }
            ) {
                TransactionCategory.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.displayName) },
                        onClick = {
                            selectedCategory = category
                            showCategoryDropdown = false
                        }
                    )
                }
            }
        }

        // Payment Mode Dropdown
        ExposedDropdownMenuBox(
            expanded = showPaymentDropdown,
            onExpandedChange = { showPaymentDropdown = it }
        ) {
            OutlinedTextField(
                value = selectedPaymentMode.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Mode of Payment") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPaymentDropdown) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            
            ExposedDropdownMenu(
                expanded = showPaymentDropdown,
                onDismissRequest = { showPaymentDropdown = false }
            ) {
                PaymentMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.displayName) },
                        onClick = {
                            selectedPaymentMode = mode
                            showPaymentDropdown = false
                        }
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
                checked = isPaid,
                onCheckedChange = { isPaid = it }
            )
            Text("Paid", modifier = Modifier.padding(start = 8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save Button
        Button(
            onClick = {
                val amountValue = amount.toDoubleOrNull()
                if (amountValue != null && amountValue > 0 && description.isNotEmpty()) {
                    try {
                        val date = LocalDate.parse(selectedDate)
                        val transaction = Transaction(
                            date = date,
                            description = description,
                            inflow = if (isIncome) amountValue else 0.0,
                            outflow = if (!isIncome) amountValue else 0.0,
                            category = selectedCategory.displayName,
                            modeOfPayment = selectedPaymentMode.displayName,
                            isPaid = isPaid
                        )
                        onTransactionSaved(transaction)
                        
                        // Clear form
                        description = ""
                        amount = ""
                        isIncome = false
                        selectedCategory = TransactionCategory.OTHER
                        selectedPaymentMode = PaymentMode.OTHER
                        isPaid = false
                    } catch (e: Exception) {
                        // Handle invalid date format
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = amount.toDoubleOrNull() != null && 
                     amount.toDoubleOrNull()!! > 0 && 
                     description.isNotEmpty()
        ) {
            Text("Save Transaction")
        }
    }
}