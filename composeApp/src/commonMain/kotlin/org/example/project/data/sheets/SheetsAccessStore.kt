package org.example.project.data.sheets

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.firestore.boolean
import org.example.project.data.firestore.string

/**
 * Whether an account may use — and has switched on — the household Google Sheet ledger.
 *
 * [granted] means the project owner created `users/{uid}/access/ledger { source: "sheets" }` in
 * the Firebase console; `firestore.rules` makes that document read-only for clients, so nobody can
 * grant it to themselves. [enabled] is the account's own developer switch in Settings.
 */
data class SheetsAccess(val granted: Boolean = false, val enabled: Boolean = false) {
    val isActive: Boolean get() = granted && enabled

    companion object {
        val NONE = SheetsAccess()
    }
}

/**
 * Reads the Sheets grant and the developer switch (`users/{uid}/settings/developer`). Store users
 * never have a grant, so they never see the switch.
 *
 * Fails closed: any error, or a build without a gateway URL, reads as no access.
 */
class SheetsAccessStore(
    private val firestore: FirestoreRestClient,
    private val gatewayConfigured: () -> Boolean,
) {

    suspend fun load(uid: String): SheetsAccess {
        if (!gatewayConfigured()) return SheetsAccess.NONE
        val grant = runCatching { firestore.getDocument(grantPath(uid)) }.getOrNull()
        if (grant?.string("source") != SHEETS_GRANT) return SheetsAccess.NONE
        // Until the switch is first touched, a granted account keeps using the sheet (as before it existed).
        val enabled = runCatching { firestore.getDocument(switchPath(uid))?.boolean(USE_SHEETS) }.getOrNull() ?: true
        return SheetsAccess(granted = true, enabled = enabled)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun setEnabled(uid: String, enabled: Boolean) {
        firestore.setDocument(
            switchPath(uid),
            mapOf(USE_SHEETS to enabled, "updatedAt" to Clock.System.now().toEpochMilliseconds()),
        )
    }

    companion object {
        private const val SHEETS_GRANT = "sheets"
        private const val USE_SHEETS = "useSheets"
        fun grantPath(uid: String) = "users/$uid/access/ledger"
        fun switchPath(uid: String) = "users/$uid/settings/developer"
    }
}
