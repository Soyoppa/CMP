package org.example.project

/** Per-target facts that common code branches on. */
interface Platform {
    val name: String

    /**
     * Whether accounts granted the household Google Sheet may use it on this target. Only the web
     * build ships the Sheets integration; store builds always use the per-user cloud ledger.
     */
    val supportsSheetsLedger: Boolean get() = false
}

expect fun getPlatform(): Platform
