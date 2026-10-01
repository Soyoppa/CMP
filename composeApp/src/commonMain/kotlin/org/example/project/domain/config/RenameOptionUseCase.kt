package org.example.project.domain.config

import org.example.project.data.ledger.LabelField
import org.example.project.model.OptionList
import org.example.project.repository.ConfigRepository
import org.example.project.repository.LedgerRepository
import org.example.project.util.UserFacingException

/**
 * Renames a category or payment mode everywhere it appears: the option list, the budgets that
 * use it, and every past transaction — so the Summary keeps one line for it instead of two.
 */
class RenameOptionUseCase(
    private val config: ConfigRepository,
    private val ledger: LedgerRepository,
) {
    suspend operator fun invoke(list: OptionList, from: String, to: String): Result<String> =
        config.renameOption(list, from, to).mapCatching { newName ->
            if (newName != from) {
                runCatching { ledger.relabel(list.labelField, from, newName) }.onFailure {
                    throw UserFacingException("Renamed to \"$newName\", but older transactions still show \"$from\".")
                }
            }
            newName
        }

    private val OptionList.labelField: LabelField
        get() = when (this) {
            OptionList.EXPENSE_CATEGORIES, OptionList.INCOME_CATEGORIES -> LabelField.CATEGORY
            OptionList.PAYMENT_MODES -> LabelField.PAYMENT_MODE
        }
}
