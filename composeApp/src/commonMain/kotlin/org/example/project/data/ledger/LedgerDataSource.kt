package org.example.project.data.ledger

import org.example.project.model.Transaction

/**
 * Where a signed-in user's ledger lives.
 *  - [CLOUD]: the user's own Firestore collection — the default for every account.
 *  - [SHEETS]: the household Google Sheet — web only, granted per account from the Firebase
 *    console (see [LedgerAccessResolver]).
 */
enum class LedgerSource { CLOUD, SHEETS }

/**
 * One expense row, reduced to what the Summary drill-down, Paid & Unpaid screen and the AI chat
 * context need. [monthNumber] is 1..12 (0 if unknown); [date] is the stored date text, kept as-is
 * for display. Income rows are excluded by the producers.
 *
 * [modeOfPayment] and [isPaid] power the Paid & Unpaid screen; sources without those columns leave
 * the defaults (blank / unpaid).
 */
data class LedgerEntry(
    val description: String,
    val amount: Double,
    val category: String,
    val monthNumber: Int,
    val date: String = "",
    val modeOfPayment: String = "",
    val isPaid: Boolean = false,
)

/**
 * A recent entry reduced to what the read diagnostic needs: a label and a magnitude.
 * [isInflow] distinguishes income/refunds (+) from expenses/charges (−).
 */
data class RecentLedgerEntry(
    val description: String,
    val amount: Double,
    val isInflow: Boolean,
)

/** Outcome of a ledger write. [errorMessage] is user-facing (never a raw URL or response body). */
data class AddTransactionResult(
    val success: Boolean,
    val errorMessage: String? = null,
)

/** Read + write access to one ledger backend. Picked per session by [org.example.project.repository.LedgerRepository]. */
interface LedgerDataSource {

    /** Every expense row with its category + month. */
    suspend fun getExpenses(): List<LedgerEntry>

    /** The most recent [limit] entries (newest first). */
    suspend fun getRecent(limit: Int): List<RecentLedgerEntry>

    suspend fun addTransaction(transaction: Transaction): AddTransactionResult
}
