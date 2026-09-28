package org.example.project.data.sheets

import org.example.project.config.ConfigManager
import org.example.project.data.ledger.LedgerDataSource

/**
 * Picks the Google Sheets [LedgerDataSource] for the build-time `SHEET_SCHEMA` in
 * `local.properties`. Only used for accounts granted [org.example.project.data.ledger.LedgerSource.SHEETS].
 *
 * To add a schema: implement [LedgerDataSource], add a branch here and a profile in
 * [org.example.project.config.LedgerProfile].
 */
object SheetDataSourceFactory {

    const val SCHEMA_TRACKER_1 = "tracker_1"
    const val SCHEMA_TRACKER_2 = "tracker_2"

    fun create(gateway: SheetsGatewayClient): LedgerDataSource =
        when (val schema = ConfigManager.getConfig().sheetSchema) {
            SCHEMA_TRACKER_1 -> Tracker1SheetDataSource(gateway)
            SCHEMA_TRACKER_2 -> Tracker2SheetDataSource(gateway)
            else -> error("Unknown SHEET_SCHEMA='$schema' (expected $SCHEMA_TRACKER_1 or $SCHEMA_TRACKER_2)")
        }
}
