package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.AppContainer
import org.example.project.SessionGraph
import org.example.project.auth.AppUser
import org.example.project.auth.SessionRepository
import org.example.project.model.BudgetCycle
import org.example.project.util.DateUtils
import org.example.project.util.FormatUtils
import org.example.project.util.toUserMessage

enum class DiagnosticKind { IDLE, SUCCESS, WARNING, ERROR }

data class DiagnosticResult(val kind: DiagnosticKind, val message: String) {
    companion object {
        val Idle = DiagnosticResult(DiagnosticKind.IDLE, "Not tested yet")
    }
}

data class SettingsUiState(
    /** Null when using the app without an account (data on this phone only). */
    val email: String? = null,
    val hasAccount: Boolean = false,
    /** How often the user budgets — the Settings toggle. */
    val budgetCycle: BudgetCycle = BudgetCycle.CUT_OFF,
    val isSwitchingCycle: Boolean = false,
    val expenseCategoryCount: Int = 0,
    val incomeCategoryCount: Int = 0,
    val paymentModeCount: Int = 0,
    /** Developer-only: this account may read its ledger from the household sheet. */
    val sheetsGranted: Boolean = false,
    val sheetsEnabled: Boolean = false,
    val isSwitchingSheets: Boolean = false,
    /** Waiting for the user to confirm wiping the phone's data. */
    val confirmErase: Boolean = false,
    val isErasing: Boolean = false,
    val isTestingRead: Boolean = false,
    val readResult: DiagnosticResult = DiagnosticResult.Idle,
    val error: String? = null,
)

sealed interface SettingsEvent {
    data class BudgetCycleSelected(val cycle: BudgetCycle) : SettingsEvent
    data object SignOutClicked : SettingsEvent
    data object EraseClicked : SettingsEvent
    data object EraseConfirmed : SettingsEvent
    data object EraseDismissed : SettingsEvent
    data class SheetsToggled(val enabled: Boolean) : SettingsEvent
    data object TestReadClicked : SettingsEvent
    data object ErrorShown : SettingsEvent
}

/**
 * Settings: the session (account or this-phone-only), counts for the list editors, the
 * developer-only Sheets switch and its read diagnostic, and wiping the phone's data.
 */
class SettingsViewModel(
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository,
    private val session: SessionGraph = AppContainer.session(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        when (val user = session.user) {
            AppUser.Device -> SettingsUiState()
            is AppUser.Account -> SettingsUiState(
                email = user.email,
                hasAccount = true,
                sheetsGranted = user.sheets.granted,
                sheetsEnabled = user.sheets.isActive,
            )
        }
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { session.config.ensureLoaded() }
        viewModelScope.launch {
            session.config.state.collect { configState ->
                val config = configState.config
                _uiState.update {
                    it.copy(
                        budgetCycle = config.cycle,
                        expenseCategoryCount = config.expenseCategories.size,
                        incomeCategoryCount = config.incomeCategories.size,
                        paymentModeCount = config.paymentModes.size,
                    )
                }
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.BudgetCycleSelected -> selectCycle(event.cycle)
            SettingsEvent.SignOutClicked -> viewModelScope.launch { sessionRepository.signOut() }
            SettingsEvent.EraseClicked -> _uiState.update { it.copy(confirmErase = true) }
            SettingsEvent.EraseDismissed -> _uiState.update { it.copy(confirmErase = false) }
            SettingsEvent.EraseConfirmed -> erase()
            is SettingsEvent.SheetsToggled -> toggleSheets(event.enabled)
            SettingsEvent.TestReadClicked -> testRead()
            SettingsEvent.ErrorShown -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun selectCycle(cycle: BudgetCycle) {
        if (_uiState.value.isSwitchingCycle || cycle == _uiState.value.budgetCycle) return
        // Optimistic: the toggle moves at once, and the config flow confirms it (or puts it back).
        _uiState.update { it.copy(budgetCycle = cycle, isSwitchingCycle = true) }
        viewModelScope.launch {
            val result = session.config.setCycle(cycle)
            _uiState.update { state ->
                if (result.isSuccess) state.copy(isSwitchingCycle = false)
                else state.copy(
                    isSwitchingCycle = false,
                    budgetCycle = session.config.config.cycle,
                    error = result.exceptionOrNull()?.toUserMessage("Couldn't change how you budget."),
                )
            }
        }
    }

    private fun erase() {
        if (_uiState.value.isErasing) return
        _uiState.update { it.copy(isErasing = true) }
        viewModelScope.launch {
            // Success ends the session, which disposes this screen; only failures come back here.
            sessionRepository.eraseDeviceData().onFailure { e ->
                _uiState.update {
                    it.copy(isErasing = false, confirmErase = false, error = e.toUserMessage("Couldn't erase the data on this phone."))
                }
            }
        }
    }

    private fun toggleSheets(enabled: Boolean) {
        if (_uiState.value.isSwitchingSheets || !_uiState.value.sheetsGranted) return
        _uiState.update { it.copy(isSwitchingSheets = true, sheetsEnabled = enabled) }
        viewModelScope.launch {
            // Success starts a new session on the other ledger, rebuilding every screen.
            sessionRepository.setSheetsEnabled(enabled).onFailure { e ->
                _uiState.update {
                    it.copy(isSwitchingSheets = false, sheetsEnabled = !enabled, error = e.toUserMessage("Couldn't switch ledgers."))
                }
            }
        }
    }

    private fun testRead() {
        if (_uiState.value.isTestingRead) return
        _uiState.update { it.copy(isTestingRead = true) }
        viewModelScope.launch {
            val result = try {
                val year = DateUtils.today().year
                val recent = session.ledger.readYear(year).entries.takeLast(3).reversed()
                if (recent.isEmpty()) {
                    DiagnosticResult(DiagnosticKind.WARNING, "Read succeeded but the ledger has no transactions in $year.")
                } else {
                    val lines = recent.joinToString("\n") { entry ->
                        val sign = if (entry.isIncome) "+" else "-"
                        "• ${entry.description} — $sign${FormatUtils.money(entry.amount, cents = true)}"
                    }
                    DiagnosticResult(DiagnosticKind.SUCCESS, "Last ${recent.size} transactions:\n$lines")
                }
            } catch (e: Exception) {
                DiagnosticResult(DiagnosticKind.ERROR, e.toUserMessage("Read failed. Check your connection and try again."))
            }
            _uiState.update { it.copy(isTestingRead = false, readResult = result) }
        }
    }
}
