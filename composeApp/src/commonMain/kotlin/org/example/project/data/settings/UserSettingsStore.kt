package org.example.project.data.settings

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.firestore.number
import org.example.project.model.BudgetPlan

/**
 * Per-user settings in Firestore:
 *  - `users/{uid}/budgets/{periodId}`: `{ total, <bucket name>: amount, …, updatedAt }` — one
 *    document per cut-off
 *  - `users/{uid}/settings/categories` / `paymentModes`: `{ items: [..], updatedAt }`
 *
 * Shared by every platform (REST), replacing the old per-platform stores.
 */
@OptIn(ExperimentalTime::class)
class UserSettingsStore(private val firestore: FirestoreRestClient) {

    /** Every saved cut-off budget, keyed by [org.example.project.model.BudgetPeriod.id]. */
    suspend fun loadBudgetPlans(uid: String): Map<String, BudgetPlan> =
        firestore.listDocuments(budgetsPath(uid)).associate { doc -> doc.id to doc.fields.toBudgetPlan(TOTAL) }

    /** Saves the budget for one cut-off at `users/{uid}/budgets/{periodId}`. */
    suspend fun saveBudgetPlan(uid: String, periodId: String, plan: BudgetPlan) {
        firestore.setDocument(
            "${budgetsPath(uid)}/$periodId",
            plan.byBucket + mapOf(TOTAL to plan.total, UPDATED_AT to now()),
        )
    }

    /**
     * The pre-cut-off, per-month budget (`settings/budget`), if the account has one. Read-only:
     * it only seeds the first cut-off's suggestion (halved) so nobody starts from a blank form.
     */
    suspend fun loadLegacyMonthlyPlan(uid: String): BudgetPlan? =
        firestore.getDocument(path(uid, BUDGET_DOC))?.toBudgetPlan(LEGACY_TOTAL_MONTHLY)?.takeUnless { it.isEmpty }

    private fun Map<String, Any?>.toBudgetPlan(totalField: String): BudgetPlan = BudgetPlan(
        total = number(totalField) ?: 0.0,
        byBucket = keys
            .filter { it !in RESERVED_BUDGET_FIELDS }
            .mapNotNull { key -> number(key)?.let { key to it } }
            .toMap(),
    )

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
        /** Reserved budget-doc field for the overall budget (never a bucket name). */
        private const val TOTAL = "total"
        private const val LEGACY_TOTAL_MONTHLY = "totalMonthly"
        private val RESERVED_BUDGET_FIELDS = setOf(TOTAL, LEGACY_TOTAL_MONTHLY, UPDATED_AT)

        fun budgetsPath(uid: String) = "users/$uid/budgets"
        private const val UPDATED_AT = "updatedAt"

        fun path(uid: String, docId: String) = "users/$uid/settings/$docId"
    }
}
