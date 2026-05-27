package org.example.project.config

import org.example.project.data.SheetRepositoryFactory
import org.example.project.model.CareofCatagory
import org.example.project.model.IncomeCategory
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
    /** Category list + title shown when the Income type is selected (only when [showIncomeOption]). */
    val incomeCategoryPickerTitle: String,
    val incomeCategoryOptions: List<String>,
    /** Whether the AI assistant has analysis data (budget sheets) to work with. */
    val aiAnalysisAvailable: Boolean,
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
                incomeCategoryPickerTitle = "Pick an income source",
                incomeCategoryOptions = IncomeCategory.entries.map { it.displayName },
                aiAnalysisAvailable = true,
            )
            SheetRepositoryFactory.SCHEMA_TRACKER_2 -> SchemaFeatures(
                showIncomeOption = false,
                showPaidToggle = false,
                categoryLabel = "Care-of",
                categoryPickerTitle = "Pick a care-of",
                categoryOptions = CareofCatagory.entries.map { it.displayName },
                // No income type for this schema; mirror the default list so the fields are never empty.
                incomeCategoryPickerTitle = "Pick a care-of",
                incomeCategoryOptions = CareofCatagory.entries.map { it.displayName },
                // Analysis/budget sheets for Tracker 2 aren't built yet — AI shows "coming soon".
                aiAnalysisAvailable = false,
            )
            else -> error("Unknown SHEET_SCHEMA='$schema'")
        }
    }
}
