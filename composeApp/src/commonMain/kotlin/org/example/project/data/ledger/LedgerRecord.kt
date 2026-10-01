package org.example.project.data.ledger

import kotlinx.serialization.Serializable
import org.example.project.model.Transaction
import org.example.project.util.DateUtils
import org.example.project.util.UserFacingException

/**
 * One stored transaction, in the shape both the device ledger (JSON) and the cloud ledger
 * (Firestore fields) keep. [date] is ISO `yyyy-MM-dd` so rows sort and filter by year as text.
 *
 * [id] is generated on the device that created the row and kept when it moves to the cloud, so
 * re-running an upload never duplicates a transaction.
 */
@Serializable
data class LedgerRecord(
    val id: String,
    val date: String,
    val description: String,
    val inflow: Double = 0.0,
    val outflow: Double = 0.0,
    val category: String = "",
    val modeOfPayment: String = "",
    val isPaid: Boolean = false,
    val createdAt: Long = 0L,
) {
    /** Rows without a description or an amount are noise (half-written or hand-edited). */
    val isValid: Boolean get() = description.isNotBlank() && (inflow > 0.0 || outflow > 0.0)

    val year: Int? get() = date.take(4).toIntOrNull()

    fun toEntry(): LedgerEntry {
        val isIncome = inflow > 0.0
        return LedgerEntry(
            id = id,
            description = description,
            amount = if (isIncome) inflow else outflow,
            category = category,
            monthNumber = DateUtils.monthNumberFromDate(date),
            date = date,
            modeOfPayment = modeOfPayment,
            isPaid = isPaid,
            isIncome = isIncome,
        )
    }

    companion object {
        /** Mirrors the firestore.rules string limit. */
        const val MAX_TEXT = 200

        /** Order rows the way every screen lists them: by date, then by when they were logged. */
        val chronological: Comparator<LedgerRecord> = compareBy<LedgerRecord> { it.date }.thenBy { it.createdAt }

        fun from(transaction: Transaction, id: String, createdAt: Long) = LedgerRecord(
            id = id,
            date = isoDate(transaction.date),
            description = transaction.description.trim().take(MAX_TEXT),
            inflow = transaction.inflow,
            outflow = transaction.outflow,
            category = transaction.category.take(MAX_TEXT),
            modeOfPayment = transaction.modeOfPayment.take(MAX_TEXT),
            isPaid = transaction.isPaid,
            createdAt = createdAt,
        )

        /** The form produces M/d/yyyy; storage is ISO so documents sort and read unambiguously. */
        private fun isoDate(formDate: String): String =
            DateUtils.parseDate(formDate)?.toString() ?: throw UserFacingException("Please pick a valid date.")
    }
}
