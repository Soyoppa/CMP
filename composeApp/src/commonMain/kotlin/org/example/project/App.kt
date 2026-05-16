package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.example.project.domain.transaction.TransactionFormEffect
import org.example.project.ui.ChatScreen
import org.example.project.ui.TestConnectionScreen
import org.example.project.ui.TransactionInputScreen
import org.example.project.ui.theme.FinanceTrackerTheme
import org.example.project.viewmodel.TransactionViewModel
import org.example.project.viewmodel.createAiViewModel
import org.example.project.viewmodel.createTransactionViewModel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private enum class NavTab { CHAT, ADD, DEBUG }

@Immutable
private data class NavItem(
    val tab: NavTab,
    val iconRes: DrawableResource,
    val contentDescription: String,
)

private val NavItems: List<NavItem> = listOf(
    NavItem(NavTab.CHAT, Res.drawable.chat_bubble, "Chat"),
    NavItem(NavTab.ADD, Res.drawable.add, "Add Transaction"),
    NavItem(NavTab.DEBUG, Res.drawable.dots, "Debug"),
)

private val NavActiveColor = Color(0xFF00C853)
private val NavInactiveColor = Color(0xFF9E9E9E)
private val NavPillBackground = Color(0xFF1A1A1A)

@Composable
@Preview
fun App(viewModel: TransactionViewModel = createTransactionViewModel()) {
    FinanceTrackerTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        var selectedTab by remember { mutableStateOf(NavTab.ADD) }
        val aiViewModel = createAiViewModel()

        LaunchedEffect(viewModel) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is TransactionFormEffect.ShowSuccess ->
                        snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
                    is TransactionFormEffect.ShowError ->
                        snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
                    TransactionFormEffect.FormCleared -> Unit
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            val focusManager = LocalFocusManager.current
            val keyboardController = LocalSoftwareKeyboardController.current
            val dismissInteractionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .clickable(
                        interactionSource = dismissInteractionSource,
                        indication = null,
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
            ) {
                when (selectedTab) {
                    NavTab.CHAT -> ChatScreen(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = aiViewModel,
                        bottomPadding = 100.dp, // clears the floating nav pill
                    )
                    NavTab.ADD -> TransactionInputScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                    NavTab.DEBUG -> TestConnectionScreen(
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                FloatingNavPill(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp, start = 50.dp, end = 50.dp),
                )
            }
        }
    }
}

@Composable
private fun FloatingNavPill(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(NavPillBackground)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItems.forEach { item ->
                NavPillItem(
                    iconRes = item.iconRes,
                    contentDescription = item.contentDescription,
                    isSelected = selectedTab == item.tab,
                    onClick = { onTabSelected(item.tab) },
                )
            }
        }
    }
}

@Composable
private fun NavPillItem(
    iconRes: DrawableResource,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(
                if (isSelected) NavActiveColor.copy(alpha = 0.15f) else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (isSelected) NavActiveColor else NavInactiveColor,
            modifier = Modifier.size(22.dp),
        )
    }
}