package org.example.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.data.ledger.LedgerEntry
import org.example.project.ui.components.AppSheet
import org.example.project.ui.components.BounceSurface
import org.example.project.ui.components.SheetActionPill
import org.example.project.ui.components.CategoryGlyph
import org.example.project.ui.components.YearStepper
import org.example.project.ui.components.categoryGlyphKind
import org.example.project.ui.theme.AppShapes
import org.example.project.ui.theme.ExpenseTerracotta
import org.example.project.ui.theme.IncomeGreen
import org.example.project.util.DateUtils
import org.example.project.util.FormatUtils
import org.example.project.viewmodel.HistoryFilter
import org.example.project.viewmodel.TransactionHistoryEvent
import org.example.project.viewmodel.TransactionHistoryUiState
import org.example.project.viewmodel.TransactionHistoryViewModel

/**
 * Every transaction the user has logged, newest first and grouped by month, with delete.
 *
 */
@Composable
fun TransactionHistoryScreen(
    onClose: () -> Unit,
    viewModel: TransactionHistoryViewModel = viewModel { TransactionHistoryViewModel() },
) {
    val state by viewModel.uiState.collectAsState()

    AppSheet(
        title = "Transactions",
        subtitle = "Newest first · one year at a time",
        onClose = onClose,
        actions = { SheetActionPill("Refresh") { viewModel.onEvent(TransactionHistoryEvent.Refresh) } },
    ) {
        when {
            state.isLoading -> CenteredBox {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(30.dp),
                )
            }

            state.error != null -> CenteredBox {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    BounceSurface(
                        onClick = { viewModel.onEvent(TransactionHistoryEvent.Refresh) },
                        shape = AppShapes.pill,
                        color = MaterialTheme.colorScheme.primary,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "Try again",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            else -> HistoryContent(
                state = state,
                onYearChange = { viewModel.onEvent(TransactionHistoryEvent.YearSelected(it)) },
                onFilterSelected = { viewModel.onEvent(TransactionHistoryEvent.FilterSelected(it)) },
                onDelete = { viewModel.onEvent(TransactionHistoryEvent.DeleteClicked(it)) },
                onDismissError = { viewModel.onEvent(TransactionHistoryEvent.DeleteErrorShown) },
            )
        }
    }

    state.pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(TransactionHistoryEvent.DeleteDismissed) },
            shape = AppShapes.card,
            title = { Text("Delete transaction?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "“${entry.description}” — ${signedAmount(entry)}${entry.date.takeIf { it.isNotBlank() }?.let { " on $it" }.orEmpty()}. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(TransactionHistoryEvent.DeleteConfirmed) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(TransactionHistoryEvent.DeleteDismissed) }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HistoryContent(
    state: TransactionHistoryUiState,
    onYearChange: (Int) -> Unit,
    onFilterSelected: (HistoryFilter) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
    onDismissError: () -> Unit,
) {
    val entries = state.visibleEntries
    // Month headers in the order entries appear (newest first); undated rows go last.
    val groups = remember(entries) { entries.groupBy { it.monthNumber } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YearStepper(
                year = state.year,
                canGoBack = state.canGoBack,
                canGoForward = !state.isCurrentYear,
                onYearChange = onYearChange,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryFilter.entries.forEach { filter ->
                FilterPill(
                    label = when (filter) {
                        HistoryFilter.ALL -> "All"
                        HistoryFilter.EXPENSES -> "Expenses"
                        HistoryFilter.INCOME -> "Income"
                    },
                    selected = state.filter == filter,
                    onClick = { onFilterSelected(filter) },
                )
            }
        }

        AnimatedVisibility(visible = state.deleteError != null, enter = fadeIn(), exit = fadeOut()) {
            BounceSurface(
                onClick = onDismissError,
                shape = AppShapes.field,
                color = MaterialTheme.colorScheme.errorContainer,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = state.deleteError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (entries.isEmpty()) {
            CenteredBox {
                Text(
                    text = when {
                        state.entries.isNotEmpty() -> "Nothing here for this filter."
                        state.isCurrentYear -> "No transactions yet — add one from the + tab."
                        else -> "No transactions in ${state.year}."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
        ) {
            groups.forEach { (monthNumber, monthEntries) ->
                item(key = "header-$monthNumber") {
                    Text(
                        text = DateUtils.monthName(monthNumber).ifBlank { "Undated" }.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
                    )
                }
                items(monthEntries, key = { entry -> "${entry.id}|${entry.description}|${entry.date}" }) { entry ->
                    HistoryRow(entry = entry, onDelete = { onDelete(entry) })
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: LedgerEntry, onDelete: () -> Unit) {
    val accent = if (entry.isIncome) IncomeGreen else ExpenseTerracotta
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(AppShapes.pill)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            CategoryGlyph(kind = categoryGlyphKind(entry.category), color = accent, size = 20.dp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(entry.date, entry.category, entry.modeOfPayment)
                    .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = signedAmount(entry),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
        )

        BounceSurface(
            onClick = onDelete,
            shape = AppShapes.pill,
            color = Color.Transparent,
            pressedScale = 0.88f,
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Delete ${entry.description}" },
        ) {
            TrashGlyph(color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    BounceSurface(
        onClick = onClick,
        shape = AppShapes.pill,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        pressedScale = 0.94f,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier.heightIn(min = 40.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A small line-art trash can, in the same 24-unit grid style as [CategoryGlyph]. */
@Composable
private fun TrashGlyph(color: Color, size: Dp = 20.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val strokeWidth = 1.9f * u
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), strokeWidth, StrokeCap.Round)
        line(4f, 6.5f, 20f, 6.5f)          // lid
        line(9.5f, 6.5f, 10f, 3.5f)        // handle
        line(10f, 3.5f, 14f, 3.5f)
        line(14f, 3.5f, 14.5f, 6.5f)
        line(6f, 6.5f, 7.2f, 20.5f)        // can sides + base
        line(18f, 6.5f, 16.8f, 20.5f)
        line(7.2f, 20.5f, 16.8f, 20.5f)
        line(10f, 10f, 10.3f, 17f)         // ribs
        line(14f, 10f, 13.7f, 17f)
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** "+P55,000.00" for income, "−P1,200.00" for expenses. */
private fun signedAmount(entry: LedgerEntry): String =
    (if (entry.isIncome) "+" else "−") + FormatUtils.money(entry.amount, cents = true)
