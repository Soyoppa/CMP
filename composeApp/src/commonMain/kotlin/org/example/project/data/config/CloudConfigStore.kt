package org.example.project.data.config

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.firestore.number
import org.example.project.model.BudgetPlan
import org.example.project.model.OptionList
import org.example.project.model.UserConfig

/**
 * An account's config in Firestore:
 *  - `users/{uid}/settings/{listId}`: `{ items: [..], updatedAt }`, one document per [OptionList]
 *  - `users/{uid}/budgets/{periodId}`: `{ total, categories: { name: amount }, updatedAt }`, one
 *    document per cut-off
 */
@OptIn(ExperimentalTime::class)
class CloudConfigStore(
    private val firestore: FirestoreRestClient,
    private val uid: String,
) : ConfigStore {

    override suspend fun load(): UserConfig = coroutineScope {
        val lists = OptionList.entries.map { list -> async { list to loadList(list) } }
        val budgets = async {
            firestore.listDocuments(budgetsPath(uid)).associate { doc -> doc.id to doc.fields.toBudgetPlan() }
        }
        UserConfig(lists = lists.awaitAll().toMap(), budgets = budgets.await())
    }

    override suspend fun saveList(list: OptionList, items: List<String>) {
        firestore.setDocument(listPath(uid, list), mapOf(ITEMS to items, UPDATED_AT to now()))
    }

    override suspend fun saveBudget(periodId: String, plan: BudgetPlan) {
        firestore.setDocument(
            "${budgetsPath(uid)}/$periodId",
            mapOf(
                TOTAL to plan.total,
                CATEGORIES to plan.byBucket.filterValues { it > 0.0 },
                UPDATED_AT to now(),
            ),
        )
    }

    private suspend fun loadList(list: OptionList): List<String> {
        val items = firestore.getDocument(listPath(uid, list))?.get(ITEMS) as? List<*> ?: return emptyList()
        return items.filterIsInstance<String>().filter { it.isNotBlank() }
    }

    /**
     * Category amounts live in the `categories` map. Budgets saved before it existed kept them as
     * top-level fields, so those still read (and are folded into the map on the next save).
     */
    private fun Map<String, Any?>.toBudgetPlan(): BudgetPlan {
        val nested = (this[CATEGORIES] as? Map<*, *>).orEmpty()
            .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.toDouble()?.let { key to it } } }
            .toMap()
        val legacy = keys
            .filter { it !in RESERVED_FIELDS }
            .mapNotNull { key -> number(key)?.let { key to it } }
            .toMap()
        return BudgetPlan(total = number(TOTAL) ?: 0.0, byBucket = legacy + nested)
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    companion object {
        private const val ITEMS = "items"
        private const val TOTAL = "total"
        private const val CATEGORIES = "categories"
        private const val UPDATED_AT = "updatedAt"
        private val RESERVED_FIELDS = setOf(TOTAL, CATEGORIES, UPDATED_AT, "totalMonthly")

        fun budgetsPath(uid: String) = "users/$uid/budgets"
        fun settingsPath(uid: String) = "users/$uid/settings"
        fun listPath(uid: String, list: OptionList) = "${settingsPath(uid)}/${list.id}"
    }
}
