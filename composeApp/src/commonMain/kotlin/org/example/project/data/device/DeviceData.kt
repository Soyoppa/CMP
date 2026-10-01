package org.example.project.data.device

import org.example.project.data.config.DeviceConfigStore
import org.example.project.data.ledger.DeviceLedgerDataSource

/**
 * Everything kept on this phone for people without an account: their [ledger], their [config],
 * and whether they chose to use the app this way (so the next launch opens straight in).
 */
class DeviceData(private val store: DeviceStore) {

    val ledger = DeviceLedgerDataSource(store)
    val config = DeviceConfigStore(store)

    suspend fun isModeChosen(): Boolean = store.read(MODE_KEY) == MODE_DEVICE

    suspend fun setModeChosen(chosen: Boolean) {
        if (chosen) store.write(MODE_KEY, MODE_DEVICE) else store.delete(MODE_KEY)
    }

    /** True when there are transactions, lists or budgets that haven't moved to an account yet. */
    suspend fun hasData(): Boolean = store.keys().any { it != MODE_KEY }

    /** Wipes the ledger and config (the mode choice is separate: see [setModeChosen]). */
    suspend fun clear() {
        ledger.clear()
        config.clear()
    }

    private companion object {
        const val MODE_KEY = "session/mode"
        const val MODE_DEVICE = "device"
    }
}
