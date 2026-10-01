package org.example.project.data.sync

import org.example.project.data.config.ConfigStore
import org.example.project.data.device.DeviceData
import org.example.project.data.ledger.LedgerRecord
import org.example.project.model.OptionList
import org.example.project.repository.ConfigRepository

/** Somewhere a [LedgerRecord] can be written under its own id (the account's cloud ledger). */
fun interface RecordImporter {
    suspend fun importRecord(record: LedgerRecord)
}

/**
 * Moves a phone's data into an account when someone who started without one signs in or signs
 * up, so nothing they logged is left behind:
 *  - option lists are merged (the account's order first, then anything only the phone had)
 *  - budgets are copied for cut-offs the account hasn't budgeted
 *  - transactions are written under their device ids, so a retry never duplicates them
 *
 * The phone's copy is cleared only after everything is written. A failure leaves it in place and
 * the next sign-in (or app launch) simply runs the upload again.
 */
class DeviceDataUploader(
    private val device: DeviceData,
    private val cloudConfig: (uid: String) -> ConfigStore,
    private val cloudLedger: (uid: String) -> RecordImporter,
) {
    suspend fun hasPendingData(): Boolean = device.hasData()

    /** Uploads everything on the phone to [uid]'s account; returns how many transactions moved. */
    suspend fun upload(uid: String): Int {
        val local = device.config.load()
        val records = device.ledger.allRecords()

        val remoteStore = cloudConfig(uid)
        val remote = remoteStore.load()
        OptionList.entries.forEach { list ->
            val merged = merge(remote.items(list), local.items(list))
            if (merged != remote.items(list)) remoteStore.saveList(list, merged)
        }
        local.budgets.forEach { (periodId, plan) ->
            if (!plan.isEmpty && remote.budgets[periodId]?.isEmpty != false) remoteStore.saveBudget(periodId, plan)
        }

        val importer = cloudLedger(uid)
        records.forEach { importer.importRecord(it) }

        device.clear()
        return records.size
    }

    private fun merge(account: List<String>, phone: List<String>): List<String> {
        val extra = phone.filter { item -> account.none { it.equals(item, ignoreCase = true) } }
        return (account + extra).take(ConfigRepository.MAX_ITEMS)
    }
}
