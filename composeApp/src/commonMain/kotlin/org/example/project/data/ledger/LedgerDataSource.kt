package org.example.project.data.ledger

import org.example.project.model.Transaction

/**
 * Where a session's ledger lives.
 *  - [DEVICE]: this phone's storage — mobile users without an account.
 *  - [CLOUD]: the account's own Firestore collection — every account (always on the web).
 *  - [SHEETS]: the household Google Sheet — a developer-only option for accounts granted it in
 *    the Firebase console (see [org.example.project.data.sheets.SheetsAccessStore]).
 */
enum class LedgerSource { DEVICE, CLOUD, SHEETS }

/** A label column that can be renamed across the whole ledger. */
enum class LabelField { CATEGORY, PAYMENT_MODE }

/**
 * One ledger row (income or expense). [amount] is always positive; [isIncome] tells the sides
 * apart. [monthNumber] is 1..12 (0 if unknown); [date] is the stored date text, kept as-is for
 * display.
 *
 * [id] identifies the row within its backend so it can be deleted: the Firestore document id, or
 * the 1-based sheet row number for Google Sheets. [modeOfPayment] and [isPaid] power the Paid &
 * Unpaid screen; sources without those columns leave the defaults (blank / unpaid).
 */
data class LedgerEntry(
    val id: String = "",
    val description: String,
    val amount: Double,
    val category: String,
    val monthNumber: Int,
    val date: String = "",
    val modeOfPayment: String = "",
    val isPaid: Boolean = false,
    val isIncome: Boolean = false,
)

/**
 * One calendar year of a ledger: its rows, plus how far back the whole ledger goes so the year
 * picker knows which years to offer.
 */
data class LedgerYear(
    val year: Int,
    /** Rows dated in [year] (income and expenses), oldest first. */
    val entries: List<LedgerEntry>,
    /** Earliest year with any row; null when the ledger is empty. */
    val earliestYear: Int?,
) {
    val expenses: List<LedgerEntry> get() = entries.filterNot { it.isIncome }
}

/** Outcome of a ledger write. [errorMessage] is user-facing (never a raw URL or response body). */
data class AddTransactionResult(
    val success: Boolean,
    val errorMessage: String? = null,
)

/** Read + write access to one ledger backend. Picked per session by [org.example.project.repository.LedgerRepository]. */
interface LedgerDataSource {

    /**
     * One calendar year of rows. Reads are scoped by date at the source — Firestore filters
     * server-side, the sheet filters the rows it returns — so the app never pulls a whole ledger.
     */
    suspend fun readYear(year: Int): LedgerYear

    suspend fun addTransaction(transaction: Transaction): AddTransactionResult

    /** Permanently removes [entry] (identified by [LedgerEntry.id]). Throws a user-facing error on failure. */
    suspend fun deleteEntry(entry: LedgerEntry)

    /**
     * Renames a label on every row that carries it — a category or payment mode was renamed, and
     * past transactions should follow. Returns how many rows changed.
     */
    suspend fun relabel(field: LabelField, from: String, to: String): Int
}
