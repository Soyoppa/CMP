package org.example.project.repository

import org.example.project.AppContainer
import org.example.project.auth.Session
import org.example.project.data.settings.UserSettingsStore
import org.example.project.util.UserFacingException

/**
 * Per-user monthly budgets. Resolves the current uid from [Session] so callers never thread it,
 * and keeps reads non-fatal (budgets are optional context for the summary — a failure means
 * "no budgets", not an error).
 */
class BudgetRepository(
    private val store: UserSettingsStore = AppContainer.userSettings,
) {
    private val uid: String? get() = Session.currentUser?.uid

    /** Saved budgets keyed by bucket name; empty map when none / not signed in / on error. */
    suspend fun getBudgets(): Map<String, Double> {
        val id = uid ?: return emptyMap()
        return runCatching { store.loadBudgets(id) }.getOrDefault(emptyMap())
    }

    /** Persists [budgets]; fails cleanly when nobody is signed in. */
    suspend fun saveBudgets(budgets: Map<String, Double>): Result<Unit> {
        val id = uid ?: return Result.failure(UserFacingException("Sign in to save budgets."))
        return runCatching { store.saveBudgets(id, budgets) }
    }
}
