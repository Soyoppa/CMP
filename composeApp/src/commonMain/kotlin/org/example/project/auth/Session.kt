package org.example.project.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.project.data.ledger.LedgerSource
import org.example.project.data.sheets.SheetsAccess

/** Who is using the app, which decides where every piece of their data lives. */
sealed interface AppUser {
    val ledgerSource: LedgerSource

    /** No account (mobile only): transactions, lists and budgets stay on this phone. */
    data object Device : AppUser {
        override val ledgerSource: LedgerSource get() = LedgerSource.DEVICE
    }

    /** A Firebase account: everything lives in Firestore and syncs between the web and phones. */
    data class Account(
        val uid: String,
        val email: String?,
        /** The developer-only household-sheet ledger; [SheetsAccess.NONE] for every store user. */
        val sheets: SheetsAccess = SheetsAccess.NONE,
    ) : AppUser {
        override val ledgerSource: LedgerSource
            get() = if (sheets.isActive) LedgerSource.SHEETS else LedgerSource.CLOUD
    }
}

/** Identifies a session's data: a new key means a fresh set of ViewModels and repositories. */
val AppUser.sessionKey: String
    get() = when (this) {
        AppUser.Device -> "device"
        is AppUser.Account -> "$uid/$ledgerSource"
    }

sealed interface SessionState {
    /** Restoring the previous session at startup. */
    data object Loading : SessionState
    /** Nobody is in: show the welcome / sign-in screen. */
    data object SignedOut : SessionState
    data class Active(val user: AppUser) : SessionState
}

/**
 * Process-wide session, read by the App gate and the session graph. Only [SessionRepository]
 * changes it; everything else observes [state].
 */
object Session {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    /** A one-off message for the signed-in app to show (e.g. after moving data to an account). */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val currentUser: AppUser?
        get() = (_state.value as? SessionState.Active)?.user

    internal fun start(user: AppUser) { _state.value = SessionState.Active(user) }
    internal fun end() { _state.value = SessionState.SignedOut }

    internal fun post(notice: String) { _notice.value = notice }
    fun consumeNotice() { _notice.value = null }
}
