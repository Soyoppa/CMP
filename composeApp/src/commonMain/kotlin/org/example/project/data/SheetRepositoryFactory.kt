package org.example.project.data

import org.example.project.config.ConfigManager

/**
 * Picks the [SheetRepository] implementation based on the build-time
 * `SHEET_SCHEMA` value in `local.properties`.
 *
 * To add a new schema: implement [SheetRepository], add a branch here, and
 * document the value in `local.properties.example` + SETUP.md.
 */
object SheetRepositoryFactory {

    const val SCHEMA_TRACKER_1 = "tracker_1"
    const val SCHEMA_TRACKER_2 = "tracker_2"

    fun create(): SheetRepository {
        return when (val schema = ConfigManager.getConfig().sheetSchema) {
            SCHEMA_TRACKER_1 -> Tracker1Repository()
            SCHEMA_TRACKER_2 -> Tracker2Repository()
            else -> error("Unknown SHEET_SCHEMA='$schema' (expected $SCHEMA_TRACKER_1 or $SCHEMA_TRACKER_2)")
        }
    }
}
