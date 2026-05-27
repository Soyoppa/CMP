package org.example.project

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.add
import kotlinproject.composeapp.generated.resources.app_logo
import kotlinproject.composeapp.generated.resources.chat_bubble
import kotlinproject.composeapp.generated.resources.dots
import kotlinproject.composeapp.generated.resources.failed
import kotlinproject.composeapp.generated.resources.success
import org.example.project.auth.AuthState
import org.example.project.auth.Session
import org.example.project.auth.createAuthRepository
import org.example.project.domain.transaction.TransactionFormEffect
import org.example.project.ui.ChatScreen
import org.example.project.ui.LoginScreen
import org.example.project.ui.TestConnectionScreen
import org.example.project.ui.TransactionInputScreen
import org.example.project.ui.theme.FinanceTrackerTheme
import org.example.project.viewmodel.AuthViewModel
import org.example.project.viewmodel.TransactionViewModel
import org.example.project.viewmodel.createAiViewModel
import org.example.project.viewmodel.createTransactionViewModel
import kotlinx.coroutines.launch
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

// The floating nav pill is a fixed dark-forest glass element in both themes,
// with golden marking the active tab (brand highlight).
private val NavActiveColor = Color(0xFFFFBA00)   // golden
private val NavInactiveColor = Color(0xFFB8C2BB)  // muted sage-grey
private val NavPillBaseColor = Color(0xFF0C3B2E)  // dark forest glass
private val PillCorner = RoundedCornerShape(50.dp)
private val PillItemHeight = 56.dp

@Composable
@Preview
fun App(viewModel: TransactionViewModel = createTransactionViewModel()) {
    var darkThemeOverride by remember { mutableStateOf<Boolean?>(null) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val resolvedDark = darkThemeOverride ?: systemDark
    FinanceTrackerTheme(darkTheme = resolvedDark) {
        val snackbarHostState = remember { SnackbarHostState() }
        var selectedTab by remember { mutableStateOf(NavTab.ADD) }
        val aiViewModel = createAiViewModel()

        val authRepository = remember { createAuthRepository() }
        val authViewModel = remember { AuthViewModel(authRepository) }
        val authState by Session.state.collectAsState()
        val scope = rememberCoroutineScope()

        // Restore any persisted session once at startup.
        LaunchedEffect(Unit) { authRepository.restoreSession() }

        // Guest → "Create account": flip to sign-up, sign the guest out so the gate shows Login.
        val requestSignUp: () -> Unit = {
            authViewModel.setMode(AuthViewModel.Mode.SIGN_UP)
            scope.launch { authRepository.signOut() }
            Unit
        }
        val signOut: () -> Unit = {
            scope.launch { authRepository.signOut() }
            Unit
        }

        LaunchedEffect(viewModel) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is TransactionFormEffect.ShowSuccess ->
                        snackbarHostState.showSnackbar(
                            FeedbackSnackbarVisuals(effect.message, FeedbackKind.SUCCESS)
                        )
                    is TransactionFormEffect.ShowError ->
                        snackbarHostState.showSnackbar(
                            FeedbackSnackbarVisuals(effect.message, FeedbackKind.ERROR)
                        )
                    TransactionFormEffect.FormCleared -> Unit
                }
            }
        }

        when (val auth = authState) {
            AuthState.Loading -> AuthSplash()

            AuthState.SignedOut -> LoginScreen(
                viewModel = authViewModel,
                modifier = Modifier.fillMaxSize(),
            )

            is AuthState.Authenticated -> Scaffold(
                snackbarHost = {}
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (auth.user.isGuest) {
                            GuestBanner(onCreateAccount = requestSignUp)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            when (selectedTab) {
                                NavTab.CHAT -> ChatScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    viewModel = aiViewModel,
                                    bottomPadding = 100.dp, // clears the floating nav pill
                                    onRequestSignUp = requestSignUp,
                                )
                                NavTab.ADD -> TransactionInputScreen(
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                NavTab.DEBUG -> TestConnectionScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    isDarkTheme = resolvedDark,
                                    onDarkThemeChange = { darkThemeOverride = it },
                                    accountEmail = auth.user.email,
                                    onSignOut = signOut,
                                )
                            }
                        }
                    }

                    FloatingNavPill(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp, start = 50.dp, end = 50.dp),
                    )

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp),
                    ) { data ->
                        FeedbackSnackbar(data)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthSplash() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(Res.drawable.app_logo),
            contentDescription = "App logo",
            modifier = Modifier.size(96.dp),
        )
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun GuestBanner(onCreateAccount: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Exploring as guest",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(accent)
                .clickable(onClick = onCreateAccount)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Create account",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

private enum class FeedbackKind { SUCCESS, ERROR }

private class FeedbackSnackbarVisuals(
    override val message: String,
    val kind: FeedbackKind,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
) : SnackbarVisuals

@Composable
private fun FeedbackSnackbar(data: SnackbarData) {
    val visuals = data.visuals as? FeedbackSnackbarVisuals
    val kind = visuals?.kind ?: FeedbackKind.SUCCESS
    val accent = when (kind) {
        FeedbackKind.SUCCESS -> Color(0xFF00C853)
        FeedbackKind.ERROR -> Color(0xFFE53935)
    }
    val iconRes = when (kind) {
        FeedbackKind.SUCCESS -> Res.drawable.success
        FeedbackKind.ERROR -> Res.drawable.failed
    }

    // Spring-in entrance for emotional weight on success.
    val visible by produceState(initialValue = false) { value = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "snackScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "snackAlpha",
    )

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.55f),
                        accent.copy(alpha = 0.18f),
                    )
                ),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = data.visuals.message,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
        )
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

    var pressedTab by remember { mutableStateOf<NavTab?>(null) }

    Box(
        modifier = modifier
            .clip(PillCorner)
            .pointerInput(itemCount, slotWidthPx, horizontalPaddingPx) {
                if (slotWidthPx <= 0) return@pointerInput
                fun tabAt(x: Float): NavTab {
                    val local = (x - horizontalPaddingPx).coerceAtLeast(0f)
                    val idx = (local / slotWidthPx).toInt().coerceIn(0, itemCount - 1)
                    return NavItems[idx].tab
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var current = tabAt(down.position.x)
                    pressedTab = current
                    onTabSelected(current)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            pressedTab = null
                            break
                        }
                        val next = tabAt(change.position.x)
                        if (next != current) {
                            current = next
                            onTabSelected(next)
                        }
                        pressedTab = current
                        change.consume()
                    }
                }
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
                    isPressed = pressedTab == item.tab,
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
    isPressed: Boolean,
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier.height(PillItemHeight),
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
