package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.model.Transaction
import org.example.project.repository.TransactionRepository

@Composable
fun TestConnectionScreen(
    modifier: Modifier = Modifier
) {
    val repository = remember { TransactionRepository() }
    val coroutineScope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf("Not tested yet") }
    var isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Google Sheets Connection Test",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Button(
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    try {
                        val transactions = repository.getAllTransactions()
                        testResult = "Read Success! Found ${transactions.size} transactions\n" +
                                   "First few: ${transactions.take(3).joinToString("\n") { 
                                       "${it.date}: ${it.description} - ${it.category}" 
                                   }}"
                    } catch (e: Exception) {
                        testResult = "Read Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            } else {
                Text("Test Read")
            }
        }
        
        Button(
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    try {
                        val testTransaction = Transaction(
                            date = kotlinx.datetime.LocalDate.parse("2026-01-26"),
                            description = "Test Transaction",
                            outflow = 100.0,
                            category = "Test",
                            modeOfPayment = "Test",
                            isPaid = false
                        )
                        val success = repository.addTransaction(testTransaction)
                        testResult = if (success) {
                            "✅ Write Success! Check your Google Sheet to confirm the transaction was added."
                        } else {
                            "⚠️ Write may have failed, but CHECK YOUR GOOGLE SHEET - the data might still be there! Google Apps Script sometimes works even when the response parsing fails."
                        }
                    } catch (e: Exception) {
                        testResult = "❌ Write Error: ${e.message}\n\n⚠️ But still CHECK YOUR GOOGLE SHEET - the transaction might have been added despite the error!"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            } else {
                Text("Test Write")
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = testResult,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Text(
            text = "Current Config:\n" +
                    "Spreadsheet ID: ${org.example.project.config.ApiConfig.SPREADSHEET_ID}\n" +
                    "API Key: ${org.example.project.config.ApiConfig.API_KEY.take(10)}...\n" +
                    "Range: ${org.example.project.config.ApiConfig.SHEET_RANGE}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp)
        )
        
        Text(
            text = "Instructions:\n" +
                    "1. Get the ACTUAL spreadsheet ID from the edit URL (not published URL)\n" +
                    "2. Make sure your Google Sheet is shared with 'Anyone with link can EDIT'\n" +
                    "3. Sheet should have columns: Date, Description, Inflow, Outflow, Category, Mode of Payment, Paid\n" +
                    "4. Your sheet structure matches the format shown in your image",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp)
        )
    }
}