package org.example.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.example.project.data.ledger.LedgerEntry
import org.example.project.model.BudgetCycle
import org.example.project.model.BudgetPeriod
import org.example.project.model.BudgetStatus
import org.example.project.model.CategorySummary
import org.example.project.model.SpendingBuckets
import org.example.project.ui.components.BounceSurface
import org.example.project.ui.components.BudgetOverviewCard
import org.example.project.ui.components.YearStepper
import org.example.project.ui.components.CategoryGlyph
import org.example.project.ui.components.categoryGlyphKind
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.ui.theme.AmberBrown
import org.example.project.ui.theme.AppShapes
import org.example.project.ui.theme.ExpenseTerracotta
import org.example.project.ui.theme.GoldenYellow
import org.example.project.ui.theme.IncomeGreen
import org.example.project.ui.theme.SageBright
import org.example.project.ui.theme.SageGreen
import org.example.project.util.FormatUtils
import org.example.project.viewmodel.SummaryEvent
import org.example.project.viewmodel.SummaryViewMode
import org.example.project.viewmodel.SummaryViewModel
import org.example.project.viewmodel.createSummaryViewModel

private val CategoryBarColors = listOf(
    GoldenYellow,
    SageGreen,
    AmberBrown,
    IncomeGreen,
    ExpenseTerracotta,
    SageBright,
)

/**
 * Below this width the Summary header stacks its actions under the title. Sized just above the
 * common small-phone logical width (375dp: iPhone 13 mini, SE) so those devices stack.
 */
private val StackedHeaderWidth = 400.dp

private fun colorForIndex(index: Int): Color = CategoryBarColors[index % CategoryBarColors.size]

/** "—" for nothing spent, otherwise the whole-peso amount. */
private fun formatAmount(amount: Double): String =
    if (amount == 0.0) "—" else FormatUtils.money(amount)

private fun abbreviateAmount(amount: Double): String = when {
    amount == 0.0 -> "—"
    amount >= 1_000_000 -> {
        val m = amount / 1_000_000
        val whole = m.toLong()
        val dec = ((m - whole) * 10).toLong()
        "${if (dec == 0L) "$whole" else "$whole.$dec"}M"
    }
    amount >= 1_000 -> "${(amount / 1_000).toLong()}K"
    else -> "${amount.toLong()}"
}

// ─── Screen entry ────────────────────────────────────────────────────────────

/**
 * The spending summary as a full nav screen (it used to be a [ModalBottomSheet] launched
 * from the chat). [bottomPadding] reserves room under the scroll content so the floating
 * nav pill never covers the last category row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    onOpenBudgets: (() -> Unit)? = null,
    onOpenPaymentStatus: (() -> Unit)? = null,
    viewModel: SummaryViewModel = createSummaryViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        SummaryHeader(
            onEditBudgets = onOpenBudgets,
            onOpenPaymentStatus = onOpenPaymentStatus,
        )

        // Pull down to reload — no refresh button; the content stays visible while it reloads.
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.onEvent(SummaryEvent.Refresh) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                uiState.isLoading -> SummaryLoading()
                uiState.error != null -> SummaryError(
                    message = uiState.error!!,
                    onRetry = { viewModel.onEvent(SummaryEvent.Refresh) },
                )
                uiState.categories.isEmpty() -> SummaryEmpty()
                else -> SummaryContent(
                    categories = uiState.categories,
                    cycle = uiState.cycle,
                    year = uiState.year,
                    isCurrentYear = uiState.isCurrentYear,
                    canGoBack = uiState.canGoBack,
                    onYearChange = { viewModel.onEvent(SummaryEvent.YearSelected(it)) },
                    periods = uiState.periods,
                    selectedPeriod = uiState.selectedPeriod,
                    totalBudgetByPeriod = uiState.totalBudgetByPeriod,
                    viewMode = uiState.viewMode,
                    selectedCategory = uiState.selectedCategory,
                    transactions = uiState.transactions,
                    buckets = uiState.buckets,
                    onPeriodSelected = { viewModel.onEvent(SummaryEvent.PeriodSelected(it)) },
                    onMonthSelected = { viewModel.onEvent(SummaryEvent.MonthSelected(it)) },
                    onViewModeSelected = { viewModel.onEvent(SummaryEvent.ViewModeSelected(it)) },
                    onCategorySelected = { viewModel.onEvent(SummaryEvent.CategorySelected(it)) },
                    onSetBudget = onOpenBudgets,
                )
            }
            Spacer(Modifier.height(bottomPadding))
        }
        }
    }
}

// ─── Content ─────────────────────────────────────────────────────────────────

@Composable
private fun SummaryContent(
    categories: List<CategorySummary>,
    /** Whether the user budgets per cut-off or per month — decides the chips and the copy. */
    cycle: BudgetCycle,
    /** The calendar year on screen: the chart's twelve bars are this year's months. */
    year: Int,
    isCurrentYear: Boolean,
    canGoBack: Boolean,
    onYearChange: (Int) -> Unit,
    periods: List<BudgetPeriod>,
    selectedPeriod: BudgetPeriod?,
    totalBudgetByPeriod: Map<String, Double>,
    viewMode: SummaryViewMode,
    selectedCategory: String?,
    transactions: List<LedgerEntry>,
    /** How a transaction's category maps onto the breakdown's rows. */
    buckets: SpendingBuckets,
    onPeriodSelected: (String) -> Unit,
    onMonthSelected: (String) -> Unit,
    onViewModeSelected: (SummaryViewMode) -> Unit,
    onCategorySelected: (String) -> Unit,
    onSetBudget: (() -> Unit)?,
) {
    // Stable accent per category (by load order), shared by the selector chips, the trend
    // chart, and the breakdown — so a category keeps the same colour everywhere it appears.
    val categoryColors = remember(categories) {
        categories.mapIndexed { i, c -> c.category to colorForIndex(i) }.toMap()
    }
    // Spend per cut-off across every category, keyed by period id.
    val periodTotals = remember(categories, periods) {
        periods.associate { period -> period.id to categories.sumOf { it.spentIn(period.id) } }
    }

    val activeCategory = categories.firstOrNull { it.category == selectedCategory }
    val byCategory = viewMode == SummaryViewMode.BY_CATEGORY && activeCategory != null

    // Amounts are per cut-off everywhere; the chart just aggregates them into one bar per
    // calendar month, which is far easier to scan than two cramped bars per month.
    val periodAmounts = if (byCategory) activeCategory!!.spendByPeriod else periodTotals
    fun budgetOf(period: BudgetPeriod): Double =
        if (byCategory) activeCategory!!.budgetFor(period.id) else totalBudgetByPeriod[period.id] ?: 0.0

    val monthsInOrder = remember(periods) { periods.groupBy { it.monthKey } }
    val monthBars = remember(monthsInOrder, periodAmounts) {
        monthsInOrder.map { (key, inMonth) ->
            MonthBar(
                key = key,
                label = inMonth.first().monthLabel,
                amount = inMonth.sumOf { periodAmounts[it.id] ?: 0.0 },
            )
        }
    }
    // The periods of the month on screen — the chips that pick which one the card describes.
    val monthPeriods = selectedPeriod?.let { monthsInOrder[it.monthKey] }.orEmpty()
    // Bars are monthly, so the reference line is too: the month's period budgets added up.
    val monthBudget = monthPeriods.sumOf(::budgetOf)

    val chartBudget = selectedPeriod?.let(::budgetOf) ?: 0.0
    val chartAccent = if (byCategory) categoryColors[activeCategory!!.category] ?: MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.primary

    val selectedTotal = selectedPeriod?.let { periodAmounts[it.id] } ?: periodAmounts.values.sum()

    // Past years need the year spelled out; "Sep 16–30" alone would be ambiguous.
    val periodLabel = selectedPeriod?.let { p -> if (isCurrentYear) p.label else "${p.label} $year" }
    val cardTitle = buildString {
        if (byCategory) append("${activeCategory!!.category} · ")
        append(periodLabel?.let { "$it budget" } ?: "$year")
    }

    // Remaining budget is the headline; spend sits beside it. A budget only applies to a single
    // period, so "all periods" shows spend alone. The cut-off chips sit inside the card, right
    // above the figures they switch.
    BudgetOverviewCard(
        title = cardTitle,
        status = BudgetStatus(spent = selectedTotal, budget = chartBudget),
        showRemaining = selectedPeriod != null,
        periodNoun = cycle.noun,
        // In By-Category mode the prompt would set the overall budget, which isn't what's shown.
        onSetBudget = onSetBudget.takeIf { !byCategory },
        // Only worth showing when there's a choice to make — never when budgeting monthly.
        selector = if (monthPeriods.size > 1) {
            {
                CutOffChips(
                    periods = monthPeriods,
                    selectedId = selectedPeriod?.id,
                    onSelected = onPeriodSelected,
                )
            }
        } else {
            null
        },
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
    )

    ViewModeToggle(
        mode = viewMode,
        onModeSelected = onViewModeSelected,
    )

    AnimatedVisibility(
        visible = viewMode == SummaryViewMode.BY_CATEGORY,
        enter = fadeIn(tween(180)) + expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ),
        exit = fadeOut(tween(140)) + shrinkVertically(tween(160)),
    ) {
        CategorySelector(
            categories = categories,
            selectedCategory = selectedCategory,
            categoryColors = categoryColors,
            onCategorySelected = onCategorySelected,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        YearStepper(
            year = year,
            canGoBack = canGoBack,
            // Later years can't have entries yet, so forward stops at the current one.
            canGoForward = !isCurrentYear,
            onYearChange = onYearChange,
        )
    }

    MonthBarChart(
        months = monthBars,
        selectedMonthKey = selectedPeriod?.monthKey,
        budget = monthBudget,
        selectedBarColor = chartAccent,
        onMonthSelected = onMonthSelected,
    )

    if (monthBudget > 0) {
        BudgetLegend(budget = monthBudget)
    }

    Spacer(Modifier.height(20.dp))

    // Total mode → the category breakdown for the cut-off. By Category mode → the actual
    // line-items for the selected category + cut-off, ranked high→low.
    AnimatedContent(
        targetState = byCategory,
        transitionSpec = {
            (fadeIn(tween(200)) togetherWith fadeOut(tween(120))).using(SizeTransform(clip = false))
        },
        label = "breakdownSwap",
    ) { showTransactions ->
        if (showTransactions && activeCategory != null) {
            CategoryTransactions(
                transactions = transactions,
                buckets = buckets,
                category = activeCategory.category,
                period = selectedPeriod,
                chartedPeriods = periods,
                accent = chartAccent,
            )
        } else {
            CategoryBreakdown(
                categories = categories,
                selectedPeriodId = selectedPeriod?.id,
                categoryColors = categoryColors,
            )
        }
    }

    Spacer(Modifier.height(8.dp))
}

// ─── Per-category transactions (drill-down) ───────────────────────────────────

@Composable
private fun CategoryTransactions(
    transactions: List<LedgerEntry>,
    buckets: SpendingBuckets,
    category: String,
    /** The selected cut-off, or null for every charted one. */
    period: BudgetPeriod?,
    chartedPeriods: List<BudgetPeriod>,
    accent: Color,
) {
    val items = remember(transactions, buckets, category, period, chartedPeriods) {
        transactions
            .filter {
                buckets.bucketFor(it.category) == category &&
                    BudgetPeriod.of(it).let { p -> if (period != null) p == period else p in chartedPeriods }
            }
            .sortedByDescending { it.amount }
    }
    val whenLabel = period?.label ?: "all cut-offs"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryGlyph(kind = categoryGlyphKind(category), color = accent, size = 18.dp)
                Text(
                    text = "$category · $whenLabel",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (items.isNotEmpty()) {
                Text(
                    text = "high → low",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }

        when {
            items.isEmpty() -> TransactionsNotice(
                text = "No transactions recorded for $category in ${period?.label ?: "these cut-offs"}.",
            )

            else -> items.forEach { txn ->
                TransactionRow(
                    description = txn.description,
                    amount = txn.amount,
                )
            }
        }
    }
}

@Composable
private fun TransactionsNotice(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TransactionRow(
    description: String,
    amount: Double,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatAmount(amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

// ─── View-mode toggle ─────────────────────────────────────────────────────────

@Composable
private fun ViewModeToggle(
    mode: SummaryViewMode,
    onModeSelected: (SummaryViewMode) -> Unit,
) {
    val options = listOf(
        SummaryViewMode.TOTAL to "Total",
        SummaryViewMode.BY_CATEGORY to "By Category",
    )
    val selectedIndex = options.indexOfFirst { it.first == mode }.coerceAtLeast(0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 4.dp)
            .height(40.dp)
            .clip(AppShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        val segmentWidth = maxWidth / options.size
        // The sliding pill IS the feedback — no ripple needed on the segments themselves.
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "toggleIndicator",
        )

        Box(
            modifier = Modifier
                .width(segmentWidth)
                .fillMaxHeight()
                .offset(x = indicatorOffset)
                .padding(4.dp)
                .clip(AppShapes.pill)
                .background(MaterialTheme.colorScheme.primary),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEach { (optionMode, label) ->
                val selected = optionMode == mode
                val textColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                                  else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "toggleText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(AppShapes.pill)
                        .clickable(indication = null, interactionSource = null) {
                            onModeSelected(optionMode)
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

// ─── Category selector ────────────────────────────────────────────────────────

@Composable
private fun CategorySelector(
    categories: List<CategorySummary>,
    selectedCategory: String?,
    categoryColors: Map<String, Color>,
    onCategorySelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { summary ->
            CategoryChip(
                category = summary.category,
                color = categoryColors[summary.category] ?: MaterialTheme.colorScheme.primary,
                selected = summary.category == selectedCategory,
                onClick = { onCategorySelected(summary.category) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    category: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) color.copy(alpha = 0.16f)
                      else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "chipBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) color.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = tween(200),
        label = "chipBorder",
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CategoryGlyph(
            kind = categoryGlyphKind(category),
            color = color,
            size = 16.dp,
        )
        Text(
            text = category,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ─── Bar chart ───────────────────────────────────────────────────────────────

private val ChartHeight = 172.dp
// Bar fill uses 70% of chart height; top 30% is reserved for labels + breathing room
private const val BarAreaFraction = 0.70f
// Bottom margin inside each bar column (month label height ≈ 18dp + 5dp spacer)
private val BarBottomPad = 23.dp
// Top margin inside each bar column (amount label height ≈ 14dp + 3dp spacer)
private val BarTopPad = 17.dp
// Dash pattern for the budget reference line — constant, allocated once at class-load time.
private val BudgetLineDash = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))

/** One chart bar: a calendar month's total spend. */
@Immutable
private data class MonthBar(val key: String, val label: String, val amount: Double)

@Composable
private fun MonthBarChart(
    months: List<MonthBar>,
    selectedMonthKey: String?,
    /** The selected month's budget (its cut-offs added up); 0 hides the reference line. */
    budget: Double,
    selectedBarColor: Color,
    onMonthSelected: (String) -> Unit,
) {
    val maxActual = months.maxOfOrNull { it.amount }?.takeIf { it > 0 } ?: 1.0
    val maxY = maxOf(maxActual, budget).takeIf { it > 0 } ?: 1.0
    val budgetFraction = if (budget > 0) (budget / maxY).toFloat() else -1f

    val budgetLineColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .padding(horizontal = 16.dp)
            .pointerInput(months) {
                if (months.isEmpty()) return@pointerInput
                awaitEachGesture {
                    // Respond to both tap (down) and drag without any minimum distance
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val idx = (down.position.x / size.width * months.size)
                        .toInt().coerceIn(0, months.lastIndex)
                    onMonthSelected(months[idx].key)

                    do {
                        val event = awaitPointerEvent()
                        val pos = event.changes.firstOrNull()?.position ?: break
                        val dragIdx = (pos.x / size.width * months.size)
                            .toInt().coerceIn(0, months.lastIndex)
                        onMonthSelected(months[dragIdx].key)
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        if (budgetFraction > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bottomPad = BarBottomPad.toPx()
                val topPad = BarTopPad.toPx()
                val barDrawingArea = size.height - topPad - bottomPad
                val lineY = size.height - bottomPad - (budgetFraction * barDrawingArea * BarAreaFraction)
                drawLine(
                    color = budgetLineColor.copy(alpha = 0.55f),
                    start = Offset(0f, lineY),
                    end = Offset(size.width, lineY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = BudgetLineDash,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            months.forEachIndexed { index, month ->
                BarColumn(
                    index = index,
                    month = month,
                    fraction = (month.amount / maxY).toFloat(),
                    isSelected = month.key == selectedMonthKey,
                    selectedColor = selectedBarColor,
                )
            }
        }
    }
}

@Composable
private fun RowScope.BarColumn(
    index: Int,
    month: MonthBar,
    fraction: Float,
    isSelected: Boolean,
    selectedColor: Color,
) {
    // Grow each bar up from the baseline, staggered left-to-right on first load — the
    // data "arrives" rather than appearing fully formed (Monarch Money's chart intro).
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        started = true
    }
    val animatedFraction by animateFloatAsState(
        targetValue = if (started) fraction * BarAreaFraction else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "bar_${month.key}",
    )

    // Animate the accent so switching category/mode re-tints the selected bar smoothly
    // rather than snapping between palette colours.
    val barColor by animateColorAsState(
        targetValue = if (isSelected) selectedColor
                      else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(220),
        label = "barColor_${month.key}",
    )
    val labelColor = if (isSelected) selectedColor
                     else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = if (isSelected) abbreviateAmount(month.amount) else "",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            fontSize = 9.sp,
            maxLines = 1,
        )

        Spacer(Modifier.height(3.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .fillMaxHeight(animatedFraction)
                .clip(AppShapes.pill)
                .background(barColor),
        )

        Spacer(Modifier.height(5.dp))

        Text(
            text = month.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = labelColor,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

// ─── Cut-off chips ───────────────────────────────────────────────────────────

/**
 * Picks which half of the charted month — 1st–15th or 16th–end — the budget card and the
 * breakdown describe. Budgets and spending are per cut-off even though the chart shows months.
 */
@Composable
private fun CutOffChips(
    periods: List<BudgetPeriod>,
    selectedId: String?,
    onSelected: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        periods.forEach { period ->
            val selected = period.id == selectedId
            BounceSurface(
                onClick = { onSelected(period.id) },
                shape = AppShapes.pill,
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainer,
                pressedScale = 0.94f,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                modifier = Modifier.heightIn(min = 32.dp),
            ) {
                Text(
                    text = period.rangeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Budget legend ────────────────────────────────────────────────────────────

@Composable
private fun BudgetLegend(budget: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Dashed line swatch (matches the chart budget line = secondary/sage)
        val swatchColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
        Canvas(modifier = Modifier.size(width = 20.dp, height = 2.dp)) {
            drawRoundRect(
                color = swatchColor,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(50f),
            )
        }
        Text(
            text = "Budget  ${formatAmount(budget)} / mo",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Category breakdown ───────────────────────────────────────────────────────

@Composable
private fun CategoryBreakdown(
    categories: List<CategorySummary>,
    /** The selected cut-off, or null for every charted one (no budget comparison then). */
    selectedPeriodId: String?,
    categoryColors: Map<String, Color>,
) {
    fun spent(c: CategorySummary) = if (selectedPeriodId != null) c.spentIn(selectedPeriodId) else c.totalSpent
    fun budget(c: CategorySummary) = if (selectedPeriodId != null) c.budgetFor(selectedPeriodId) else 0.0

    // Most over-budget first; categories without a budget go last, biggest spend first.
    // (NEGATIVE_INFINITY, not MIN_VALUE — that's the smallest *positive* double, which floated
    // unbudgeted categories above every under-budget one.)
    val sorted = remember(categories, selectedPeriodId) {
        categories.sortedWith(
            compareByDescending<CategorySummary> { c ->
                val budget = budget(c)
                if (budget > 0.0) spent(c) - budget else Double.NEGATIVE_INFINITY
            }.thenByDescending { spent(it) }
        )
    }
    val maxAmount = sorted.maxOfOrNull { spent(it) }?.takeIf { it > 0 } ?: 1.0

    var showPills by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "By Category",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        sorted.forEachIndexed { index, summary ->
            val amount = spent(summary)
            CategoryRow(
                index = index,
                category = summary.category,
                amount = amount,
                fallbackFraction = (amount / maxAmount).toFloat(),
                budget = budget(summary),
                barColor = categoryColors[summary.category] ?: colorForIndex(index),
                showPills = showPills,
                onTap = { showPills = !showPills },
            )
        }
    }
}

@Composable
private fun CategoryRow(
    index: Int,
    category: String,
    amount: Double,
    fallbackFraction: Float,
    budget: Double,
    barColor: Color,
    showPills: Boolean,
    onTap: () -> Unit,
) {
    val hasBudget = budget > 0.0
    val isOverBudget = hasBudget && amount > budget
    val remaining = budget - amount

    val targetFraction = if (hasBudget) (amount / budget).toFloat().coerceAtMost(1f)
                         else fallbackFraction

    // Fill each progress bar from empty, staggered down the list so the breakdown reads
    // as curated rather than dumped — same intro language as the month bars above.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 35L)
        started = true
    }
    val animatedFraction by animateFloatAsState(
        targetValue = if (started) targetFraction else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "cat_$category",
    )

    val overflowColor = MaterialTheme.colorScheme.error
    val remainingColor = MaterialTheme.colorScheme.secondary

    val bounce = rememberPressBounce(pressedScale = 0.97f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(bounce.modifier)
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                onClick = onTap,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    )
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                CategoryGlyph(
                    kind = categoryGlyphKind(category),
                    color = barColor,
                    size = 22.dp,
                )
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(
                visible = showPills,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        expandHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            expandFrom = Alignment.End,
                        ),
                exit = fadeOut(tween(140)) +
                       shrinkHorizontally(tween(140), shrinkTowards = Alignment.End),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(AppShapes.pill)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (hasBudget) abbreviateAmount(budget) else "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            fontSize = 9.sp,
                        )
                    }
                    if (hasBudget) {
                        BudgetStatusPill(
                            remaining = remaining,
                            isOver = isOverBudget,
                            remainingColor = remainingColor,
                            overColor = overflowColor,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(AppShapes.pill)
                .background(
                    if (hasBudget) barColor.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(AppShapes.pill)
                    .background(
                        if (isOverBudget) overflowColor.copy(alpha = 0.85f)
                        else barColor.copy(alpha = 0.85f)
                    ),
            )
            if (isOverBudget) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(AppShapes.pill)
                        .background(overflowColor),
                )
            }
        }
    }
}

@Composable
private fun BudgetStatusPill(
    remaining: Double,
    isOver: Boolean,
    remainingColor: Color,
    overColor: Color,
) {
    val label = if (isOver) "${abbreviateAmount(-remaining)} over"
                else "${abbreviateAmount(remaining)} left"
    val color = if (isOver) overColor else remainingColor

    Box(
        modifier = Modifier
            .clip(AppShapes.pill)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            fontSize = 9.sp,
        )
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

/**
 * Title plus up to three action pills, laid out to fit the device.
 *
 * On a roomy screen they share one row. Below [StackedHeaderWidth] the pills drop to a row of
 * their own: three pills plus a serif title on one line leaves the title ~120dp on a small phone
 * (iPhone 13 mini and friends), which is narrow enough that "Spending Summary" breaks mid-word.
 * Giving the title the full width costs one row of height and keeps every action reachable —
 * better than shrinking the type or hiding actions behind an overflow menu.
 */
@Composable
private fun SummaryHeader(
    onEditBudgets: (() -> Unit)?,
    onOpenPaymentStatus: (() -> Unit)?,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stacked = maxWidth < StackedHeaderWidth

        if (stacked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryHeaderTitle()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryHeaderActions(
                        onEditBudgets = onEditBudgets,
                        onOpenPaymentStatus = onOpenPaymentStatus,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) { SummaryHeaderTitle() }
                SummaryHeaderActions(
                    onEditBudgets = onEditBudgets,
                    onOpenPaymentStatus = onOpenPaymentStatus,
                )
            }
        }
    }
}

@Composable
private fun SummaryHeaderTitle() {
    Column {
        Text(
            text = "Spending Summary",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Tap a month · pull down to refresh",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The pills themselves — no Row/Column scope needed, so both layouts can host them. */
@Composable
private fun SummaryHeaderActions(
    onEditBudgets: (() -> Unit)?,
    onOpenPaymentStatus: (() -> Unit)?,
) {
    if (onOpenPaymentStatus != null) {
        HeaderActionPill(
            label = "Payments",
            color = ExpenseTerracotta,
            onClick = onOpenPaymentStatus,
        )
    }
    if (onEditBudgets != null) {
        HeaderActionPill(
            label = "Budgets",
            color = MaterialTheme.colorScheme.primary,
            onClick = onEditBudgets,
        )
    }
}

@Composable
private fun HeaderActionPill(label: String, color: Color, onClick: () -> Unit) {
    BounceSurface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = AppShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainer,
        pressedScale = 0.92f,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}

// ─── Empty / loading / error ─────────────────────────────────────────────────

@Composable
private fun SummaryLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = "Loading summary…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Couldn't load summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            BounceSurface(
                onClick = onRetry,
                shape = AppShapes.field,
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
}

@Composable
private fun SummaryEmpty() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Nothing to summarise yet.\nLog an expense from the + tab and it shows up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
