package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.AppContainer
import org.example.project.data.ai.AiRepository
import org.example.project.data.ai.AiUsageTracker
import org.example.project.data.ai.ChatTurn
import org.example.project.data.ledger.LedgerEntry
import org.example.project.model.ChatMessage
import org.example.project.repository.LedgerRepository
import org.example.project.util.DateUtils

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingTransactions: Boolean = false,
    val error: String? = null,
    val transactionsLoaded: Boolean = false,
)

sealed interface ChatEvent {
    data class SendClicked(val text: String) : ChatEvent
    data object ErrorShown : ChatEvent
    data object ClearClicked : ChatEvent
}

/**
 * The AI assistant chat. Each message reads the ledger fresh, so answers reflect current data
 * rather than a snapshot from when the chat opened.
 */
class ChatViewModel(
    private val aiRepository: AiRepository = AppContainer.aiRepository,
    private val ledgerRepository: LedgerRepository = AppContainer.session().ledger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val conversationHistory = mutableListOf<ChatTurn>()
    private var nextMessageId = 0L

    fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.SendClicked -> sendMessage(event.text)
            ChatEvent.ErrorShown -> _uiState.update { it.copy(error = null) }
            ChatEvent.ClearClicked -> {
                conversationHistory.clear()
                _uiState.update { it.copy(messages = emptyList(), error = null) }
            }
        }
    }

    /**
     * Fresh read of this calendar year's expenses for the turn's context — the prompt talks about
     * this month and year-to-date, so a year is exactly the window it needs. Empty (not thrown) on
     * failure.
     */
    private suspend fun fetchLedger(): List<LedgerEntry> {
        _uiState.update { it.copy(isLoadingTransactions = true) }
        return try {
            ledgerRepository.expensesIn(DateUtils.today().year).also {
                _uiState.update { s -> s.copy(isLoadingTransactions = false, transactionsLoaded = true) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoadingTransactions = false, transactionsLoaded = false) }
            emptyList()
        }
    }

    private fun sendMessage(userInput: String) {
        val text = userInput.trim()
        if (text.isEmpty() || _uiState.value.isLoading) return

        if (text.length > MAX_MESSAGE_LENGTH) return // the composer enforces this; guard anyway

        val userMsg = ChatMessage(id = newMessageId(), role = ChatMessage.Role.USER, content = text)
        val thinkingMsg = ChatMessage(id = newMessageId(), role = ChatMessage.Role.ASSISTANT, content = "", isStreaming = true)

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + thinkingMsg,
                isLoading = true,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                val result = aiRepository.chat(
                    userMessage = text,
                    transactions = fetchLedger(),
                    history = conversationHistory.toList(),
                )
                // Session usage powers the chat chip + Settings usage card; failures don't count.
                if (!result.isError) AiUsageTracker.record(result)

                conversationHistory += ChatTurn("user", text)
                conversationHistory += ChatTurn("assistant", result.text)

                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.dropLast(1) + ChatMessage(
                            id = thinkingMsg.id,
                            role = ChatMessage.Role.ASSISTANT,
                            content = result.text,
                            model = result.model,
                            totalTokens = result.totalTokens,
                        ),
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.dropLast(1),
                        isLoading = false,
                        error = "Failed to get a response. Please try again.",
                    )
                }
            }
        }
    }

    private fun newMessageId(): String = "msg_${nextMessageId++}"

    companion object {
        /** Longest message the composer accepts. */
        const val MAX_MESSAGE_LENGTH = 4_000
    }
}
