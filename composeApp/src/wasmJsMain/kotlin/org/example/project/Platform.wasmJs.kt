package org.example.project

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val supportsSheetsLedger: Boolean = true
}

actual fun getPlatform(): Platform = WasmPlatform()