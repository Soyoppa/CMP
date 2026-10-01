package org.example.project.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.project.config.LedgerProfile
import org.example.project.data.config.ConfigStore
import org.example.project.model.BudgetPeriod
import org.example.project.model.BudgetPlan
import org.example.project.model.OptionList
import org.example.project.model.UserConfig
import org.example.project.util.UserFacingException
import org.example.project.util.toUserMessage

data class ConfigState(
    /** True until the first load finishes. */
    val isLoading: Boolean = true,
    val config: UserConfig = UserConfig(),
    /** The last load failed; screens keep what they have and offer a retry. */
    val error: String? = null,
)

/**
 * The session's option lists and budgets — one instance per session, shared by every screen, so
 * an edit made anywhere (a new category from the Add form, a budget saved in its sheet) shows up
 * everywhere at once through [state].
 *
 * Writes go to the [ConfigStore] first and only then update [state], so the screen never shows
 * something that wasn't saved. Every failure is a [UserFacingException] message.
 */
class ConfigRepository(
    private val store: ConfigStore,
    private val profile: LedgerProfile = LedgerProfile.STANDARD,
) {
    private val _state = MutableStateFlow(ConfigState())
    val state: StateFlow<ConfigState> = _state.asStateFlow()

    val config: UserConfig get() = _state.value.config

    private val loadLock = Mutex()
    private val writeLock = Mutex()
    private var loaded = false

    /** Loads once per session; a no-op afterwards unless the last attempt failed. */
    suspend fun ensureLoaded() = loadLock.withLock { if (!loaded) load() }

    /** Re-reads the store — e.g. when the app comes back to the foreground after edits on the web. */
    suspend fun refresh() = loadLock.withLock { load() }

    private suspend fun load() {
        try {
            val loadedConfig = profile.withBuiltIns(store.load())
            loaded = true
            _state.value = ConfigState(isLoading = false, config = loadedConfig)
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = e.toUserMessage("Couldn't load your settings.")) }
        }
    }

    /** Appends [rawName] to [list]; returns the saved (cleaned-up) name. */
    suspend fun addOption(list: OptionList, rawName: String): Result<String> = mutate { config ->
        val name = clean(rawName, list)
        val items = config.items(list)
        if (items.any { it.equals(name, ignoreCase = true) }) throw UserFacingException("\"$name\" is already in the list.")
        if (items.size >= MAX_ITEMS) throw UserFacingException("You can have up to $MAX_ITEMS ${list.plural}.")
        store.saveList(list, items + name)
        config.withItems(list, items + name) to name
    }

    /**
     * Renames [from] in place (keeping its position). An expense category's budgets follow it;
     * past transactions are relabelled by [org.example.project.domain.config.RenameOptionUseCase].
     */
    suspend fun renameOption(list: OptionList, from: String, rawTo: String): Result<String> = mutate { config ->
        val to = clean(rawTo, list)
        val items = config.items(list)
        if (from !in items) throw UserFacingException("\"$from\" isn't in the list anymore.")
        if (to == from) return@mutate config to to
        if (items.any { it != from && it.equals(to, ignoreCase = true) }) throw UserFacingException("\"$to\" is already in the list.")

        val renamed = items.map { if (it == from) to else it }
        store.saveList(list, renamed)
        var next = config.withItems(list, renamed)
        if (list == OptionList.EXPENSE_CATEGORIES) {
            val moved = config.budgets
                .filterValues { from in it.byBucket }
                .mapValues { (_, plan) -> plan.copy(byBucket = plan.byBucket.mapKeys { (k, _) -> if (k == from) to else k }) }
            moved.forEach { (periodId, plan) -> store.saveBudget(periodId, plan) }
            next = next.copy(budgets = config.budgets + moved)
        }
        next to to
    }

    /** Removes [name] from [list]. Past transactions keep the label they were saved with. */
    suspend fun deleteOption(list: OptionList, name: String): Result<Unit> = mutate { config ->
        val items = config.items(list).filterNot { it == name }
        store.saveList(list, items)
        config.withItems(list, items) to Unit
    }

    suspend fun saveBudget(period: BudgetPeriod, plan: BudgetPlan): Result<Unit> = mutate { config ->
        store.saveBudget(period.id, plan)
        config.copy(budgets = config.budgets + (period.id to plan)) to Unit
    }

    /** Runs one write against the latest config; publishes the result only once it's saved. */
    private suspend fun <T> mutate(block: suspend (UserConfig) -> Pair<UserConfig, T>): Result<T> =
        writeLock.withLock {
            runCatching {
                ensureLoaded()
                if (!loaded) throw UserFacingException(_state.value.error ?: "Couldn't load your settings.")
                val (next, result) = block(_state.value.config)
                _state.update { it.copy(config = next) }
                result
            }.recoverCatching { e -> throw UserFacingException(e.toUserMessage("Couldn't save your changes.")) }
        }

    /** Trims, collapses inner whitespace and caps the length (mirrors firestore.rules). */
    private fun clean(raw: String, list: OptionList): String {
        val name = raw.trim().split(Regex("\\s+")).joinToString(" ").take(MAX_NAME_LENGTH)
        if (name.isEmpty()) throw UserFacingException("Type a ${list.noun} name first.")
        return name
    }

    companion object {
        const val MAX_NAME_LENGTH = 60
        const val MAX_ITEMS = 100
    }
}
