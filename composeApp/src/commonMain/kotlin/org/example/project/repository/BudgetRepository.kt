package org.example.project.repository

import org.example.project.AppContainer
import org.example.project.auth.Session
import org.example.project.data.ledger.DemoLedgerDataSource
import org.example.project.data.settings.UserSettingsStore
import org.example.project.model.BudgetPeriod
import org.example.project.model.BudgetPlan
import org.example.project.util.UserFacingException

/**
 * Per-user budgets, one [BudgetPlan] per cut-off ([BudgetPeriod]). Resolves the current uid from
 * [Session] so callers never thread it, and keeps reads non-fatal (a failed read means "no
 * budget", not an error, since budgets are optional context for the summary).
 */
class BudgetRepository(
    private val store: UserSettingsStore = AppContainer.userSettings,
) {
    private val uid: String? get() = Session.currentUser?.uid

    /** Saved plans keyed by [BudgetPeriod.id]. Guests get the demo budgets; errors read as none. */
    suspend fun getPlans(): Map<String, BudgetPlan> {
        if (Session.isGuest) return DemoLedgerDataSource.budgetPlans()
        val id = uid ?: return emptyMap()
        return runCatching { store.loadBudgetPlans(id) }.getOrDefault(emptyMap())
    }

    /**
     * What to pre-fill for a cut-off that has no budget yet, so the user reviews rather than
     * retypes: the most recent earlier cut-off's plan, else half of the old per-month budget.
     */
    suspend fun suggestPlan(period: BudgetPeriod, plans: Map<String, BudgetPlan>): BudgetPlan? {
        val latestEarlier = plans
            .filterKeys { key -> BudgetPeriod.fromId(key)?.let { it < period } == true }
            .maxByOrNull { (key, _) -> key }
            ?.value
            ?.takeUnless { it.isEmpty }
        if (latestEarlier != null) return latestEarlier
        val id = uid ?: return null
        return runCatching { store.loadLegacyMonthlyPlan(id) }.getOrNull()?.scaled(0.5)
    }

    /** Persists the budget for [period]; fails cleanly when nobody is signed in. */
    suspend fun savePlan(period: BudgetPeriod, plan: BudgetPlan): Result<Unit> {
        val id = uid?.takeUnless { Session.isGuest }
            ?: return Result.failure(UserFacingException("Sign in to save budgets."))
        return runCatching { store.saveBudgetPlan(id, period.id, plan) }
    }
}
