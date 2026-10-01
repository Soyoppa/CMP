package org.example.project.data.ledger

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.example.project.data.firestore.FirestoreDocument
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.firestore.boolean
import org.example.project.data.firestore.number
import org.example.project.data.firestore.string
import org.example.project.model.Transaction
import org.example.project.util.DateUtils
import org.example.project.util.UserFacingException

/**
 * The default ledger: one Firestore document per transaction at `users/{uid}/transactions/{id}`.
 * `firestore.rules` restricts the collection to its owner and validates every field.
 *
 * Document shape: `date` (ISO yyyy-MM-dd), `description`, `inflow`, `outflow`, `category`,
 * `modeOfPayment`, `isPaid`, `createdAt` (epoch millis).
 *
 * Because `date` is stored ISO, a year is a plain string range (`2026-01-01`..`2026-12-31`) that
 * Firestore can filter server-side with no composite index.
 */
class FirestoreLedgerDataSource(
    private val firestore: FirestoreRestClient,
    private val currentUid: () -> String?,
) : LedgerDataSource {

    private fun uid(): String = currentUid() ?: throw UserFacingException("Please sign in again.")

    private fun parentPath(): String = "users/${uid()}"

    private fun collectionPath(): String = "${parentPath()}/$TRANSACTIONS"

    override suspend fun readYear(year: Int): LedgerYear {
        val parent = parentPath()
        val entries = firestore.queryByRange(
            parentPath = parent,
            collectionId = TRANSACTIONS,
            field = FIELD_DATE,
            from = "$year-01-01",
            to = "$year-12-31",
        )
            .map(::toStored)
            .filter { it.description.isNotBlank() && (it.inflow > 0.0 || it.outflow > 0.0) }
            // The query orders by date; createdAt breaks ties within the same day.
            .sortedWith(compareBy<StoredTransaction> { it.date }.thenBy { it.createdAt })
            .map { stored ->
                val isIncome = stored.inflow > 0.0
                LedgerEntry(
                    id = stored.id,
                    description = stored.description,
                    amount = if (isIncome) stored.inflow else stored.outflow,
                    category = stored.category,
                    monthNumber = DateUtils.monthNumberFromDate(stored.date),
                    date = stored.date,
                    modeOfPayment = stored.modeOfPayment,
                    isPaid = stored.isPaid,
                    isIncome = isIncome,
                )
            }
        val earliest = firestore.firstByField(parent, TRANSACTIONS, FIELD_DATE)
            ?.fields?.string(FIELD_DATE)?.take(4)?.toIntOrNull()
        return LedgerYear(year = year, entries = entries, earliestYear = earliest)
    }

    override suspend fun deleteEntry(entry: LedgerEntry) {
        if (entry.id.isBlank()) throw UserFacingException("This transaction can't be deleted.")
        firestore.deleteDocument("${collectionPath()}/${entry.id}")
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun addTransaction(transaction: Transaction): AddTransactionResult {
        firestore.createDocument(
            collectionPath(),
            mapOf(
                "date" to toIsoDate(transaction.date),
                "description" to transaction.description.take(MAX_TEXT),
                "inflow" to transaction.inflow,
                "outflow" to transaction.outflow,
                "category" to transaction.category.take(MAX_TEXT),
                "modeOfPayment" to transaction.modeOfPayment.take(MAX_TEXT),
                "isPaid" to transaction.isPaid,
                "createdAt" to Clock.System.now().toEpochMilliseconds(),
            ),
        )
        return AddTransactionResult(success = true)
    }

    /** The form produces M/d/yyyy; store ISO so documents sort and read unambiguously. */
    private fun toIsoDate(formDate: String): String =
        DateUtils.parseDate(formDate)?.toString()
            ?: throw UserFacingException("Please pick a valid date.")

    private fun toStored(document: FirestoreDocument): StoredTransaction {
        val f = document.fields
        return StoredTransaction(
            id = document.id,
            date = f.string("date").orEmpty(),
            description = f.string("description").orEmpty(),
            inflow = f.number("inflow") ?: 0.0,
            outflow = f.number("outflow") ?: 0.0,
            category = f.string("category").orEmpty(),
            modeOfPayment = f.string("modeOfPayment").orEmpty(),
            isPaid = f.boolean("isPaid") ?: false,
            createdAt = f.number("createdAt")?.toLong() ?: 0L,
        )
    }

    private data class StoredTransaction(
        val id: String,
        val date: String,
        val description: String,
        val inflow: Double,
        val outflow: Double,
        val category: String,
        val modeOfPayment: String,
        val isPaid: Boolean,
        val createdAt: Long,
    )

    private companion object {
        /** Mirrors the firestore.rules string limit. */
        const val MAX_TEXT = 200
        const val TRANSACTIONS = "transactions"
        const val FIELD_DATE = "date"
    }
}
