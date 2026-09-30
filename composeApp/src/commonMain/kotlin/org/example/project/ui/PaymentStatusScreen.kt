package org.example.project.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.data.ledger.LedgerEntry
import org.example.project.ui.components.BounceSurface
import org.example.project.ui.components.ChoiceField
import org.example.project.ui.components.ChoicePickerSheet
import org.example.project.ui.components.PaymentBadge
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.ui.theme.AppShapes
import org.example.project.ui.theme.ExpenseTerracotta
import org.example.project.ui.theme.IncomeGreen
import org.example.project.util.FormatUtils
import org.example.project.viewmodel.PaymentStatusEvent
import org.example.project.viewmodel.PaymentStatusFilter
import org.example.project.viewmodel.PaymentStatusUiState
import org.example.project.viewmodel.PaymentStatusViewModel
import org.example.project.viewmodel.createPaymentStatusViewModel

/** Chip label for the "no filter" option in the month row. */
private const val ALL_LABEL = "All"

/** The mode picker's "no filter" entry — spelled out, since it sits in a list of card names. */
private const val ALL_MODES_LABEL = "All modes"

/**
 * Full-screen view of the ledger's Paid checkbox: what's settled, what's still outstanding, and
 * on which card or wallet.
 *
 * Reads the same 'Data Dump' expense rows as the Summary drill-down. Two filters stack on top of
 * each other — mode of payment (All, or one card/wallet) and month — and the two totals at the top
 * always report both sides so narrowing the list never hides half the picture.
 *
 * Read-only: the Paid column is edited in the sheet, not here.
 */
@Composable
fun PaymentStatusScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    viewModel: PaymentStatusViewModel = createPaymentStatusViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PaymentStatusTopBar(onClose = onClose, onRefresh = { viewModel.onEvent(PaymentStatusEvent.Refresh) })

        when {
            uiState.isLoading -> CenteredNotice {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(30.dp),
                )
            }

            uiState.error != null -> CenteredNotice {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Couldn't load the ledger.\n${uiState.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    BounceSurface(
                        onClick = { viewModel.onEvent(PaymentStatusEvent.Refresh) },
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

            else -> PaymentStatusContent(
                state = uiState,
                onStatusSelected = { viewModel.onEvent(PaymentStatusEvent.StatusSelected(it)) },
                onModeSelected = { viewModel.onEvent(PaymentStatusEvent.ModeSelected(it)) },
                onMonthSelected = { viewModel.onEvent(PaymentStatusEvent.MonthSelected(it)) },
            )
        }
    }
}

// ─── Content ─────────────────────────────────────────────────────────────────

@Composable
private fun PaymentStatusContent(
    state: PaymentStatusUiState,
    onStatusSelected: (PaymentStatusFilter) -> Unit,
    onModeSelected: (String?) -> Unit,
    onMonthSelected: (String?) -> Unit,
) {
    var modePickerOpen by remember { mutableStateOf(false) }
    // "All modes" rides in the same list as the real cards so the modal has one flat set of
    // choices; it maps back to a null filter on the way out.
    val modeOptions = remember(state.modes) { listOf(ALL_MODES_LABEL) + state.modes.map { it.name } }
    val modeBadges = remember(state.modes) { state.modes.associate { it.name to it.unpaidCount } }

    // The visible rows are already ordered unpaid-first, so section headers fall out of a single
    // pass over them — flattened here so the list stays one lazy interval instead of ~1000.
    val rows = remember(state.entries) { buildListRows(state.entries) }

    // Lazy, not a scrolling Column: "All modes / All months" can be the whole year's ledger.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusTotalCard(
                    label = "Unpaid",
                    total = state.unpaidTotal,
                    count = state.unpaidCount,
                    accent = ExpenseTerracotta,
                    modifier = Modifier.weight(1f),
                )
                StatusTotalCard(
                    label = "Paid",
                    total = state.paidTotal,
                    count = state.paidCount,
                    accent = IncomeGreen,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Spacer(Modifier.height(14.dp))
            StatusToggle(filter = state.statusFilter, onFilterSelected = onStatusSelected)
        }

        item {
            // A field + modal, not a chip strip: the ledger has a dozen-plus cards and anything
            // past the third scrolls out of sight. Same control as the Add Transaction form.
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                ChoiceField(
                    label = "Mode of payment",
                    placeholder = ALL_MODES_LABEL,
                    selected = state.selectedMode ?: ALL_MODES_LABEL,
                    isExpanded = modePickerOpen,
                    isEnabled = true,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onToggle = { modePickerOpen = !modePickerOpen },
                    leading = { PaymentBadge(name = state.selectedMode ?: ALL_LABEL) },
                )
            }
        }

        item {
            FilterRowLabel(
                text = "Month",
                trailing = state.selectedMonth ?: "$ALL_LABEL months",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    label = ALL_LABEL,
                    selected = state.selectedMonth == null,
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = { onMonthSelected(null) },
                )
                state.months.forEach { month ->
                    FilterChip(
                        label = month.take(3),
                        selected = month == state.selectedMonth,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = { onMonthSelected(month) },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        if (state.entries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyMessage(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is ListRow.Header -> SectionHeader(
                        text = if (row.paid) "Paid" else "Unpaid",
                        accent = if (row.paid) IncomeGreen else ExpenseTerracotta,
                        count = row.count,
                    )
                    is ListRow.Entry -> EntryRow(entry = row.transaction)
                }
            }
        }
    }

    if (modePickerOpen) {
        ChoicePickerSheet(
            title = "Filter by payment mode",
            options = modeOptions,
            selected = state.selectedMode ?: ALL_MODES_LABEL,
            accentColor = MaterialTheme.colorScheme.primary,
            onDismiss = { modePickerOpen = false },
            onSelect = { picked ->
                onModeSelected(picked.takeIf { it != ALL_MODES_LABEL })
                modePickerOpen = false
            },
            showPaymentBadges = true,
            badges = modeBadges,
            badgeColor = ExpenseTerracotta,
        )
    }
}

private fun emptyMessage(state: PaymentStatusUiState): String {
    val where = buildString {
        state.selectedMode?.let { append(" on $it") }
        state.selectedMonth?.let { append(" in $it") }
    }
    return when {
        !state.hasEntries -> "No transactions recorded$where."
        state.statusFilter == PaymentStatusFilter.UNPAID -> "Nothing outstanding$where — all settled."
        state.statusFilter == PaymentStatusFilter.PAID -> "Nothing paid yet$where."
        else -> "No transactions recorded$where."
    }
}

// ─── List row model ───────────────────────────────────────────────────────────

/**
 * One line of the lazy list: either a Paid/Unpaid section header or a transaction.
 *
 * Flattening ahead of time keeps [LazyColumn] to a single `items` interval and gives every line a
 * stable key, so a filter change animates instead of rebuilding the whole list.
 */
private sealed interface ListRow {
    val key: String

    data class Header(val paid: Boolean, val count: Int) : ListRow {
        override val key: String get() = "header-$paid"
    }

    data class Entry(val index: Int, val transaction: LedgerEntry) : ListRow {
        override val key: String get() = "row-$index"
    }
}

/** Walks the unpaid-first list once, opening a header each time the paid/unpaid boundary flips. */
private fun buildListRows(entries: List<LedgerEntry>): List<ListRow> {
    val unpaidCount = entries.count { !it.isPaid }
    val paidCount = entries.size - unpaidCount
    val rows = ArrayList<ListRow>(entries.size + 2)
    entries.forEachIndexed { index, entry ->
        if (index == 0 || entries[index - 1].isPaid != entry.isPaid) {
            rows += ListRow.Header(
                paid = entry.isPaid,
                count = if (entry.isPaid) paidCount else unpaidCount,
            )
        }
        rows += ListRow.Entry(index, entry)
    }
    return rows
}

// ─── Totals ──────────────────────────────────────────────────────────────────

@Composable
private fun StatusTotalCard(
    label: String,
    total: Double,
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(AppShapes.card)
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.35f), AppShapes.card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(AppShapes.pill)
                    .background(accent),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = FormatUtils.money(total),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (count == 1) "1 transaction" else "$count transactions",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

// ─── Status toggle ────────────────────────────────────────────────────────────

@Composable
private fun StatusToggle(
    filter: PaymentStatusFilter,
    onFilterSelected: (PaymentStatusFilter) -> Unit,
) {
    val options = listOf(
        PaymentStatusFilter.ALL to ALL_LABEL,
        PaymentStatusFilter.UNPAID to "Unpaid",
        PaymentStatusFilter.PAID to "Paid",
    )
    val selectedIndex = options.indexOfFirst { it.first == filter }.coerceAtLeast(0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(40.dp)
            .clip(AppShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        val segmentWidth = maxWidth / options.size
        // The sliding pill IS the feedback — matches the Summary screen's view-mode toggle.
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "statusToggleIndicator",
        )
        val indicatorColor by animateColorAsState(
            targetValue = when (filter) {
                PaymentStatusFilter.UNPAID -> ExpenseTerracotta
                PaymentStatusFilter.PAID -> IncomeGreen
                PaymentStatusFilter.ALL -> MaterialTheme.colorScheme.primary
            },
            animationSpec = tween(220),
            label = "statusToggleColor",
        )

        Box(
            modifier = Modifier
                .width(segmentWidth)
                .fillMaxHeight()
                .offset(x = indicatorOffset)
                .padding(4.dp)
                .clip(AppShapes.pill)
                .background(indicatorColor),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEach { (option, label) ->
                val selected = option == filter
                val textColor by animateColorAsState(
                    targetValue = if (selected) Color.White
                                  else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "statusToggleText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(AppShapes.pill)
                        .clickable(indication = null, interactionSource = null) {
                            onFilterSelected(option)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                    )
                }
            }
        }
    }
}

// ─── Filter chips ─────────────────────────────────────────────────────────────

@Composable
private fun FilterRowLabel(text: String, trailing: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.16f)
                      else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "filterChipBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = tween(200),
        label = "filterChipBorder",
    )

    val bounce = rememberPressBounce(pressedScale = 0.94f)
    Row(
        modifier = Modifier
            .then(bounce.modifier)
            .heightIn(min = 40.dp)
            .clip(AppShapes.pill)
            .background(bg)
            .border(width = 1.dp, color = borderColor, shape = AppShapes.pill)
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        trailing?.invoke()
    }
}

// ─── Entry list ───────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String, accent: Color, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(AppShapes.pill)
                .background(accent),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun EntryRow(entry: LedgerEntry) {
    val accent = if (entry.isPaid) IncomeGreen else ExpenseTerracotta
    // Paid rows recede; unpaid rows keep a visible edge so they read as the actionable ones.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = if (entry.isPaid) Color.Transparent else accent.copy(alpha = 0.28f),
                shape = AppShapes.field,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PaymentBadge(name = entry.modeOfPayment, size = 34.dp)

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
                text = entryMeta(entry),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = FormatUtils.money(entry.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = if (entry.isPaid) "Paid" else "Unpaid",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}

/** "Sep 1 · Subscription · Maya" — the parts of a row that aren't the name or the amount. */
private fun entryMeta(entry: LedgerEntry): String =
    listOf(entry.date.trim(), entry.category.trim(), entry.modeOfPayment.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" · ")

// ─── Chrome ──────────────────────────────────────────────────────────────────

@Composable
private fun PaymentStatusTopBar(onClose: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BounceSurface(
            onClick = onClose,
            shape = AppShapes.pill,
            color = MaterialTheme.colorScheme.surfaceContainer,
            pressedScale = 0.9f,
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Paid & Unpaid",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Filter by payment mode and month",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BounceSurface(
            onClick = onRefresh,
            shape = AppShapes.pill,
            color = MaterialTheme.colorScheme.surfaceContainer,
            pressedScale = 0.92f,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                text = "Refresh",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenteredNotice(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
