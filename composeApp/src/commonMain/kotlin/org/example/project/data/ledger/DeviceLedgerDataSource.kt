package org.example.project.data.ledger

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.example.project.data.device.DeviceStore
import org.example.project.model.Transaction
import org.example.project.util.UserFacingException

/**
 * The ledger of someone using the app without an account: one JSON document per calendar year
 * (`ledger/2026`) in the [DeviceStore], so reading a year never touches the others.
 */
class DeviceLedgerDataSource(private val store: DeviceStore) : LedgerDataSource {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(LedgerRecord.serializer())
    // Writes are read-modify-write on a whole year; serialize them so two saves can't race.
    private val mutex = Mutex()

    override suspend fun readYear(year: Int): LedgerYear = LedgerYear(
        year = year,
        entries = load(year).filter { it.isValid }.sortedWith(LedgerRecord.chronological).map { it.toEntry() },
        earliestYear = years().firstOrNull { load(it).any { r -> r.isValid } },
    )

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun addTransaction(transaction: Transaction): AddTransactionResult {
        val record = LedgerRecord.from(transaction, id = Uuid.random().toString(), createdAt = now())
        val year = record.year ?: throw UserFacingException("Please pick a valid date.")
        mutex.withLock { save(year, load(year) + record) }
        return AddTransactionResult(success = true)
    }

    override suspend fun deleteEntry(entry: LedgerEntry) {
        val year = entry.date.take(4).toIntOrNull() ?: throw UserFacingException("This transaction can't be deleted.")
        mutex.withLock {
            val rows = load(year)
            if (rows.none { it.id == entry.id }) throw UserFacingException("That transaction is already gone.")
            save(year, rows.filterNot { it.id == entry.id })
        }
    }

    override suspend fun relabel(field: LabelField, from: String, to: String): Int = mutex.withLock {
        var changed = 0
        years().forEach { year ->
            val rows = load(year)
            val updated = rows.map { row ->
                when {
                    field == LabelField.CATEGORY && row.category == from -> row.copy(category = to).also { changed++ }
                    field == LabelField.PAYMENT_MODE && row.modeOfPayment == from -> row.copy(modeOfPayment = to).also { changed++ }
                    else -> row
                }
            }
            if (updated != rows) save(year, updated)
        }
        changed
    }

    /** Every stored row, oldest first — what moves to the cloud when the user creates an account. */
    suspend fun allRecords(): List<LedgerRecord> =
        years().flatMap { load(it) }.filter { it.isValid }.sortedWith(LedgerRecord.chronological)

    /** Removes every year once the rows are safely in the cloud. */
    suspend fun clear() = mutex.withLock { years().forEach { store.delete(key(it)) } }

    private suspend fun years(): List<Int> =
        store.keys().mapNotNull { it.removePrefix(PREFIX).takeIf { _ -> it.startsWith(PREFIX) }?.toIntOrNull() }.sorted()

    private suspend fun load(year: Int): List<LedgerRecord> {
        val raw = store.read(key(year)) ?: return emptyList()
        // Never treat unreadable data as empty: the next save would overwrite it for good.
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrElse { throw UserFacingException("Your $year data on this phone couldn't be read.") }
    }

    private suspend fun save(year: Int, rows: List<LedgerRecord>) {
        if (rows.isEmpty()) store.delete(key(year)) else store.write(key(year), json.encodeToString(serializer, rows))
    }

    private fun key(year: Int) = "$PREFIX$year"

    @OptIn(ExperimentalTime::class)
    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val PREFIX = "ledger/"
    }
}
