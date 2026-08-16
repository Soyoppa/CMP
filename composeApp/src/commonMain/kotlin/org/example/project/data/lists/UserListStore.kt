package org.example.project.data.lists

/**
 * Per-user, per-list persistence, keyed by Firebase Auth uid + a list id (e.g. "categories",
 * "paymentModes"). Backs user-editable dropdown options (custom categories, payment modes) the
 * same way [org.example.project.data.budget.BudgetStore] backs budgets.
 *
 * Implementations are chosen per platform via [createUserListStore]:
 *  - wasmJs: Cloud Firestore via the `window.__financeDb` JS bridge (the deployed web target).
 *  - other targets: [InMemoryUserListStore] until Firebase is wired there (they run BypassAuth).
 */
interface UserListStore {
    /** Saved items for [uid]/[listId], preserving order; null when nothing has ever been saved
     * (the caller should seed schema defaults), distinct from an empty list (user cleared it). */
    suspend fun load(uid: String, listId: String): List<String>?

    /** Persists [items] for [uid]/[listId], overwriting the stored list. */
    suspend fun save(uid: String, listId: String, items: List<String>)
}

/** Platform-selected store. See [UserListStore]. */
expect fun createUserListStore(): UserListStore

/**
 * Process-lifetime, in-memory fallback for targets without a cloud binding. State is shared
 * across instances (via the companion) so edits are visible across screens within the same run —
 * it just doesn't survive a restart.
 */
class InMemoryUserListStore : UserListStore {
    override suspend fun load(uid: String, listId: String): List<String>? = shared["$uid/$listId"]

    override suspend fun save(uid: String, listId: String, items: List<String>) {
        shared["$uid/$listId"] = items.toList()
    }

    private companion object {
        val shared = mutableMapOf<String, List<String>>()
    }
}
