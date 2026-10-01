package org.example.project.config

import org.example.project.auth.AppUser
import org.example.project.auth.Session
import org.example.project.data.ledger.LedgerSource
import org.example.project.data.sheets.SheetDataSourceFactory
import org.example.project.model.CareOfCategory
import org.example.project.model.IncomeCategory
import org.example.project.model.OptionList
import org.example.project.model.PaymentMode
import org.example.project.model.SpendingBuckets
import org.example.project.model.TransactionCategory
import org.example.project.model.UserConfig

/**
 * What the UI offers for the current session's ledger: which fields the form shows and how
 * spending rolls up for budgets. Lets screens render the right thing without comparing schema
 * strings.
 *
 * Every real user gets [STANDARD]: no built-in options (they create their own) and one budget
 * line per expense category. The household-sheet profiles exist only for the developer-only
 * Sheets ledger, whose rows must use the sheet's own vocabulary.
 */
data class LedgerProfile(
    val showIncomeOption: Boolean,
    val showPaidToggle: Boolean,
    val categoryLabel: String,
    /** Title of the category list editor. */
    val categoryListTitle: String = "Categories",
    /** Whether the ledger carries enough detail for the Summary tab, budgets and AI analysis. */
    val summaryAvailable: Boolean,
    /** Options a list starts with while the account hasn't saved its own. Empty for [STANDARD]. */
    val builtInLists: Map<OptionList, List<String>> = emptyMap(),
    /** Fixed roll-up buckets (household sheet); null = budget each of the user's expense categories. */
    private val fixedBuckets: SpendingBuckets? = null,
) {
    /** The budget lines for [config]: the user's own expense categories, or the sheet's buckets. */
    fun bucketsFor(config: UserConfig): SpendingBuckets =
        fixedBuckets ?: SpendingBuckets.of(config.expenseCategories)

    /** [config] with any never-saved list filled from [builtInLists] (a no-op for [STANDARD]). */
    fun withBuiltIns(config: UserConfig): UserConfig =
        builtInLists.entries.fold(config) { acc, (list, items) ->
            if (acc.items(list).isEmpty()) acc.withItems(list, items) else acc
        }

    companion object {
        fun current(): LedgerProfile = forUser(Session.currentUser)

        fun forUser(user: AppUser?): LedgerProfile =
            if (user?.ledgerSource == LedgerSource.SHEETS) forSheetSchema(ConfigManager.getConfig().sheetSchema)
            else STANDARD

        val STANDARD = LedgerProfile(
            showIncomeOption = true,
            showPaidToggle = true,
            categoryLabel = "Category",
            summaryAvailable = true,
        )

        fun forSheetSchema(schema: String): LedgerProfile = when (schema) {
            SheetDataSourceFactory.SCHEMA_TRACKER_1 -> LedgerProfile(
                showIncomeOption = true,
                showPaidToggle = true,
                categoryLabel = "Category",
                summaryAvailable = true,
                builtInLists = mapOf(
                    OptionList.EXPENSE_CATEGORIES to TransactionCategory.entries.map { it.displayName },
                    OptionList.INCOME_CATEGORIES to IncomeCategory.entries.map { it.displayName },
                    OptionList.PAYMENT_MODES to PaymentMode.entries.map { it.displayName },
                ),
                fixedBuckets = SpendingBuckets.HOUSEHOLD,
            )
            SheetDataSourceFactory.SCHEMA_TRACKER_2 -> LedgerProfile(
                showIncomeOption = false,
                showPaidToggle = false,
                categoryLabel = "Care-of",
                categoryListTitle = "Care-of list",
                // The Tracker 2 sheet has no expense breakdown, so there's nothing to summarise yet.
                summaryAvailable = false,
                builtInLists = mapOf(
                    OptionList.EXPENSE_CATEGORIES to CareOfCategory.entries.map { it.displayName },
                    OptionList.PAYMENT_MODES to PaymentMode.entries.map { it.displayName },
                ),
                fixedBuckets = SpendingBuckets.HOUSEHOLD,
            )
            else -> error("Unknown SHEET_SCHEMA='$schema'")
        }
    }
}
