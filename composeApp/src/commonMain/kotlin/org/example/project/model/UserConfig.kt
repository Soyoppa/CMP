package org.example.project.model

/**
 * The user-editable option lists. Every list starts empty — the user builds their own — and can
 * be added to, renamed and pruned at any time.
 *
 * [id] is the storage key (Firestore `settings/{id}`, device `config/lists/{id}`).
 */
enum class OptionList(val id: String, val noun: String, val plural: String) {
    EXPENSE_CATEGORIES("categories", "category", "categories"),
    INCOME_CATEGORIES("incomeCategories", "income source", "income sources"),
    PAYMENT_MODES("paymentModes", "payment mode", "payment modes"),
}

/** Everything a user configures: their option lists and a budget per cut-off. */
data class UserConfig(
    val lists: Map<OptionList, List<String>> = emptyMap(),
    /** Budgets keyed by [BudgetPeriod.id]. */
    val budgets: Map<String, BudgetPlan> = emptyMap(),
) {
    fun items(list: OptionList): List<String> = lists[list].orEmpty()

    val expenseCategories: List<String> get() = items(OptionList.EXPENSE_CATEGORIES)
    val incomeCategories: List<String> get() = items(OptionList.INCOME_CATEGORIES)
    val paymentModes: List<String> get() = items(OptionList.PAYMENT_MODES)

    fun withItems(list: OptionList, items: List<String>): UserConfig = copy(lists = lists + (list to items))

    /**
     * What to pre-fill for a cut-off that has no budget yet, so the user reviews rather than
     * retypes: the most recent earlier cut-off's plan.
     */
    fun suggestedPlan(period: BudgetPeriod): BudgetPlan? =
        budgets
            .filterKeys { key -> BudgetPeriod.fromId(key)?.let { it < period } == true }
            .maxByOrNull { (key, _) -> key }
            ?.value
            ?.takeUnless { it.isEmpty }
}
