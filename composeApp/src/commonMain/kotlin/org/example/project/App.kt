package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.example.project.ui.TestConnectionScreen
import org.example.project.ui.TransactionInputScreen
import org.example.project.ui.theme.FinanceTrackerTheme
import org.example.project.viewmodel.TransactionViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    FinanceTrackerTheme {
        val viewModel: TransactionViewModel = viewModel()
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()
        var showTestScreen by remember { mutableStateOf(false) }
        
        // Handle UI state changes
        LaunchedEffect(viewModel.uiState.errorMessage) {
            viewModel.uiState.errorMessage?.let { error ->
                snackbarHostState.showSnackbar(error)
                viewModel.clearError()
            }
        }
        
        LaunchedEffect(viewModel.uiState.showSuccessMessage) {
            if (viewModel.uiState.showSuccessMessage) {
                viewModel.uiState.successMessage?.let { message ->
                    snackbarHostState.showSnackbar(message)
                }
                viewModel.clearSuccess()
            }
        }
        
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                // Bottom buttons
                Surface(
                    shadowElevation = 8.dp,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, end = 16.dp, bottom = 50.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!showTestScreen) {
                                    viewModel.addTransaction()
                                } else {
                                    showTestScreen = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = if (showTestScreen) true else (viewModel.formState.isValid && !viewModel.uiState.isLoading)
                        ) {
                            if (!showTestScreen && viewModel.uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(if (showTestScreen) "Add Transaction" else "Save Transaction")
                            }
                        }
                        Button(
                            onClick = { showTestScreen = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Test Connection")
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (showTestScreen) {
                    TestConnectionScreen(modifier = Modifier.fillMaxSize())
                } else {
                    TransactionInputScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}