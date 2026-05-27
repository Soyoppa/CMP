package org.example.project.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.ai_icon
import kotlinproject.composeapp.generated.resources.send
import kotlinx.coroutines.launch
import org.example.project.data.ai.AiPrefs
import org.example.project.data.ai.AiProviderId
import org.example.project.data.ai.AiUsageTracker
import org.example.project.model.ChatMessage
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.viewmodel.AiViewModel
import org.example.project.viewmodel.createAiViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private val ChatAccent = Color(0xFF00C853)
private val BubbleCorner = 18.dp
private val FieldCorner = RoundedCornerShape(24.dp)
private val ChipCorner = RoundedCornerShape(14.dp)

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: AiViewModel = createAiViewModel(),
    bottomPadding: Dp = 0.dp,
) {
    val uiState by viewModel.uiState.collectAsState()
    val usage by AiUsageTracker.state.collectAsState()
    val showPerMessageTokens by AiPrefs.showPerMessageTokens.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }

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
                    onSuggestionClick = { inputText = it },
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

        ChatInputBar(
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
        )
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
                text = "AI Finance Assistant",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatusPill(
                isLoading = isLoadingTransactions,
                isLoaded = transactionsLoaded,
            )
            // Appears once the first reply lands; cross-fades green↔amber on provider change.
            if (provider != null) {
                ProviderPill(provider = provider, model = model)
            }
        }
        val clearBounce = rememberPressBounce(pressedScale = 0.92f)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(
                    interactionSource = clearBounce.interactionSource,
                    indication = null,
                    onClick = onClearChat,
                )
                .then(clearBounce.modifier)
                .padding(horizontal = 14.dp, vertical = 8.dp),
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
        isLoaded -> "Transactions synced" to ChatAccent
        else -> "No transaction data" to MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
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

/** Which AI backend answered last — green for Gemini, amber for the Ollama fallback. */
@Composable
private fun ProviderPill(provider: AiProviderId, model: String?) {
    val targetColor = when (provider) {
        AiProviderId.GEMINI -> ChatAccent
        AiProviderId.OLLAMA -> Color(0xFFFFB300)
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
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
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
                    .clip(RoundedCornerShape(50))
                    .background(ChatAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.ai_icon),
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
                        if (isUser) ChatAccent
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
                        color = if (isUser) Color.White
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

@Composable
private fun EmptyState(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = remember {
        listOf(
            "How much did I spend on food?",
            "What's my biggest expense this month?",
            "Show my recent income.",
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
                .clip(RoundedCornerShape(50))
                .background(ChatAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.ai_icon),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = "Ask me about your finances",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Try one of these to get started",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        suggestions.forEach { suggestion ->
            SuggestionChip(text = suggestion, onClick = { onSuggestionClick(suggestion) })
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    val bounce = rememberPressBounce(pressedScale = 0.97f)
    Box(
        modifier = Modifier
            .clip(ChipCorner)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                onClick = onClick,
            )
            .then(bounce.modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
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
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).then(inputBounce.modifier),
            placeholder = { Text("Ask about your finances…", fontSize = 13.sp) },
            maxLines = 3,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            shape = FieldCorner,
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ChatAccent,
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
            shape = RoundedCornerShape(50),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = ChatAccent,
                contentColor = Color.White,
            ),
            interactionSource = sendBounce.interactionSource,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
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
