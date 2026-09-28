package org.example.project.repository

import org.example.project.AppContainer
import org.example.project.auth.Session
import org.example.project.data.settings.UserSettingsStore
import org.example.project.util.UserFacingException

/**
 * One user-editable option list (e.g. [UserSettingsStore.CATEGORIES_LIST]). Resolves the uid from
 * [Session] and falls back to caller-supplied defaults on first use or any read error.
 */
class UserListRepository(
    private val listId: String,
    private val store: UserSettingsStore = AppContainer.userSettings,
) {
    private val uid: String? get() = Session.currentUser?.uid

    /** The user's saved list, or [defaults] when not signed in, never saved, or on error. */
    suspend fun getItems(defaults: List<String>): List<String> {
        val id = uid ?: return defaults
        return runCatching { store.loadList(id, listId) }.getOrNull() ?: defaults
    }

    /** Persists [items] in order; fails cleanly when nobody is signed in. */
    suspend fun saveItems(items: List<String>): Result<Unit> {
        val id = uid ?: return Result.failure(UserFacingException("Sign in to save changes."))
        return runCatching { store.saveList(id, listId, items) }
    }
}
