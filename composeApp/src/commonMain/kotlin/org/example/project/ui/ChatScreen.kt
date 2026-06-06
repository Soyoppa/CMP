package org.example.project.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key.Companion.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.app_logo
import kotlinproject.composeapp.generated.resources.chart_label
import kotlinproject.composeapp.generated.resources.send
import kotlinx.coroutines.launch
import org.example.project.auth.AuthState
import org.example.project.auth.Session
import org.example.project.config.FeatureFlagStore
import org.example.project.config.SchemaFeatures
import org.example.project.data.ai.AiPrefs
import org.example.project.data.ai.AiProviderId
import org.example.project.data.ai.AiUsageTracker
import org.example.project.model.ChatMessage
import org.example.project.ui.components.BounceSurface
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.ui.theme.AppShapes
import org.example.project.viewmodel.AiViewModel
import org.example.project.viewmodel.createAiViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private val BubbleCorner = 18.dp

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: AiViewModel = createAiViewModel(),
    bottomPadding: Dp = 0.dp,
    onRequestSignUp: () -> Unit = {},
) {
    // Some schemas (e.g. Tracker 2) have no analysis sheets yet — show a "coming soon" state.
    val aiAvailable = remember { SchemaFeatures.current().aiAnalysisAvailable }
    if (!aiAvailable) {
        AiComingSoon(modifier = modifier.fillMaxSize(), bottomPadding = bottomPadding)
        return
    }

    val uiState by viewModel.uiState.collectAsState()
    val usage by AiUsageTracker.state.collectAsState()
    val showPerMessageTokens by AiPrefs.showPerMessageTokens.collectAsState()
    val flags by FeatureFlagStore.state.collectAsState()
    val authState by Session.state.collectAsState()
    val authUser = (authState as? AuthState.Authenticated)?.user
    val isGuest = authUser?.isGuest == true
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var showSummarySheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(uiState.messages.size - 1) }
        }
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) { viewModel.clearError() }
    }

    Column(modifier = modifier.fillMaxSize()) {

        ChatHeader(
            transactionsLoaded = uiState.transactionsLoaded,
            isLoadingTransactions = uiState.isLoadingTransactions,
            provider = usage.lastProvider,
            model = usage.lastModel,
            onClearChat = viewModel::clearChat,
        )

        Box(modifier = Modifier.weight(1f)) {
            if (uiState.messages.isEmpty()) {
                EmptyState(
                    onSuggestionClick = { suggestion ->
                        if (isGuest) viewModel.sendMessage(suggestion) else inputText = suggestion
                    },
                    onShowSummary = { showSummarySheet = true },
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        MessageBubble(message = message, showTokens = showPerMessageTokens)
                    }
                }
            }
        }

        uiState.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        when {
            isGuest && uiState.guestLocked ->
                GuestChatUpsell(
                    onSignUp = onRequestSignUp,
                    signupEnabled = flags.signupEnabled,
                    bottomPadding = bottomPadding,
                )

            // Guests interact by tapping suggestions only — no free-text composer.
            isGuest ->
                GuestSuggestHint(bottomPadding = bottomPadding)

            else -> ChatInputBar(
                value = inputText,
                isLoading = uiState.isLoading,
                onValueChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                bottomPadding = bottomPadding,
                hint = "Ask about your finances…",
            )
        }
    }

    if (showSummarySheet) {
        SummarySheet(onDismiss = { showSummarySheet = false })
    }
}

/** Replaces the composer for guests before they spend their message — they can only tap a suggestion. */
@Composable
private fun GuestSuggestHint(bottomPadding: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp + bottomPadding),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(AppShapes.field)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Guest preview · tap a suggested question above to try the assistant",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Replaces the composer once a guest has used their free message. */
@Composable
private fun GuestChatUpsell(onSignUp: () -> Unit, signupEnabled: Boolean, bottomPadding: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp + bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "You've used your free guest message.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (signupEnabled) {
            BounceSurface(
                onClick = onSignUp,
                shape = AppShapes.field,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "Sign up to keep chatting",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(
    transactionsLoaded: Boolean,
    isLoadingTransactions: Boolean,
    provider: AiProviderId?,
    model: String?,
    onClearChat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "AI Treasurer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatusPill(
                isLoading = isLoadingTransactions,
                isLoaded = transactionsLoaded,
            )
            // Appears once the first reply lands; cross-fades sage↔amber on provider change.
            if (provider != null) {
                ProviderPill(provider = provider, model = model)
            }
        }
        BounceSurface(
            onClick = onClearChat,
            shape = AppShapes.pill,
            color = MaterialTheme.colorScheme.surfaceContainer,
            pressedScale = 0.92f,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Clear",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(isLoading: Boolean, isLoaded: Boolean) {
    val (label, color) = when {
        isLoading -> "Loading your transactions" to MaterialTheme.colorScheme.primary
        isLoaded -> "Transactions synced" to MaterialTheme.colorScheme.secondary
        else -> "No transaction data" to MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = Modifier
            .clip(AppShapes.pill)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(AppShapes.pill)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Which AI backend answered last — sage for Gemini, amber for the Ollama fallback. */
@Composable
private fun ProviderPill(provider: AiProviderId, model: String?) {
    val targetColor = when (provider) {
        AiProviderId.GEMINI -> MaterialTheme.colorScheme.secondary
        AiProviderId.OLLAMA -> MaterialTheme.colorScheme.tertiary
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "providerColor",
    )
    val label = when (provider) {
        AiProviderId.GEMINI -> "Gemini" + (model?.let { " · ${shortModel(it)}" } ?: "")
        AiProviderId.OLLAMA -> "Ollama · fallback"
    }
    Row(
        modifier = Modifier
            .clip(AppShapes.pill)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(AppShapes.pill)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** "gemini-2.5-flash" → "2.5-flash"; leaves non-Gemini model ids untouched. */
private fun shortModel(model: String): String = model.removePrefix("gemini-")

/** Faint per-message usage line under an assistant bubble; fades in after the bubble settles. */
@Composable
private fun TokenFootnote(model: String, tokens: Int) {
    val visible by produceState(false) { value = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tokenFade",
    )
    Text(
        text = "${shortModel(model)} · $tokens tok",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 4.dp, start = 6.dp)
            .graphicsLayer { this.alpha = alpha },
    )
}

@Composable
private fun MessageBubble(message: ChatMessage, showTokens: Boolean) {
    val isUser = message.role == ChatMessage.Role.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(AppShapes.pill)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
        }

        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = BubbleCorner,
                            topEnd = BubbleCorner,
                            bottomStart = if (isUser) BubbleCorner else 4.dp,
                            bottomEnd = if (isUser) 4.dp else BubbleCorner,
                        )
                    )
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (message.isStreaming) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Thinking…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = message.content,
                        fontSize = 14.sp,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                    )
                }
            }

            if (!isUser && !message.isStreaming && showTokens && message.model != null) {
                TokenFootnote(model = message.model, tokens = message.totalTokens ?: 0)
            }
        }

        if (isUser) Spacer(Modifier.width(10.dp))
    }
}

/** Shown instead of the chat when the active schema has no analysis data yet. */
@Composable
private fun AiComingSoon(modifier: Modifier = Modifier, bottomPadding: Dp = 0.dp) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .padding(bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(AppShapes.pill)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                )
            }
            Box(
                modifier = Modifier
                    .clip(AppShapes.pill)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "SOON",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    letterSpacing = 1.5.sp,
                )
            }
            Text(
                text = "AI analysis is coming soon",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "We're still wiring up the analysis data for this tracker. " +
                    "The assistant will light up here once its sheets are ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyState(
    onSuggestionClick: (String) -> Unit,
    onShowSummary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = remember {
        listOf(
            "How much did I spend on food?",
            "tell me a joke",
        )
    }
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(AppShapes.pill)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        }

        // Summary shortcut — opens the spending-by-category sheet
        Text(
            text = "Try one of these to get started",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Ask me about your finances",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        SuggestionChip(
            text =  "Show spending by category",
            onClick = onShowSummary,
            highlighted = true,
        )
        suggestions.forEach { suggestion ->
            SuggestionChip(text = suggestion, onClick = { onSuggestionClick(suggestion) })
        }

    }
}

@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    BounceSurface(
        onClick = onClick,
        shape = AppShapes.field,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    value: String,
    isLoading: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    bottomPadding: Dp = 0.dp,
    hint: String = "Ask about your finances…",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp + bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val inputBounce = rememberPressBounce(pressedScale = 0.98f)
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= 4_000) onValueChange(it) },
            modifier = Modifier.weight(1f).then(inputBounce.modifier),
            placeholder = { Text(hint, fontSize = 13.sp) },
            maxLines = 3,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            shape = AppShapes.card,
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            interactionSource = inputBounce.interactionSource,
        )

        val sendBounce = rememberPressBounce(pressedScale = 0.85f)
        FilledIconButton(
            onClick = onSend,
            enabled = value.isNotBlank() && !isLoading,
            modifier = Modifier.size(48.dp).then(sendBounce.modifier),
            shape = AppShapes.pill,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            interactionSource = sendBounce.interactionSource,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    painter = painterResource(Res.drawable.send),
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
@Preview
fun PreviewChatInputBar() {
    ChatInputBar(
        value = "",
        isLoading = false,
        onValueChange = {},
        onSend = {},
    )
}
