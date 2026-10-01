package org.example.project.data.config

import org.example.project.model.BudgetPlan
import org.example.project.model.OptionList
import org.example.project.model.UserConfig

/**
 * Persistence for a [UserConfig]: [DeviceConfigStore] on a phone without an account,
 * [CloudConfigStore] for accounts. Picked per session; the UI only talks to
 * [org.example.project.repository.ConfigRepository].
 */
interface ConfigStore {
    /** The saved config; lists and budgets that were never saved read as empty. */
    suspend fun load(): UserConfig

    suspend fun saveList(list: OptionList, items: List<String>)

    suspend fun saveBudget(periodId: String, plan: BudgetPlan)
}
