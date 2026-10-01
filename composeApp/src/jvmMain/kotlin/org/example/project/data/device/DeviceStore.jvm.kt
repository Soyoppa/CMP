package org.example.project.data.device

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Desktop is a development target: it keeps "device" data in the user's home folder.
actual fun createDeviceStore(): DeviceStore? =
    FileDeviceStore(File(System.getProperty("user.home"), ".treasurer/device-data"))

/** One file per key; writes land in a temp file that atomically replaces the old one. */
private class FileDeviceStore(private val dir: File) : DeviceStore {

    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        fileFor(key).takeIf { it.isFile }?.readText()
    }

    override suspend fun write(key: String, value: String) {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val target = fileFor(key)
            val temp = File(dir, target.name + ".tmp")
            temp.writeText(value)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
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

    private fun fileFor(key: String) = File(dir, key.replace('/', '.') + SUFFIX)

    private companion object {
        const val SUFFIX = ".json"
    }
}
