package org.example.project.repository

import org.example.project.auth.Session
import org.example.project.data.budget.BudgetStore
import org.example.project.data.budget.createBudgetStore

/**
 * Thin facade over [BudgetStore] the app talks to for per-user budgets. Resolves the current
 * Firebase uid from [Session] so callers never have to thread it, and keeps reads non-fatal
 * (budgets are optional context for the summary — a failure means "no budgets", not an error).
 */
class BudgetRepository(
    private val store: BudgetStore = createBudgetStore(),
) {
    /** No signed-in uid → nothing to load/save (e.g. guests, who are gated off in the UI anyway). */
    private val uid: String? get() = Session.currentUser?.uid

    /** Saved budgets keyed by bucket display name; empty map when none / not signed in / on error. */
    suspend fun getBudgets(): Map<String, Double> {
        val id = uid ?: return emptyMap()
        return runCatching { store.load(id) }.getOrDefault(emptyMap())
    }

    /**
     * Persists [budgets]. Returns success/failure so the editor can surface a real error instead
     * of silently dropping the write. Fails cleanly when there is no signed-in user.
     */
    suspend fun saveBudgets(budgets: Map<String, Double>): Result<Unit> {
        val id = uid ?: return Result.failure(IllegalStateException("Sign in to save budgets."))
        return runCatching { store.save(id, budgets) }
    }
}
