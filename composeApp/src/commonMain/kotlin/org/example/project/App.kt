package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.chat_bubble
import kotlinproject.composeapp.generated.resources.dots
import kotlinproject.composeapp.generated.resources.levels
import kotlinx.coroutines.launch
import org.example.project.ui.ChatScreen
import org.example.project.ui.TestConnectionScreen
import org.example.project.ui.TransactionInputScreen
import org.example.project.ui.theme.FinanceTrackerTheme
import org.example.project.viewmodel.TransactionViewModel
import org.example.project.viewmodel.createAiViewModel
import org.example.project.viewmodel.createTransactionViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
@Preview
fun App(viewModel: TransactionViewModel = createTransactionViewModel()) {
    FinanceTrackerTheme {
        val uiState by viewModel.uiState.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        var showTestScreen by remember { mutableStateOf(false) }
        var showChatScreen by remember { mutableStateOf(false) }
        val aiViewModel = createAiViewModel()

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
                    showChatScreen = showChatScreen,
                    isLoading = uiState.isLoading,
                    isFormValid = viewModel.formState.isValid,
                    onSaveClick = {
                        when {
                            showTestScreen -> showTestScreen = false
                            showChatScreen -> showChatScreen = false
                            else -> viewModel.addTransaction()
                        }
                    },
                    onTestClick = { showTestScreen = true; showChatScreen = false },
                    onChatClick = { showChatScreen = true; showTestScreen = false }
                )
            }
        ) { paddingValues ->
            val focusManager = LocalFocusManager.current
            val keyboardController = LocalSoftwareKeyboardController.current

            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
                    .padding(paddingValues)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null // No ripple effect
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
            ) {
                if (showTestScreen) {
                    TestConnectionScreen(modifier = Modifier.fillMaxSize())
                } else if (showChatScreen) {
                    ChatScreen(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = aiViewModel
                    )
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
    showChatScreen: Boolean,
    isLoading: Boolean,
    isFormValid: Boolean,
    onSaveClick: () -> Unit,
    onTestClick: () -> Unit,
    onChatClick: () -> Unit
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
                enabled = !isLoading,
                colors = when {
                    showTestScreen || showChatScreen -> ButtonDefaults.buttonColors(
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
                if (!showTestScreen && !showChatScreen && isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (showTestScreen || showChatScreen) "Add Transaction" else "Send Transaction")
                }
            }

            // Chat button
            FilledTonalIconButton(
                onClick = onChatClick,
                modifier = Modifier.size(39.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (showChatScreen)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (showChatScreen)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chat_bubble),
                    contentDescription = "Chat AI",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Test connection button
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
                    painter = painterResource(Res.drawable.dots),
                    contentDescription = "Test Connection",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
