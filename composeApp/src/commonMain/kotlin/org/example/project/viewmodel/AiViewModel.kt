package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.data.AiRepository
import org.example.project.data.OllamaMessage
import org.example.project.model.ChatMessage
import org.example.project.repository.TransactionRepository
import kotlinx.datetime.Clock
import org.example.project.config.ConfigManager

data class AiUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingTransactions: Boolean = false,
    val error: String? = null,
    val transactionsLoaded: Boolean = false
)

class AiViewModel(
    private val aiRepository: AiRepository = AiRepository(),
    private val transactionRepository: TransactionRepository = TransactionRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    // Conversation history for context (Ollama format)
    private val conversationHistory = mutableListOf<OllamaMessage>()

    init {
        ConfigManager.reset() // ensure fresh config on each ViewModel init
        loadTransactions()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTransactions = true) }
            try {
                transactionRepository.getAllTransactions() // preloads into repo cache
                _uiState.update { it.copy(isLoadingTransactions = false, transactionsLoaded = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingTransactions = false, transactionsLoaded = false) }
            }
        }
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank() || _uiState.value.isLoading) return

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
                error = null
            )
        }

        viewModelScope.launch {
            try {
                val transactions = transactionRepository.getAllTransactions()
                println("🤖 [AiViewModel] Sending with ${transactions.size} transactions")
                val response = aiRepository.chat(
                    userMessage = userInput.trim(),
                    transactions = transactions,
                    history = conversationHistory.toList()
                )

                // Update conversation history
                conversationHistory.add(OllamaMessage("user", userInput.trim()))
                conversationHistory.add(OllamaMessage("assistant", response))

                // Replace thinking bubble with real response
                _uiState.update { state ->
                    val updatedMessages = state.messages.dropLast(1) + ChatMessage(
                        id = thinkingMsg.id,
                        role = ChatMessage.Role.ASSISTANT,
                        content = response,
                        isStreaming = false
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

    private fun generateId(): String {
        val timestamp = kotlinx.datetime.DateTimePeriod()
        return "msg_${timestamp}_${(0..9999).random()}"
    }
}
