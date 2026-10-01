package org.example.project.data.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.example.project.data.device.DeviceStore
import org.example.project.model.BudgetCycle
import org.example.project.model.BudgetPlan
import org.example.project.model.OptionList
import org.example.project.model.UserConfig
import org.example.project.util.UserFacingException

/**
 * Config for someone using the app without an account, as JSON in the [DeviceStore]:
 * `config/lists/{listId}` (an array of names), `config/budgets` (period id → plan) and
 * `config/preferences` (the budgeting cycle).
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
        cycle = loadPreferences().cycle(),
    )

    override suspend fun saveList(list: OptionList, items: List<String>) {
        store.write(listKey(list), json.encodeToString(itemsSerializer, items))
    }

    override suspend fun saveBudget(periodId: String, plan: BudgetPlan) {
        val budgets = loadBudgets() + (periodId to plan)
        store.write(BUDGETS_KEY, json.encodeToString(budgetsSerializer, budgets.mapValues { StoredPlan.of(it.value) }))
    }

    override suspend fun saveCycle(cycle: BudgetCycle) {
        store.write(PREFERENCES_KEY, json.encodeToString(StoredPreferences.serializer(), StoredPreferences(cycle.name)))
    }

    /** Removes every saved list, budget and preference. */
    suspend fun clear() {
        OptionList.entries.forEach { store.delete(listKey(it)) }
        store.delete(BUDGETS_KEY)
        store.delete(PREFERENCES_KEY)
    }

    private suspend fun loadPreferences(): StoredPreferences =
        store.read(PREFERENCES_KEY)
            ?.let { decode(it) { raw -> json.decodeFromString(StoredPreferences.serializer(), raw) } }
            ?: StoredPreferences()

    private suspend fun loadBudgets(): Map<String, BudgetPlan> =
        store.read(BUDGETS_KEY)
            ?.let { decode(it) { raw -> json.decodeFromString(budgetsSerializer, raw) } }
            ?.mapValues { it.value.toPlan() }
            .orEmpty()

    // Unreadable data is an error, never "empty": saving over it would lose it for good.
    private fun <T> decode(raw: String, block: (String) -> T): T =
        runCatching { block(raw) }.getOrElse { throw UserFacingException("Your settings on this phone couldn't be read.") }

    @Serializable
    private data class StoredPreferences(val budgetCycle: String? = null) {
        /** An unknown (or never-saved) value reads as the default cycle. */
        fun cycle(): BudgetCycle = BudgetCycle.entries.firstOrNull { it.name == budgetCycle } ?: BudgetCycle.CUT_OFF
    }

    @Serializable
    private data class StoredPlan(val total: Double = 0.0, val categories: Map<String, Double> = emptyMap()) {
        fun toPlan() = BudgetPlan(total = total, byBucket = categories)

        companion object {
            fun of(plan: BudgetPlan) = StoredPlan(total = plan.total, categories = plan.byBucket.filterValues { it > 0.0 })
        }
    }

    private companion object {
        const val BUDGETS_KEY = "config/budgets"
        const val PREFERENCES_KEY = "config/preferences"
        fun listKey(list: OptionList) = "config/lists/${list.id}"
    }
}
