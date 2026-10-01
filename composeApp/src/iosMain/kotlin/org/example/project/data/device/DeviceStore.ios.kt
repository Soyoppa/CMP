package org.example.project.data.device

import com.russhwolf.settings.NSUserDefaultsSettings
import platform.Foundation.NSUserDefaults

actual fun createDeviceStore(): DeviceStore? = UserDefaultsDeviceStore()

/** The app's own NSUserDefaults suite (sandboxed per app, removed on uninstall). */
private class UserDefaultsDeviceStore : DeviceStore {
    private val settings = NSUserDefaultsSettings(NSUserDefaults(suiteName = "org.example.project.device"))

    override suspend fun read(key: String): String? = settings.getStringOrNull(key)
    override suspend fun write(key: String, value: String) = settings.putString(key, value)
    override suspend fun delete(key: String) = settings.remove(key)
    override suspend fun keys(): Set<String> = settings.keys
}
