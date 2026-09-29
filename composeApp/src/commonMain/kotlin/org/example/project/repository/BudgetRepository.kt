package org.example.project.repository

import org.example.project.AppContainer
import org.example.project.auth.Session
import org.example.project.data.settings.UserSettingsStore
import org.example.project.model.BudgetPlan
import org.example.project.util.UserFacingException

/**
 * Per-user monthly budget plan (overall + per category). Resolves the current uid from [Session]
 * so callers never thread it, and keeps reads non-fatal (a failed read means "no budget", not an
 * error, since budgets are optional context for the summary).
 */
class BudgetRepository(
    private val store: UserSettingsStore = AppContainer.userSettings,
) {
    private val uid: String? get() = Session.currentUser?.uid

    /** The saved plan; empty when none / not signed in / on error. */
    suspend fun getPlan(): BudgetPlan {
        val id = uid ?: return BudgetPlan()
        return runCatching { store.loadBudgetPlan(id) }.getOrDefault(BudgetPlan())
    }

    /** Persists [plan]; fails cleanly when nobody is signed in. */
    suspend fun savePlan(plan: BudgetPlan): Result<Unit> {
        val id = uid ?: return Result.failure(UserFacingException("Sign in to save budgets."))
        return runCatching { store.saveBudgetPlan(id, plan) }
    }
}
