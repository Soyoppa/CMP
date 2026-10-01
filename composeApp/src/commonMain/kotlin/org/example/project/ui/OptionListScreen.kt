package org.example.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.config.LedgerProfile
import org.example.project.model.OptionList
import org.example.project.ui.components.AppSheet
import org.example.project.ui.components.BounceSurface
import org.example.project.ui.components.CategoryGlyph
import org.example.project.ui.components.PaymentBadge
import org.example.project.ui.components.categoryGlyphKind
import org.example.project.ui.theme.AppShapes
import org.example.project.viewmodel.OptionListEvent
import org.example.project.viewmodel.OptionListUiState
import org.example.project.viewmodel.OptionListViewModel
import org.example.project.viewmodel.createOptionListViewModel

/** The user's categories: expense categories, plus income sources where the ledger has income. */
@Composable
fun CategoriesScreen(onClose: () -> Unit) {
    val profile = LedgerProfile.current()
    val lists = if (profile.showIncomeOption) {
        listOf(OptionList.EXPENSE_CATEGORIES, OptionList.INCOME_CATEGORIES)
    } else {
        listOf(OptionList.EXPENSE_CATEGORIES)
    }
    OptionListScreen(
        title = profile.categoryListTitle,
        viewModel = createOptionListViewModel(lists),
        onClose = onClose,
    )
}

/** The user's payment modes (cash, cards, e-wallets…). */
@Composable
fun PaymentModesScreen(onClose: () -> Unit) {
    OptionListScreen(
        title = "Payment modes",
        viewModel = createOptionListViewModel(listOf(OptionList.PAYMENT_MODES)),
        onClose = onClose,
    )
}

/**
 * Editor for a user-owned option list: add at the top, tap a name to rename it, delete with a
 * confirmation. Every change saves immediately — there's no separate save step to forget.
 */
@Composable
private fun OptionListScreen(
    title: String,
    viewModel: OptionListViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val onEvent = viewModel::onEvent

    AppSheet(title = title, subtitle = "Tap a name to rename it", onClose = onClose) {
        when {
            state.isLoading -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(32.dp),
                )
            }
            state.loadError != null && state.items.isEmpty() -> LoadFailed(
                message = state.loadError!!,
                onRetry = { onEvent(OptionListEvent.RetryClicked) },
                modifier = Modifier.weight(1f),
            )
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.tabs.size > 1) ListTabs(state = state, onSelect = { onEvent(OptionListEvent.TabSelected(it)) })

                AddItemRow(
                    value = state.draft,
                    placeholder = "New ${state.selected.noun}",
                    canAdd = state.canAdd,
                    onValueChange = { onEvent(OptionListEvent.DraftChanged(it)) },
                    onAdd = { onEvent(OptionListEvent.AddClicked) },
                )

                AnimatedVisibility(visible = state.error != null, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = state.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }

                if (state.items.isEmpty()) {
                    EmptyList(list = state.selected)
                } else {
                    state.items.forEach { item ->
                        OptionRow(
                            name = item,
                            list = state.selected,
                            enabled = !state.isSaving,
                            onRename = { onEvent(OptionListEvent.RenameClicked(item)) },
                            onDelete = { onEvent(OptionListEvent.DeleteClicked(item)) },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    state.renaming?.let { item ->
        RenameDialog(
            original = item,
            value = state.renameDraft,
            noun = state.selected.noun,
            renamesPastTransactions = state.renamesPastTransactions,
            isSaving = state.isSaving,
            error = state.error,
            onValueChange = { onEvent(OptionListEvent.RenameDraftChanged(it)) },
            onConfirm = { onEvent(OptionListEvent.RenameConfirmed) },
            onDismiss = { onEvent(OptionListEvent.RenameDismissed) },
        )
    }

    state.pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { onEvent(OptionListEvent.DeleteDismissed) },
            shape = AppShapes.card,
            title = { Text("Delete \"$item\"?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "It won't be offered for new transactions. Past transactions keep it as their label.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { onEvent(OptionListEvent.DeleteConfirmed) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(OptionListEvent.DeleteDismissed) }) { Text("Cancel") }
            },
        )
    }
}

/** Expenses / Income switch for the categories editor, with each list's size. */
@Composable
private fun ListTabs(state: OptionListUiState, onSelect: (OptionList) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.tabs.forEach { list ->
            val selected = list == state.selected
            BounceSurface(
                onClick = { onSelect(list) },
                shape = AppShapes.pill,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                pressedScale = 0.94f,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 40.dp),
            ) {
                Text(
                    text = when (list) {
                        OptionList.EXPENSE_CATEGORIES -> "Expenses"
                        OptionList.INCOME_CATEGORIES -> "Income"
                        OptionList.PAYMENT_MODES -> "Payment modes"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddItemRow(
    value: String,
    placeholder: String,
    canAdd: Boolean,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            shape = AppShapes.field,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.weight(1f),
        )
        BounceSurface(
            onClick = onAdd,
            enabled = canAdd,
            shape = AppShapes.pill,
            color = if (canAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            modifier = Modifier.heightIn(min = 52.dp),
        ) {
            Text(
                text = "Add",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun OptionRow(
    name: String,
    list: OptionList,
    enabled: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Rename", onClick = onRename)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (list == OptionList.PAYMENT_MODES) {
            PaymentBadge(name = name, size = 32.dp)
        } else {
            CategoryGlyph(kind = categoryGlyphKind(name), color = MaterialTheme.colorScheme.primary, size = 22.dp)
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BounceSurface(
            onClick = onDelete,
            enabled = enabled,
            shape = AppShapes.pill,
            color = MaterialTheme.colorScheme.surface,
            pressedScale = 0.92f,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Text(
                text = "Delete",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** First-run state: nothing is pre-filled, so say what goes here and how to start. */
@Composable
private fun EmptyList(list: OptionList) {
    val example = when (list) {
        OptionList.EXPENSE_CATEGORIES -> "Food, Rent, Transport"
        OptionList.INCOME_CATEGORIES -> "Salary, Freelance"
        OptionList.PAYMENT_MODES -> "Cash, GCash, Credit card"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "No ${list.plural} yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Add the ones you actually use — for example $example.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RenameDialog(
    original: String,
    value: String,
    noun: String,
    renamesPastTransactions: Boolean,
    isSaving: Boolean,
    error: String?,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        shape = AppShapes.card,
        title = { Text("Rename $noun", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                    shape = AppShapes.field,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (renamesPastTransactions) {
                    Text(
                        text = "Past transactions labelled \"$original\" are renamed too.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSaving && value.isNotBlank()) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        },
    )
}

@Composable
private fun LoadFailed(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text("Try again", fontWeight = FontWeight.SemiBold) }
    }
}
