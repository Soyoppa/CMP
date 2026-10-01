package org.example.project

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.add
import kotlinproject.composeapp.generated.resources.app_logo
import kotlinproject.composeapp.generated.resources.chart_bar
import kotlinproject.composeapp.generated.resources.dots
import kotlinproject.composeapp.generated.resources.failed
import kotlinproject.composeapp.generated.resources.success
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import org.example.project.auth.AppUser
import org.example.project.auth.Session
import org.example.project.auth.SessionState
import org.example.project.auth.sessionKey
import org.example.project.config.FeatureFlagStore
import org.example.project.config.createFeatureFlagLoader
import org.example.project.domain.transaction.TransactionFormEffect
import org.example.project.model.BudgetPeriod
import org.example.project.ui.AccountSheet
import org.example.project.ui.BudgetScreen
import org.example.project.ui.CategoriesScreen
import org.example.project.ui.ChatBubble
import org.example.project.ui.ChatModal
import org.example.project.ui.DeleteAccountDialog
import org.example.project.ui.OverlayScope
import org.example.project.ui.PaymentModesScreen
import org.example.project.ui.PaymentStatusScreen
import org.example.project.ui.PlatformBackHandler
import org.example.project.ui.SessionScope
import org.example.project.ui.SettingsScreen
import org.example.project.ui.SummaryScreen
import org.example.project.ui.TransactionFormScreen
import org.example.project.ui.TransactionHistoryScreen
import org.example.project.ui.WelcomeScreen
import org.example.project.ui.components.BounceSurface
import org.example.project.ui.theme.AppShapes
import org.example.project.ui.theme.FinanceTrackerTheme
import org.example.project.viewmodel.AuthMode
import org.example.project.viewmodel.createAuthViewModel
import org.example.project.viewmodel.createChatViewModel
import org.example.project.viewmodel.createSummaryViewModel
import org.example.project.viewmodel.createTransactionFormViewModel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private enum class NavTab { SUMMARY, ADD, SETTINGS }

@Immutable
private data class NavItem(
    val tab: NavTab,
    val iconRes: DrawableResource,
    val contentDescription: String,
)

private val NavItems: List<NavItem> = listOf(
    NavItem(NavTab.SUMMARY, Res.drawable.chart_bar, "Spending Summary"),
    NavItem(NavTab.ADD, Res.drawable.add, "Add Transaction"),
    NavItem(NavTab.SETTINGS, Res.drawable.dots, "Settings"),
)

// The floating nav pill is a fixed dark-forest glass element in both themes,
// with golden marking the active tab (brand highlight).
private val NavActiveColor = Color(0xFFFFBA00)   // golden
private val NavInactiveColor = Color(0xFFB8C2BB)  // muted sage-grey
private val NavPillBaseColor = Color(0xFF0C3B2E)  // dark forest glass
private val PillItemHeight = 56.dp

@Composable
@Preview
fun App() {
    var darkThemeOverride by remember { mutableStateOf<Boolean?>(null) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val resolvedDark = darkThemeOverride ?: systemDark
    FinanceTrackerTheme(darkTheme = resolvedDark) {
        val sessionState by Session.state.collectAsState()

        // Restore the previous session (account, or this-phone-only) and fetch remote feature
        // flags once at startup.
        LaunchedEffect(Unit) { AppContainer.sessionRepository.restore() }
        LaunchedEffect(Unit) { createFeatureFlagLoader().load() }

        when (val session = sessionState) {
            SessionState.Loading -> AuthSplash()

            // Its own scope, so every return to this screen starts from a clean form.
            SessionState.SignedOut -> OverlayScope {
                WelcomeScreen(viewModel = createAuthViewModel(), modifier = Modifier.fillMaxSize())
            }

            // Everything behind the gate lives in a per-session scope: signing out, switching
            // account or ledger discards every ViewModel, so no data survives into the next session.
            is SessionState.Active -> SessionScope(sessionKey = session.user.sessionKey) {
                SignedInApp(
                    user = session.user,
                    isDarkTheme = resolvedDark,
                    onDarkThemeChange = { darkThemeOverride = it },
                )
            }
        }
    }
}

@Composable
private fun SignedInApp(
    user: AppUser,
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
) {
    val graph = remember(user) { AppContainer.session() }
    val profile = graph.profile
    val transactionFormViewModel = createTransactionFormViewModel()
    // Owned here: it also feeds the Add screen's "left to spend" banner and the budget prompt.
    val summaryViewModel = createSummaryViewModel()
    val summaryState by summaryViewModel.uiState.collectAsState()
    val featureFlags by FeatureFlagStore.state.collectAsState()
    // The assistant is an account feature, and needs Gemini on this platform (web + Android) and
    // the remote kill-switch on.
    val chatAvailable = user is AppUser.Account && featureFlags.chatEnabled && AppContainer.aiRepository.isGeminiAvailable
    val snackbarHostState = remember { SnackbarHostState() }

    // Accounts can be edited elsewhere (the web, another phone): coming back to the app re-reads.
    val lifecycleOwner = LocalLifecycleOwner.current
    if (user is AppUser.Account) {
        LaunchedEffect(graph, lifecycleOwner) {
            var firstResume = true
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (firstResume) {
                    firstResume = false
                } else {
                    graph.ledger.invalidate()
                    graph.config.refresh()
                }
            }
        }
    }

    // Every session lands on ADD.
    var selectedTab by remember { mutableStateOf(NavTab.ADD) }
    // The AI chat lives in a floating modal summoned from a bubble, not a nav tab.
    var chatOpen by remember { mutableStateOf(false) }
    // Full-screen modal overlays: budgets (Summary + Settings), list editors and Paid & Unpaid (Settings).
    var budgetOpen by remember { mutableStateOf(false) }
    // Which period the budget sheet edits: the cut-off showing on the Summary, the one being
    // prompted for, or null for "whatever the app would ask for today" (Settings, Add screen).
    var budgetTarget by remember { mutableStateOf<BudgetPeriod?>(null) }
    var categoriesOpen by remember { mutableStateOf(false) }
    var paymentModesOpen by remember { mutableStateOf(false) }
    var paymentStatusOpen by remember { mutableStateOf(false) }
    var transactionsOpen by remember { mutableStateOf(false) }
    var deleteAccountOpen by remember { mutableStateOf(false) }
    // No-account mode: the sign-in / sign-up sheet that moves this phone's data into an account.
    var accountSheetMode by remember { mutableStateOf<AuthMode?>(null) }
    val anyOverlayOpen = chatOpen || budgetOpen || categoriesOpen || paymentModesOpen ||
        paymentStatusOpen || transactionsOpen || deleteAccountOpen || accountSheetMode != null

    // System back closes the top-most overlay instead of leaving the app.
    PlatformBackHandler(enabled = anyOverlayOpen) {
        when {
            accountSheetMode != null -> accountSheetMode = null
            deleteAccountOpen -> deleteAccountOpen = false
            transactionsOpen -> transactionsOpen = false
            paymentStatusOpen -> paymentStatusOpen = false
            paymentModesOpen -> paymentModesOpen = false
            categoriesOpen -> categoriesOpen = false
            budgetOpen -> budgetOpen = false
            chatOpen -> chatOpen = false
        }
    }

    // Budgets are set when the salary arrives, so the app only asks while a cut-off's budgeting
    // window is open (the 14th–20th, and two days before the 1st through the 5th) and it has no
    // budget yet. Then the editor opens by itself, pre-filled from the last budget — once per
    // cut-off per session; dismissing it leaves the prompt banner on the Add screen. Someone who
    // has never budgeted (a first launch) only gets the banner.
    var promptedPeriodId by remember { mutableStateOf<String?>(null) }
    val budgetPromptId = summaryState.budgetPrompt?.id
    LaunchedEffect(budgetPromptId, summaryState.isLoading, selectedTab) {
        val ready = !summaryState.isLoading && summaryState.error == null
        if (ready && budgetPromptId != null && summaryState.budgetPromptOpensSheet &&
            profile.summaryAvailable && promptedPeriodId != budgetPromptId &&
            // Never over Settings: changing how you budget there shouldn't throw the editor in
            // your face. The Add banner and the Summary card still ask.
            selectedTab != NavTab.SETTINGS
        ) {
            promptedPeriodId = budgetPromptId
            budgetTarget = summaryState.budgetPrompt
            budgetOpen = true
        }
    }

    // If chat is remotely disabled while the modal is open, collapse it.
    LaunchedEffect(chatAvailable) {
        if (!chatAvailable) chatOpen = false
    }

    LaunchedEffect(transactionFormViewModel) {
        transactionFormViewModel.effects.collect { effect ->
            val visuals = when (effect) {
                is TransactionFormEffect.ShowSuccess -> FeedbackSnackbarVisuals(effect.message, FeedbackKind.SUCCESS)
                is TransactionFormEffect.ShowError -> FeedbackSnackbarVisuals(effect.message, FeedbackKind.ERROR)
                TransactionFormEffect.FormCleared -> null
            }
            if (visuals != null) {
                // A quick flash: pull it down from under showSnackbar's suspend so the message
                // confirms-and-vanishes instead of lingering.
                val autoDismiss = launch {
                    delay(1000)
                    snackbarHostState.currentSnackbarData?.dismiss()
                }
                snackbarHostState.showSnackbar(visuals)
                autoDismiss.cancel()
            }
        }
    }

    // One-off session messages, e.g. "Moved your data from this phone to your account."
    val notice by Session.notice.collectAsState()
    LaunchedEffect(notice) {
        val message = notice ?: return@LaunchedEffect
        Session.consumeNotice()
        val autoDismiss = launch {
            delay(4000)
            snackbarHostState.currentSnackbarData?.dismiss()
        }
        snackbarHostState.showSnackbar(FeedbackSnackbarVisuals(message, FeedbackKind.SUCCESS))
        autoDismiss.cancel()
    }

    Scaffold(snackbarHost = {}) { paddingValues ->
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
                Box(modifier = Modifier.weight(1f)) {
                    // From the Summary: edit the cut-off on screen, not today's.
                    val openSelectedBudget: (() -> Unit)? =
                        if (profile.summaryAvailable) ({
                            budgetTarget = summaryState.selectedPeriod
                            budgetOpen = true
                        }) else null
                    val openCurrentBudget = {
                        budgetTarget = null
                        budgetOpen = true
                    }
                    val openPaymentStatus: (() -> Unit)? =
                        if (profile.showPaidToggle) ({ paymentStatusOpen = true }) else null
                    when (selectedTab) {
                        NavTab.SUMMARY -> SummaryScreen(
                            modifier = Modifier.fillMaxSize(),
                            bottomPadding = 100.dp, // clears the floating nav pill
                            onOpenBudgets = openSelectedBudget,
                            onOpenPaymentStatus = openPaymentStatus,
                            viewModel = summaryViewModel,
                        )
                        NavTab.ADD -> TransactionFormScreen(
                            viewModel = transactionFormViewModel,
                            modifier = Modifier.fillMaxSize(),
                            budgetStatus = summaryState.thisPeriod.takeIf { profile.summaryAvailable },
                            budgetPeriodLabel = summaryState.currentPeriod.label,
                            budgetPromptLabel = summaryState.budgetPrompt?.label
                                .takeIf { profile.summaryAvailable },
                            onSetBudget = openCurrentBudget,
                            // No budget yet → straight to the editor; otherwise the full breakdown.
                            onBudgetClick = {
                                val hasBudget = summaryState.thisPeriod?.hasBudget == true
                                if (!hasBudget && profile.summaryAvailable) openCurrentBudget()
                                else selectedTab = NavTab.SUMMARY
                            },
                        )
                        NavTab.SETTINGS -> SettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            isDarkTheme = isDarkTheme,
                            onDarkThemeChange = onDarkThemeChange,
                            aiAvailable = chatAvailable,
                            onOpenAccount = { accountSheetMode = it },
                            onDeleteAccount = { deleteAccountOpen = true },
                            onOpenBudgets = openCurrentBudget,
                            onOpenCategories = { categoriesOpen = true },
                            onOpenPaymentModes = { paymentModesOpen = true },
                            onOpenPaymentStatus = openPaymentStatus,
                            onOpenTransactions = { transactionsOpen = true },
                        )
                    }
                }
            }

            val visibleNavItems = remember(profile) {
                NavItems.filter { it.tab != NavTab.SUMMARY || profile.summaryAvailable }
            }
            FloatingNavPill(
                items = visibleNavItems,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 50.dp, end = 50.dp),
            )

            // Floating AI assistant — a draggable chat-head bubble (Messenger-style) that
            // opens the chat as a modal growing from its corner. Hidden while the modal is open.
            ChatBubble(
                visible = chatAvailable && !chatOpen,
                onClick = { chatOpen = true },
                modifier = Modifier.fillMaxSize(),
            )

            if (chatAvailable) {
                ChatModal(
                    visible = chatOpen,
                    onClose = { chatOpen = false },
                    viewModel = createChatViewModel(),
                )
            }

            // Every screen reads the same session repositories, so saved budgets and list edits
            // show up everywhere without refresh calls.
            if (budgetOpen && profile.summaryAvailable) {
                OverlayScope {
                    BudgetScreen(
                        period = budgetTarget,
                        onClose = { budgetOpen = false },
                        onManageCategories = {
                            budgetOpen = false
                            categoriesOpen = true
                        },
                    )
                }
            }

            if (categoriesOpen) {
                OverlayScope { CategoriesScreen(onClose = { categoriesOpen = false }) }
            }

            if (paymentModesOpen) {
                OverlayScope { PaymentModesScreen(onClose = { paymentModesOpen = false }) }
            }

            if (paymentStatusOpen && profile.showPaidToggle) {
                OverlayScope {
                    PaymentStatusScreen(
                        onClose = { paymentStatusOpen = false },
                    )
                }
            }

            // Ledger history with delete.
            if (transactionsOpen) {
                OverlayScope {
                    TransactionHistoryScreen(onClose = { transactionsOpen = false })
                }
            }

            if (deleteAccountOpen && user is AppUser.Account) {
                OverlayScope {
                    DeleteAccountDialog(onDismiss = { deleteAccountOpen = false })
                }
            }

            accountSheetMode?.let { mode ->
                OverlayScope {
                    AccountSheet(initialMode = mode, onClose = { accountSheetMode = null })
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 32.dp, end = 32.dp),
            ) { data ->
                FeedbackSnackbar(data)
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

private enum class FeedbackKind { SUCCESS, ERROR }

private class FeedbackSnackbarVisuals(
    override val message: String,
    val kind: FeedbackKind,
    // Indefinite by design — the half-second auto-dismiss in App() owns the timing,
    // so the built-in Short/Long timeout never competes with it.
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite,
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
    items: List<NavItem>,
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemCount = items.size
    val selectedIndex = items.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)

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
            .clip(AppShapes.pill)
            .pointerInput(itemCount, slotWidthPx, horizontalPaddingPx) {
                if (slotWidthPx <= 0) return@pointerInput
                fun tabAt(x: Float): NavTab {
                    val local = (x - horizontalPaddingPx).coerceAtLeast(0f)
                    val idx = (local / slotWidthPx).toInt().coerceIn(0, itemCount - 1)
                    return items[idx].tab
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
                shape = AppShapes.pill,
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
                    .clip(AppShapes.pill)
                    .background(NavActiveColor.copy(alpha = 0.18f))
                    .border(
                        width = 1.dp,
                        color = NavActiveColor.copy(alpha = 0.35f),
                        shape = AppShapes.pill,
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidthPx = it.width },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
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
