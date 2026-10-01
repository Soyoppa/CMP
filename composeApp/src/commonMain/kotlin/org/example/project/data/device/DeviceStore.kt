package org.example.project.data.device

/**
 * A small key → text store on this device: the whole backend for people who use the app without
 * an account. Values are JSON documents written by [DeviceLedgerDataSource] and
 * [DeviceConfigStore]; keys are short `/`-separated paths such as `ledger/2026`.
 *
 * Only mobile (and desktop, for development) has one — the web always uses an account, so
 * [createDeviceStore] returns null there.
 */
interface DeviceStore {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
    suspend fun delete(key: String)
    suspend fun keys(): Set<String>
}

/** This platform's device storage, or null where the app always needs an account (web). */
expect fun createDeviceStore(): DeviceStore?

/** Process-local [DeviceStore] for tests. */
class InMemoryDeviceStore : DeviceStore {
    private val values = mutableMapOf<String, String>()
    override suspend fun read(key: String): String? = values[key]
    override suspend fun write(key: String, value: String) { values[key] = value }
    override suspend fun delete(key: String) { values.remove(key) }
    override suspend fun keys(): Set<String> = values.keys.toSet()
}
