package org.example.project.config

import org.example.project.auth.AppUser
import org.example.project.auth.Session
import org.example.project.data.ledger.LedgerSource
import org.example.project.data.sheets.SheetDataSourceFactory
import org.example.project.model.CareOfCategory
import org.example.project.model.IncomeCategory
import org.example.project.model.PaymentMode
import org.example.project.model.SpendingBuckets
import org.example.project.model.StandardCategories
import org.example.project.model.TransactionCategory

/**
 * What the UI offers for the current session's ledger: which fields the form shows, the default
 * option lists, and how spending rolls up into buckets. Lets screens render the right thing
 * without comparing schema strings.
 *
 * Cloud-ledger accounts (all store users, and guests) get [STANDARD]; accounts granted the
 * household Google Sheet get the profile of the build's `SHEET_SCHEMA`.
 */
data class LedgerProfile(
    val showIncomeOption: Boolean,
    val showPaidToggle: Boolean,
    val categoryLabel: String,
    val categoryPickerTitle: String,
    val categoryOptions: List<String>,
    /** Category list + title shown when the Income type is selected (only when [showIncomeOption]). */
    val incomeCategoryPickerTitle: String,
    val incomeCategoryOptions: List<String>,
    val paymentModeOptions: List<String>,
    /** Whether the ledger carries enough detail for the Summary tab, budgets and AI analysis. */
    val summaryAvailable: Boolean,
    val spendingBuckets: SpendingBuckets,
) {
    companion object {
        fun current(): LedgerProfile = forUser(Session.currentUser)

        fun forUser(user: AppUser?): LedgerProfile =
            if (user?.ledgerSource == LedgerSource.SHEETS) forSheetSchema(ConfigManager.getConfig().sheetSchema)
            else STANDARD

        val STANDARD = LedgerProfile(
            showIncomeOption = true,
            showPaidToggle = true,
            categoryLabel = "Category",
            categoryPickerTitle = "Pick a category",
            categoryOptions = StandardCategories.expense,
            incomeCategoryPickerTitle = "Pick an income source",
            incomeCategoryOptions = StandardCategories.income,
            paymentModeOptions = StandardCategories.paymentModes,
            summaryAvailable = true,
            spendingBuckets = SpendingBuckets.STANDARD,
        )

        fun forSheetSchema(schema: String): LedgerProfile = when (schema) {
            SheetDataSourceFactory.SCHEMA_TRACKER_1 -> LedgerProfile(
                showIncomeOption = true,
                showPaidToggle = true,
                categoryLabel = "Category",
                categoryPickerTitle = "Pick a category",
                categoryOptions = TransactionCategory.entries.map { it.displayName },
                incomeCategoryPickerTitle = "Pick an income source",
                incomeCategoryOptions = IncomeCategory.entries.map { it.displayName },
                paymentModeOptions = PaymentMode.entries.map { it.displayName },
                summaryAvailable = true,
                spendingBuckets = SpendingBuckets.HOUSEHOLD,
            )
            SheetDataSourceFactory.SCHEMA_TRACKER_2 -> LedgerProfile(
                showIncomeOption = false,
                showPaidToggle = false,
                categoryLabel = "Care-of",
                categoryPickerTitle = "Pick a care-of",
                categoryOptions = CareOfCategory.entries.map { it.displayName },
                // No income type for this schema; mirror the default list so the fields are never empty.
                incomeCategoryPickerTitle = "Pick a care-of",
                incomeCategoryOptions = CareOfCategory.entries.map { it.displayName },
                paymentModeOptions = PaymentMode.entries.map { it.displayName },
                // The Tracker 2 sheet has no expense breakdown, so there's nothing to summarise yet.
                summaryAvailable = false,
                spendingBuckets = SpendingBuckets.HOUSEHOLD,
            )
            else -> error("Unknown SHEET_SCHEMA='$schema'")
        }
    }
}
