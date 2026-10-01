package org.example.project.data.ledger

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.example.project.data.firestore.FirestoreDocument
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.firestore.boolean
import org.example.project.data.firestore.number
import org.example.project.data.firestore.string
import org.example.project.data.sync.RecordImporter
import org.example.project.model.Transaction
import org.example.project.util.UserFacingException

/**
 * An account's ledger: one Firestore document per transaction at `users/{uid}/transactions/{id}`.
 * `firestore.rules` restricts the collection to its owner and validates every field.
 *
 * Document shape: `date` (ISO yyyy-MM-dd), `description`, `inflow`, `outflow`, `category`,
 * `modeOfPayment`, `isPaid`, `createdAt` (epoch millis) — a [LedgerRecord].
 *
 * Because `date` is stored ISO, a year is a plain string range (`2026-01-01`..`2026-12-31`) that
 * Firestore can filter server-side with no composite index.
 */
class FirestoreLedgerDataSource(
    private val firestore: FirestoreRestClient,
    uid: String,
) : LedgerDataSource, RecordImporter {

    private val parentPath = "users/$uid"
    private val collectionPath = "$parentPath/$TRANSACTIONS"

    override suspend fun readYear(year: Int): LedgerYear {
        val entries = firestore.queryByRange(
            parentPath = parentPath,
            collectionId = TRANSACTIONS,
            field = FIELD_DATE,
            from = "$year-01-01",
            to = "$year-12-31",
        )
            .map(::toRecord)
            .filter { it.isValid }
            .sortedWith(LedgerRecord.chronological)
            .map { it.toEntry() }
        val earliest = firestore.firstByField(parentPath, TRANSACTIONS, FIELD_DATE)
            ?.fields?.string(FIELD_DATE)?.take(4)?.toIntOrNull()
        return LedgerYear(year = year, entries = entries, earliestYear = earliest)
    }

    override suspend fun deleteEntry(entry: LedgerEntry) {
        if (entry.id.isBlank()) throw UserFacingException("This transaction can't be deleted.")
        firestore.deleteDocument("$collectionPath/${entry.id}")
    }

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    override suspend fun addTransaction(transaction: Transaction): AddTransactionResult {
        val record = LedgerRecord.from(
            transaction,
            id = Uuid.random().toString(),
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        importRecord(record)
        return AddTransactionResult(success = true)
    }

    override suspend fun relabel(field: LabelField, from: String, to: String): Int {
        val name = when (field) {
            LabelField.CATEGORY -> FIELD_CATEGORY
            LabelField.PAYMENT_MODE -> FIELD_PAYMENT_MODE
        }
        val matches = firestore.queryByValue(parentPath, TRANSACTIONS, name, from)
        matches.forEach { doc ->
            firestore.updateFields("$collectionPath/${doc.id}", mapOf(name to to.take(LedgerRecord.MAX_TEXT)))
        }
        return matches.size
    }

    /**
     * Writes [record] under its own id. Idempotent — re-running a device → cloud upload after a
     * failure rewrites the same documents instead of duplicating them.
     */
    override suspend fun importRecord(record: LedgerRecord) {
        firestore.setDocument(
            "$collectionPath/${record.id}",
            mapOf(
                FIELD_DATE to record.date,
                "description" to record.description.take(LedgerRecord.MAX_TEXT),
                "inflow" to record.inflow,
                "outflow" to record.outflow,
                FIELD_CATEGORY to record.category.take(LedgerRecord.MAX_TEXT),
                FIELD_PAYMENT_MODE to record.modeOfPayment.take(LedgerRecord.MAX_TEXT),
                "isPaid" to record.isPaid,
                "createdAt" to record.createdAt,
            ),
        )
    }

    private fun toRecord(document: FirestoreDocument): LedgerRecord {
        val f = document.fields
        return LedgerRecord(
            id = document.id,
            date = f.string(FIELD_DATE).orEmpty(),
            description = f.string("description").orEmpty(),
            inflow = f.number("inflow") ?: 0.0,
            outflow = f.number("outflow") ?: 0.0,
            category = f.string(FIELD_CATEGORY).orEmpty(),
            modeOfPayment = f.string(FIELD_PAYMENT_MODE).orEmpty(),
            isPaid = f.boolean("isPaid") ?: false,
            createdAt = f.number("createdAt")?.toLong() ?: 0L,
        )
    }

    companion object {
        const val TRANSACTIONS = "transactions"
        private const val FIELD_DATE = "date"
        private const val FIELD_CATEGORY = "category"
        private const val FIELD_PAYMENT_MODE = "modeOfPayment"
    }
}
