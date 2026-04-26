package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.add
import kotlinproject.composeapp.generated.resources.chat_bubble
import kotlinproject.composeapp.generated.resources.dots
import kotlinproject.composeapp.generated.resources.levels
import org.example.project.ui.ChatScreen
import org.example.project.ui.TestConnectionScreen
import org.example.project.ui.TransactionInputScreen
import org.example.project.ui.theme.FinanceTrackerTheme
import org.example.project.viewmodel.TransactionViewModel
import org.example.project.viewmodel.createAiViewModel
import org.example.project.viewmodel.createTransactionViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Tabs for the bottom nav pill. */
private enum class NavTab { CHAT, ADD, DEBUG }

@Composable
@Preview
fun App(viewModel: TransactionViewModel = createTransactionViewModel()) {
    FinanceTrackerTheme {
        val uiState by viewModel.uiState.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        var selectedTab by remember { mutableStateOf(NavTab.ADD) }
        val aiViewModel = createAiViewModel()

        SnackbarHandler(
            errorMessage = uiState.errorMessage,
            successMessage = uiState.successMessage.takeIf { uiState.showSuccessMessage },
            snackbarHostState = snackbarHostState,
            onErrorCleared = viewModel::clearError,
            onSuccessCleared = viewModel::clearSuccess
        )

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            val focusManager = LocalFocusManager.current
            val keyboardController = LocalSoftwareKeyboardController.current

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
            ) {
                // ── Screen content ──
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        NavTab.CHAT -> ChatScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = aiViewModel,
                            bottomPadding = 100.dp // lifts input bar above the floating pill
                        )
                        NavTab.ADD -> TransactionInputScreen(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                        NavTab.DEBUG -> TestConnectionScreen(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // ── Floating Maya-style dark pill nav ──
                FloatingNavPill(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp, start = 50.dp, end = 50.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingNavPill(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = Color(0xFF00C853)   // green accent matching app theme
    val inactiveColor = Color(0xFF9E9E9E) // muted grey
    val pillBackground = Color(0xFF1A1A1A) // near-black like Maya

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(pillBackground)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavPillItem(
                iconRes = Res.drawable.chat_bubble,
                contentDescription = "Chat",
                isSelected = selectedTab == NavTab.CHAT,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = { onTabSelected(NavTab.CHAT) }
            )
            NavPillItem(
                iconRes = Res.drawable.add,
                contentDescription = "Add Transaction",
                isSelected = selectedTab == NavTab.ADD,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = { onTabSelected(NavTab.ADD) }
            )
            NavPillItem(
                iconRes = Res.drawable.dots,
                contentDescription = "Debug",
                isSelected = selectedTab == NavTab.DEBUG,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = { onTabSelected(NavTab.DEBUG) }
            )
        }
    }
}

@Composable
private fun NavPillItem(
    iconRes: org.jetbrains.compose.resources.DrawableResource,
    contentDescription: String,
    isSelected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(
                if (isSelected) activeColor.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (isSelected) activeColor else inactiveColor,
            modifier = Modifier.size(22.dp)
        )
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
        errorMessage?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            onErrorCleared()
        }
    }
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            onSuccessCleared()
        }
    }
}
