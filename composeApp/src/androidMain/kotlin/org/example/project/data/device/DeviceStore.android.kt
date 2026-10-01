package org.example.project.data.device

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The application context, handed over by `MainActivity` before any UI runs. */
internal object AndroidAppContext {
    lateinit var context: Context
}

actual fun createDeviceStore(): DeviceStore? =
    FileDeviceStore(File(AndroidAppContext.context.filesDir, "device-data"))

/**
 * One file per key in the app's private storage (excluded from other apps, removed on uninstall).
 * Writes go to a temp file first and are renamed over the old one, so a crash mid-write never
 * leaves half a document behind.
 */
private class FileDeviceStore(private val dir: File) : DeviceStore {

    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        fileFor(key).takeIf { it.isFile }?.readText()
    }

    override suspend fun write(key: String, value: String) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = fileFor(key)
        val temp = File(dir, target.name + ".tmp")
        temp.writeText(value)
        if (!temp.renameTo(target)) {
            temp.delete()
            error("Couldn't save data on this phone.")
        }
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { fileFor(key).delete() }
    }

    override suspend fun keys(): Set<String> = withContext(Dispatchers.IO) {
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(SUFFIX) }
            .map { it.name.removeSuffix(SUFFIX).replace('.', '/') }
            .toSet()
    }

    // Keys are app-defined paths ("ledger/2026"); '/' becomes '.' so each key is one flat file.
    private fun fileFor(key: String) = File(dir, key.replace('/', '.') + SUFFIX)

    private companion object {
        const val SUFFIX = ".json"
    }
}
