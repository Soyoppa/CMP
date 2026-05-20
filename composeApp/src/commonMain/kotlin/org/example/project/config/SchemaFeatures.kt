package org.example.project.config

import org.example.project.data.SheetRepositoryFactory
import org.example.project.model.CareofCatagory
import org.example.project.model.TransactionCategory

/**
 * Per-schema UI feature flags. Lets the form render the right fields without
 * leaking schema strings into the UI layer.
 */
data class SchemaFeatures(
    val showIncomeOption: Boolean,
    val showPaidToggle: Boolean,
    val categoryLabel: String,
    val categoryPickerTitle: String,
    val categoryOptions: List<String>,
) {
    companion object {
        fun current(): SchemaFeatures = forSchema(ConfigManager.getConfig().sheetSchema)

        fun forSchema(schema: String): SchemaFeatures = when (schema) {
            SheetRepositoryFactory.SCHEMA_TRACKER_1 -> SchemaFeatures(
                showIncomeOption = true,
                showPaidToggle = true,
                categoryLabel = "Category",
                categoryPickerTitle = "Pick a category",
                categoryOptions = TransactionCategory.entries.map { it.displayName },
            )
            SheetRepositoryFactory.SCHEMA_TRACKER_2 -> SchemaFeatures(
                showIncomeOption = false,
                showPaidToggle = false,
                categoryLabel = "Care-of",
                categoryPickerTitle = "Pick a care-of",
                categoryOptions = CareofCatagory.entries.map { it.displayName },
            )
            else -> error("Unknown SHEET_SCHEMA='$schema'")
        }
    }
}
