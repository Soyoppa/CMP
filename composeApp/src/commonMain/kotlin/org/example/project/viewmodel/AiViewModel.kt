package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.auth.Session
import org.example.project.auth.aiCharLimit
import org.example.project.auth.aiMessageLimit
import org.example.project.data.AiRepository
import org.example.project.data.OllamaMessage
import org.example.project.data.ai.AiUsageTracker
import org.example.project.model.CategorySummary
import org.example.project.model.ChatMessage
import org.example.project.repository.TransactionRepository
import kotlinx.datetime.Clock
import org.example.project.config.ConfigManager
import org.example.project.config.SchemaFeatures

data class AiUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingTransactions: Boolean = false,
    val error: String? = null,
    val transactionsLoaded: Boolean = false,
    /** True once a guest has used up their free message allowance. */
    val guestLocked: Boolean = false,
)

class AiViewModel(
    private val aiRepository: AiRepository = AiRepository(),
    private val transactionRepository: TransactionRepository = TransactionRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    // Conversation history for context (Ollama format)
    private val conversationHistory = mutableListOf<OllamaMessage>()

    private var budget: List<CategorySummary> = emptyList()

    // Guest allowance tracking.
    private var guestMessagesSent = 0

    init {
        ConfigManager.reset()
        // Skip the budget fetch when this schema has no analysis data — the UI shows "coming soon".
        if (SchemaFeatures.current().aiAnalysisAvailable) loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTransactions = true) }
            try {
                budget = transactionRepository.getSummary()
                println("💰 [AiViewModel] Loaded ${budget.size} budget categories")
                _uiState.update { it.copy(isLoadingTransactions = false, transactionsLoaded = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingTransactions = false, transactionsLoaded = false) }
            }
        }
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank() || _uiState.value.isLoading) return

        // Guest allowance: cap total messages; lock the composer once exhausted.
        val user = Session.currentUser
        val limit = user?.aiMessageLimit ?: Int.MAX_VALUE
        val charLimit = user?.aiCharLimit ?: 4_000  // 4 000-char hard cap for authenticated users
        if (userInput.length > charLimit) return     // silently drop over-length inputs

        if (user?.isGuest == true && guestMessagesSent >= limit) {
            _uiState.update { it.copy(guestLocked = true) }
            return
        }
        if (user?.isGuest == true) guestMessagesSent++

        val userMsg = ChatMessage(
            id = generateId(),
            role = ChatMessage.Role.USER,
            content = userInput.trim()
        )

        // Add placeholder for assistant response
        val thinkingMsg = ChatMessage(
            id = generateId(),
            role = ChatMessage.Role.ASSISTANT,
            content = "",
            isStreaming = true
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + thinkingMsg,
                isLoading = true,
                error = null,
                guestLocked = user?.isGuest == true && guestMessagesSent >= limit,
            )
        }

        viewModelScope.launch {
            try {
                val result = aiRepository.chat(
                    userMessage = userInput.trim(),
                    budget = budget,
                    history = conversationHistory.toList()
                )

                // Record session usage (powers the CHAT chip + DEBUG usage card). Skip failures.
                if (!result.isError) AiUsageTracker.record(result)

                // Update conversation history
                conversationHistory.add(OllamaMessage("user", userInput.trim()))
                conversationHistory.add(OllamaMessage("assistant", result.text))

                // Replace thinking bubble with real response
                _uiState.update { state ->
                    val updatedMessages = state.messages.dropLast(1) + ChatMessage(
                        id = thinkingMsg.id,
                        role = ChatMessage.Role.ASSISTANT,
                        content = result.text,
                        isStreaming = false,
                        model = result.model,
                        totalTokens = result.totalTokens,
                    )
                    state.copy(messages = updatedMessages, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    val updatedMessages = state.messages.dropLast(1)
                    state.copy(
                        messages = updatedMessages,
                        isLoading = false,
                        error = "Failed to get response: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun clearChat() {
        conversationHistory.clear()
        _uiState.update { it.copy(messages = emptyList(), error = null) }
    }

    private fun generateId(): String =
        "msg_${Clock.System.now().toEpochMilliseconds()}_${(0..9999).random()}"
}
