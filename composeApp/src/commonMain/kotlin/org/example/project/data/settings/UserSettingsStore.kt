package org.example.project.data.settings

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.firestore.number
import org.example.project.model.BudgetPlan

/**
 * Per-user settings documents in Firestore, at `users/{uid}/settings/{docId}`:
 *  - `budget`: `{ totalMonthly, <bucket name>: monthly amount, …, updatedAt }`
 *  - `categories` / `paymentModes`: `{ items: [..], updatedAt }`
 *
 * Shared by every platform (REST), replacing the old per-platform stores.
 */
@OptIn(ExperimentalTime::class)
class UserSettingsStore(private val firestore: FirestoreRestClient) {

    /** The saved budget plan; empty when none saved yet. */
    suspend fun loadBudgetPlan(uid: String): BudgetPlan {
        val fields = firestore.getDocument(path(uid, BUDGET_DOC)) ?: return BudgetPlan()
        return BudgetPlan(
            totalMonthly = fields.number(TOTAL_MONTHLY) ?: 0.0,
            byBucket = fields.keys
                .filter { it != UPDATED_AT && it != TOTAL_MONTHLY }
                .mapNotNull { key -> fields.number(key)?.let { key to it } }
                .toMap(),
        )
    }

    suspend fun saveBudgetPlan(uid: String, plan: BudgetPlan) {
        firestore.setDocument(
            path(uid, BUDGET_DOC),
            plan.byBucket + mapOf(TOTAL_MONTHLY to plan.totalMonthly, UPDATED_AT to now()),
        )
    }

    /**
     * Saved items for [listId], in order. Null when the list was never saved (the caller seeds
     * defaults) — distinct from an empty list the user deliberately cleared.
     */
    suspend fun loadList(uid: String, listId: String): List<String>? {
        val fields = firestore.getDocument(path(uid, listId)) ?: return null
        val items = fields[ITEMS] as? List<*> ?: return null
        return items.filterIsInstance<String>().filter { it.isNotBlank() }
    }

    suspend fun saveList(uid: String, listId: String, items: List<String>) {
        firestore.setDocument(path(uid, listId), mapOf(ITEMS to items, UPDATED_AT to now()))
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    companion object {
        const val BUDGET_DOC = "budget"
        const val CATEGORIES_LIST = "categories"
        const val PAYMENT_MODES_LIST = "paymentModes"
        val ALL_DOCS = listOf(BUDGET_DOC, CATEGORIES_LIST, PAYMENT_MODES_LIST)

        private const val ITEMS = "items"
        /** Reserved budget-doc field for the overall monthly budget (never a bucket name). */
        private const val TOTAL_MONTHLY = "totalMonthly"
        private const val UPDATED_AT = "updatedAt"

        fun path(uid: String, docId: String) = "users/$uid/settings/$docId"
    }
}
