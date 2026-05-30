package org.example.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.model.CategorySummary
import org.example.project.ui.components.BounceSurface
import org.example.project.ui.theme.AmberBrown
import org.example.project.ui.theme.AppShapes
import org.example.project.ui.theme.ExpenseTerracotta
import org.example.project.ui.theme.GoldenYellow
import org.example.project.ui.theme.IncomeGreen
import org.example.project.ui.theme.SageBright
import org.example.project.ui.theme.SageGreen
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

private fun colorForIndex(index: Int): Color = CategoryBarColors[index % CategoryBarColors.size]

private fun formatAmount(amount: Double): String {
    if (amount == 0.0) return "—"
    val whole = amount.toLong()
    return "PHP " + buildString {
        whole.toString().reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) append(',')
            append(c)
        }
    }.reversed()
}

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

// ─── Sheet entry ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummarySheet(
    onDismiss: () -> Unit,
    viewModel: SummaryViewModel = createSummaryViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
            )
        },
    ) {
        // Absorbs any remaining downward drag after child scrollables are exhausted,
        // so it never reaches the sheet's dismiss gesture handler.
        val blockDismiss = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset = if (available.y > 0f) available else Offset.Zero
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Dismiss zone: drag handle (above) + header ───────────────────
            // Sheet drag-to-dismiss works normally here.
            SummaryHeader(
                hasData = uiState.categories.isNotEmpty(),
                onRetry = viewModel::load,
            )

            // ── Protected zone: chart + categories ───────────────────────────
            // nestedScroll blocker sits between this content and the sheet,
            // eating leftover downward scroll so the sheet never dismisses.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(blockDismiss)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    uiState.isLoading -> SummaryLoading()
                    uiState.error != null -> SummaryError(
                        message = uiState.error!!,
                        onRetry = viewModel::load,
                    )
                    uiState.categories.isEmpty() -> SummaryEmpty()
                    else -> SummaryContent(
                        categories = uiState.categories,
                        months = uiState.months,
                        selectedMonth = uiState.selectedMonth,
                        totalMonthlyBudget = uiState.totalMonthlyBudget,
                        budgetByCategory = uiState.budgetByCategory,
                        onMonthSelected = viewModel::selectMonth,
                    )
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

// ─── Content ─────────────────────────────────────────────────────────────────

@Composable
private fun SummaryContent(
    categories: List<CategorySummary>,
    months: List<String>,
    selectedMonth: String?,
    totalMonthlyBudget: Double,
    budgetByCategory: Map<String, Double>,
    onMonthSelected: (String) -> Unit,
) {
    val monthlyTotals = remember(categories, months) {
        months.associateWith { month -> categories.sumOf { it.spentIn(month) } }
    }
    val selectedTotal = selectedMonth?.let { monthlyTotals[it] }
        ?: monthlyTotals.values.sum()
    val isOverBudget = totalMonthlyBudget > 0 && selectedMonth != null && selectedTotal > totalMonthlyBudget

    TotalExpenseCard(
        label = if (selectedMonth != null) "$selectedMonth Total" else "All Months Total",
        total = selectedTotal,
        budget = if (selectedMonth != null) totalMonthlyBudget else 0.0,
        isOverBudget = isOverBudget,
    )

    MonthBarChart(
        months = months,
        totals = monthlyTotals,
        selectedMonth = selectedMonth,
        totalMonthlyBudget = totalMonthlyBudget,
        onMonthSelected = onMonthSelected,
    )

    if (totalMonthlyBudget > 0) {
        BudgetLegend(totalMonthlyBudget = totalMonthlyBudget)
    }

    Spacer(Modifier.height(20.dp))

    CategoryBreakdown(
        categories = categories,
        selectedMonth = selectedMonth,
        budgetByCategory = budgetByCategory,
    )

    Spacer(Modifier.height(8.dp))
}

// ─── Total expense card ───────────────────────────────────────────────────────

@Composable
private fun TotalExpenseCard(
    label: String,
    total: Double,
    budget: Double,
    isOverBudget: Boolean,
) {
    val amountColor = if (isOverBudget) MaterialTheme.colorScheme.error
                      else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Total Expense",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = formatAmount(total),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = amountColor,
            )
            if (budget > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "of ${formatAmount(budget)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isOverBudget) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "Over budget",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
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

@Composable
private fun MonthBarChart(
    months: List<String>,
    totals: Map<String, Double>,
    selectedMonth: String?,
    totalMonthlyBudget: Double,
    onMonthSelected: (String) -> Unit,
) {
    val maxActual = totals.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val maxY = maxOf(maxActual, totalMonthlyBudget).takeIf { it > 0 } ?: 1.0
    val budgetFraction = if (totalMonthlyBudget > 0) (totalMonthlyBudget / maxY).toFloat() else -1f

    val budgetLineColor = MaterialTheme.colorScheme.secondary
    val budgetLineDash = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .padding(horizontal = 16.dp)
            .pointerInput(months) {
                awaitEachGesture {
                    // Respond to both tap (down) and drag without any minimum distance
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val idx = (down.position.x / size.width * months.size)
                        .toInt().coerceIn(0, months.lastIndex)
                    onMonthSelected(months[idx])

                    do {
                        val event = awaitPointerEvent()
                        val pos = event.changes.firstOrNull()?.position ?: break
                        val dragIdx = (pos.x / size.width * months.size)
                            .toInt().coerceIn(0, months.lastIndex)
                        onMonthSelected(months[dragIdx])
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
                    pathEffect = budgetLineDash,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            months.forEach { month ->
                val total = totals[month] ?: 0.0
                BarColumn(
                    month = month,
                    amount = total,
                    fraction = (total / maxY).toFloat(),
                    isSelected = month == selectedMonth,
                )
            }
        }
    }
}

@Composable
private fun RowScope.BarColumn(
    month: String,
    amount: Double,
    fraction: Float,
    isSelected: Boolean,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction * BarAreaFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "bar_$month",
    )

    val barColor = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.surfaceContainerHighest
    val labelColor = if (isSelected) MaterialTheme.colorScheme.primary
                     else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = if (isSelected) abbreviateAmount(amount) else "",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 9.sp,
            maxLines = 1,
        )

        Spacer(Modifier.height(3.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .fillMaxHeight(animatedFraction)
                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                .background(barColor),
        )

        Spacer(Modifier.height(5.dp))

        Text(
            text = month.take(3),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = labelColor,
            fontSize = 10.sp,
        )
    }
}

// ─── Budget legend ────────────────────────────────────────────────────────────

@Composable
private fun BudgetLegend(totalMonthlyBudget: Double) {
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
            text = "Budget  ${formatAmount(totalMonthlyBudget)} / mo",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Category breakdown ───────────────────────────────────────────────────────

@Composable
private fun CategoryBreakdown(
    categories: List<CategorySummary>,
    selectedMonth: String?,
    budgetByCategory: Map<String, Double>,
) {
    val sorted = remember(categories, selectedMonth, budgetByCategory) {
        categories.sortedByDescending { c ->
            val amount = if (selectedMonth != null) c.spentIn(selectedMonth) else c.totalSpent
            val budget = budgetByCategory[c.category.lowercase()] ?: 0.0
            if (budget > 0.0) amount - budget else Double.MIN_VALUE
        }
    }
    val maxAmount = sorted.firstOrNull()?.let { c ->
        if (selectedMonth != null) c.spentIn(selectedMonth) else c.totalSpent
    }?.takeIf { it > 0 } ?: 1.0

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
            val amount = if (selectedMonth != null) summary.spentIn(selectedMonth) else summary.totalSpent
            val budget = budgetByCategory[summary.category.lowercase()] ?: 0.0
            val fallbackFraction = (amount / maxAmount).toFloat()
            CategoryRow(
                category = summary.category,
                amount = amount,
                fallbackFraction = fallbackFraction,
                monthlyBudget = budget,
                barColor = colorForIndex(index),
                showPills = showPills,
                onTap = { showPills = !showPills },
            )
        }
    }
}

@Composable
private fun CategoryRow(
    category: String,
    amount: Double,
    fallbackFraction: Float,
    monthlyBudget: Double,
    barColor: Color,
    showPills: Boolean,
    onTap: () -> Unit,
) {
    val hasBudget = monthlyBudget > 0.0
    val isOverBudget = hasBudget && amount > monthlyBudget
    val remaining = monthlyBudget - amount

    val targetFraction = if (hasBudget) (amount / monthlyBudget).toFloat().coerceAtMost(1f)
                         else fallbackFraction

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "cat_$category",
    )

    val overflowColor = MaterialTheme.colorScheme.error
    val remainingColor = MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .clickable(onClick = onTap)
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
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(barColor),
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
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (hasBudget) abbreviateAmount(monthlyBudget) else "—",
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
                .clip(RoundedCornerShape(50))
                .background(
                    if (hasBudget) barColor.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(RoundedCornerShape(50))
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
                        .clip(RoundedCornerShape(topEnd = 50.dp, bottomEnd = 50.dp))
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
            .clip(RoundedCornerShape(50))
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

@Composable
private fun SummaryHeader(hasData: Boolean, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Spending Summary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Tap a bar to filter by month",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hasData) {
            BounceSurface(
                onClick = onRetry,
                shape = AppShapes.pill,
                color = MaterialTheme.colorScheme.surfaceContainer,
                pressedScale = 0.92f,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
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
            text = "No summary data found.\nMake sure the 'Summary' tab exists in your sheet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
