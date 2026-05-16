package org.example.project

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
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
private val NavInactiveColor = Color(0xFFB8B8B8)
private val NavPillBaseColor = Color(0xFF0E0E0E)
private val PillCorner = RoundedCornerShape(50.dp)
private val PillItemHeight = 56.dp

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
    val itemCount = NavItems.size
    val selectedIndex = NavItems.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)

    var rowWidthPx by remember { mutableStateOf(0) }
    val slotWidthPx = if (itemCount > 0) rowWidthPx / itemCount else 0
    val targetOffsetPx = slotWidthPx * selectedIndex

    val animatedOffsetPx by animateIntAsState(
        targetValue = targetOffsetPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "navIndicatorOffset",
    )

    val density = LocalDensity.current
    val slotWidthDp = with(density) { slotWidthPx.toDp() }
    val horizontalPaddingPx = with(density) { 8.dp.toPx() }

    Box(
        modifier = modifier
            .clip(PillCorner)
            .pointerInput(itemCount, selectedTab, slotWidthPx) {
                if (slotWidthPx <= 0) return@pointerInput
                fun resolveTab(x: Float) {
                    val local = (x - horizontalPaddingPx).coerceAtLeast(0f)
                    val idx = (local / slotWidthPx).toInt().coerceIn(0, itemCount - 1)
                    val tab = NavItems[idx].tab
                    if (tab != selectedTab) onTabSelected(tab)
                }
                detectHorizontalDragGestures(
                    onDragStart = { offset -> resolveTab(offset.x) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        resolveTab(change.position.x)
                    },
                )
            }
            // Layered glass: translucent dark base + soft top highlight + edge stroke.
            .background(NavPillBaseColor.copy(alpha = 0.62f))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.02f),
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.38f),
                        Color.White.copy(alpha = 0.06f),
                    )
                ),
                shape = PillCorner,
            )
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (slotWidthPx > 0) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetPx, 0) }
                    .width(slotWidthDp)
                    .height(PillItemHeight)
                    .clip(PillCorner)
                    .background(NavActiveColor.copy(alpha = 0.18f))
                    .border(
                        width = 1.dp,
                        color = NavActiveColor.copy(alpha = 0.35f),
                        shape = PillCorner,
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidthPx = it.width },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItems.forEach { item ->
                NavPillItem(
                    iconRes = item.iconRes,
                    contentDescription = item.contentDescription,
                    isSelected = item.tab == selectedTab,
                    onClick = { onTabSelected(item.tab) },
                    modifier = Modifier.weight(1f),
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
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring-physics press feedback — quick squish, bouncy return.
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.84f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "navItemPressScale",
    )
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "navItemSelectionScale",
    )

    Box(
        modifier = modifier
            .height(PillItemHeight)
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
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    val s = pressScale * selectionScale
                    scaleX = s
                    scaleY = s
                },
        )
    }
}
