package org.example.project.data.budget

/**
 * Per-user budget persistence, keyed by Firebase Auth uid.
 *
 * The map is `bucket display name -> monthly budget` (e.g. "Food" -> 8000.0), matching
 * [org.example.project.model.CategoryGroups.buckets]. Absent buckets simply have no budget.
 *
 * Implementations are chosen per platform via [createBudgetStore]:
 *  - wasmJs: Cloud Firestore via the `window.__financeDb` JS bridge (the deployed web target).
 *  - other targets: [InMemoryBudgetStore] until Firebase is wired there (they run BypassAuth).
 */
interface BudgetStore {
    /** Loads the saved budgets for [uid]; empty map if none saved yet. */
    suspend fun load(uid: String): Map<String, Double>

    /** Persists [budgets] for [uid], overwriting the stored values. */
    suspend fun save(uid: String, budgets: Map<String, Double>)
}

/** Platform-selected store. See [BudgetStore]. */
expect fun createBudgetStore(): BudgetStore

/**
 * Process-lifetime, in-memory fallback for targets without a cloud binding. State is shared
 * across instances (via the companion) so a save from the budget editor is visible to the
 * summary within the same run — it just doesn't survive a restart.
 */
class InMemoryBudgetStore : BudgetStore {
    override suspend fun load(uid: String): Map<String, Double> = shared[uid].orEmpty()

    override suspend fun save(uid: String, budgets: Map<String, Double>) {
        shared[uid] = budgets.toMap()
    }

    private companion object {
        val shared = mutableMapOf<String, Map<String, Double>>()
    }
}
