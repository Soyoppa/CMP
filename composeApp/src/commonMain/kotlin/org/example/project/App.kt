package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.levels
import kotlinx.coroutines.launch
import org.example.project.ui.TestConnectionScreen
import org.example.project.ui.TransactionInputScreen
import org.example.project.ui.theme.FinanceTrackerTheme
import org.example.project.viewmodel.TransactionViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
@Preview
fun App() {
    FinanceTrackerTheme {
        val viewModel: TransactionViewModel = viewModel()
        val uiState by viewModel.uiState.collectAsState() // Collect StateFlow
        val snackbarHostState = remember { SnackbarHostState() }
        var showTestScreen by remember { mutableStateOf(false) }

        // Handle snackbar messages
        SnackbarHandler(
            errorMessage = uiState.errorMessage,
            successMessage = uiState.successMessage.takeIf { uiState.showSuccessMessage },
            snackbarHostState = snackbarHostState,
            onErrorCleared = viewModel::clearError,
            onSuccessCleared = viewModel::clearSuccess
        )

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                BottomActionBar(
                    showTestScreen = showTestScreen,
                    isLoading = uiState.isLoading,
                    isFormValid = viewModel.formState.isValid,
                    onSaveClick = {
                        if (showTestScreen) {
                            showTestScreen = false
                        } else {
                            viewModel.addTransaction()
                        }
                    },
                    onTestClick = { showTestScreen = true }
                )
            }
        ) { paddingValues ->
            Box(
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

@Composable
private fun SnackbarHandler(
    errorMessage: String?,
    successMessage: String?,
    snackbarHostState: SnackbarHostState,
    onErrorCleared: () -> Unit,
    onSuccessCleared: () -> Unit
) {
    LaunchedEffect(errorMessage) {
        errorMessage?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            onErrorCleared()
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            onSuccessCleared()
        }
    }
}


@Composable
private fun BottomActionBar(
    showTestScreen: Boolean,
    isLoading: Boolean,
    isFormValid: Boolean,
    onSaveClick: () -> Unit,
    onTestClick: () -> Unit
) {
    Surface(
        shadowElevation = 8.dp,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = 34.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onSaveClick,
                modifier = Modifier.weight(1f),
                enabled = !isLoading ,
                colors = when {
                    showTestScreen -> ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    isLoading -> ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                    !isFormValid -> ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            ) {
                if (!showTestScreen && isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (showTestScreen) "Add Transaction" else "Send Transaction")
                }
            }

            // PNG icon button with conditional colors
            FilledTonalIconButton(
                onClick = onTestClick,
                modifier = Modifier.size(39.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (showTestScreen)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (showTestScreen)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    painter = painterResource(Res.drawable.levels),
                    contentDescription = "Test Connection",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
