package org.example.project.data.ledger

import org.example.project.data.firestore.FirestoreDocument
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.firestore.boolean
import org.example.project.data.firestore.number
import org.example.project.data.firestore.string
import org.example.project.model.Transaction
import org.example.project.util.DateUtils
import org.example.project.util.UserFacingException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The default ledger: one Firestore document per transaction at `users/{uid}/transactions/{id}`.
 * `firestore.rules` restricts the collection to its owner and validates every field.
 *
 * Document shape: `date` (ISO yyyy-MM-dd), `description`, `inflow`, `outflow`, `category`,
 * `modeOfPayment`, `isPaid`, `createdAt` (epoch millis).
 */
class FirestoreLedgerDataSource(
    private val firestore: FirestoreRestClient,
    private val currentUid: () -> String?,
) : LedgerDataSource {

    private fun collectionPath(): String {
        val uid = currentUid() ?: throw UserFacingException("Please sign in again.")
        return "users/$uid/transactions"
    }

    override suspend fun getExpenses(): List<LedgerEntry> =
        firestore.listDocuments(collectionPath())
            .map(::toStored)
            .filter { it.outflow > 0.0 && it.description.isNotBlank() }
            .sortedWith(compareBy<StoredTransaction> { it.date }.thenBy { it.createdAt })
            .map { stored ->
                LedgerEntry(
                    description = stored.description,
                    amount = stored.outflow,
                    category = stored.category,
                    monthNumber = DateUtils.monthNumberFromDate(stored.date),
                    date = stored.date,
                    modeOfPayment = stored.modeOfPayment,
                    isPaid = stored.isPaid,
                )
            }

    override suspend fun getRecent(limit: Int): List<RecentLedgerEntry> =
        firestore.listDocuments(collectionPath())
            .map(::toStored)
            .sortedByDescending { it.createdAt }
            .take(limit)
            .map { stored ->
                val isInflow = stored.inflow > 0.0
                RecentLedgerEntry(
                    description = stored.description,
                    amount = if (isInflow) stored.inflow else stored.outflow,
                    isInflow = isInflow,
                )
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
    }
}
