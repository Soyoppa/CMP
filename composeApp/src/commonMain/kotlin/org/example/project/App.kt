package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.example.project.ui.TestConnectionScreen
import org.example.project.ui.TransactionInputScreen
import org.example.project.viewmodel.TransactionViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        val viewModel: TransactionViewModel = viewModel()
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()
        var showTestScreen by remember { mutableStateOf(false) }
        
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize()
        ) {
            // Toggle button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showTestScreen = false },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add Transaction")
                }
                Button(
                    onClick = { showTestScreen = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test Connection")
                }
            }
            
            if (showTestScreen) {
                TestConnectionScreen(modifier = Modifier.weight(1f))
            } else {
                TransactionInputScreen(
                    onTransactionSaved = { transaction ->
                        viewModel.addTransaction(transaction) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Transaction saved to Google Sheets!"
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Show error messages
            viewModel.errorMessage?.let { error ->
                LaunchedEffect(error) {
                    snackbarHostState.showSnackbar(error)
                }
            }
            
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}