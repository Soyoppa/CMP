package org.example.project.data.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.example.project.data.device.DeviceStore
import org.example.project.model.BudgetPlan
import org.example.project.model.OptionList
import org.example.project.model.UserConfig
import org.example.project.util.UserFacingException

/**
 * Config for someone using the app without an account, as JSON in the [DeviceStore]:
 * `config/lists/{listId}` (an array of names) and `config/budgets` (cut-off id → plan).
 */
class DeviceConfigStore(private val store: DeviceStore) : ConfigStore {

    private val json = Json { ignoreUnknownKeys = true }
    private val itemsSerializer = ListSerializer(String.serializer())
    private val budgetsSerializer = MapSerializer(String.serializer(), StoredPlan.serializer())

    override suspend fun load(): UserConfig = UserConfig(
        lists = OptionList.entries.associateWith { list ->
            store.read(listKey(list))?.let { decode(it) { raw -> json.decodeFromString(itemsSerializer, raw) } }.orEmpty()
        },
        budgets = loadBudgets(),
    )

    override suspend fun saveList(list: OptionList, items: List<String>) {
        store.write(listKey(list), json.encodeToString(itemsSerializer, items))
    }

    override suspend fun saveBudget(periodId: String, plan: BudgetPlan) {
        val budgets = loadBudgets() + (periodId to plan)
        store.write(BUDGETS_KEY, json.encodeToString(budgetsSerializer, budgets.mapValues { StoredPlan.of(it.value) }))
    }

    /** Removes every saved list and budget. */
    suspend fun clear() {
        OptionList.entries.forEach { store.delete(listKey(it)) }
        store.delete(BUDGETS_KEY)
    }

    private suspend fun loadBudgets(): Map<String, BudgetPlan> =
        store.read(BUDGETS_KEY)
            ?.let { decode(it) { raw -> json.decodeFromString(budgetsSerializer, raw) } }
            ?.mapValues { it.value.toPlan() }
            .orEmpty()

    // Unreadable data is an error, never "empty": saving over it would lose it for good.
    private fun <T> decode(raw: String, block: (String) -> T): T =
        runCatching { block(raw) }.getOrElse { throw UserFacingException("Your settings on this phone couldn't be read.") }

    @Serializable
    private data class StoredPlan(val total: Double = 0.0, val categories: Map<String, Double> = emptyMap()) {
        fun toPlan() = BudgetPlan(total = total, byBucket = categories)

        companion object {
            fun of(plan: BudgetPlan) = StoredPlan(total = plan.total, categories = plan.byBucket.filterValues { it > 0.0 })
        }
    }

    private companion object {
        const val BUDGETS_KEY = "config/budgets"
        fun listKey(list: OptionList) = "config/lists/${list.id}"
    }
}
