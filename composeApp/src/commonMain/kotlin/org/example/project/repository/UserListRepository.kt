package org.example.project.repository

import org.example.project.auth.Session
import org.example.project.data.lists.UserListStore
import org.example.project.data.lists.createUserListStore

/**
 * Thin facade over [UserListStore] for a single named list (e.g. "categories", "paymentModes").
 * Resolves the current Firebase uid from [Session] so callers never have to thread it, and keeps
 * reads non-fatal (falling back to the caller-supplied defaults on any error or first use).
 *
 * Mirrors [BudgetRepository] — same per-uid cloud shape, generalized to an ordered string list.
 */
class UserListRepository(
    private val listId: String,
    private val store: UserListStore = createUserListStore(),
) {
    private val uid: String? get() = Session.currentUser?.uid

    /** The user's saved list, or [defaults] when not signed in, never saved, or on error. */
    suspend fun getItems(defaults: List<String>): List<String> {
        val id = uid ?: return defaults
        return runCatching { store.load(id, listId) }.getOrNull() ?: defaults
    }

    /**
     * Persists [items] verbatim (order preserved). Returns success/failure so the editor can
     * surface a real error instead of silently dropping the write. Fails cleanly when there is
     * no signed-in user.
     */
    suspend fun saveItems(items: List<String>): Result<Unit> {
        val id = uid ?: return Result.failure(IllegalStateException("Sign in to save changes."))
        return runCatching { store.save(id, listId, items) }
    }
}
