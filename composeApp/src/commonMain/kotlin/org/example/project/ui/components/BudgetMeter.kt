package org.example.project.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.example.project.model.BudgetStatus
import org.example.project.ui.theme.AppShapes
import org.example.project.ui.theme.IncomeGreen
import org.example.project.ui.theme.SageBright
import org.example.project.util.FormatUtils

/**
 * The headline budget card: **what's left** first, what's been spent right beside it, and a bar
 * showing how much of the budget is used.
 *
 * - With a budget: "Remaining P12,300" (or "Over by P2,100" in the error colour) + "Spent".
 * - Without one: the spend alone, plus a "Set a budget" pill when [onSetBudget] is provided.
 * - [showRemaining] false (e.g. an "all months" view, where a monthly budget doesn't apply):
 *   spend only, with a hint to pick a month.
 */
@Composable
fun BudgetOverviewCard(
    title: String,
    status: BudgetStatus,
    modifier: Modifier = Modifier,
    showRemaining: Boolean = true,
    onSetBudget: (() -> Unit)? = null,
) {
    val budgetApplies = showRemaining && status.hasBudget
    val remainingColor by animateColorAsState(
        targetValue = if (status.isOverBudget) MaterialTheme.colorScheme.error else onTrackColor(),
        label = "remainingColor",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (budgetApplies) {
                // Priority figure: what's left to spend this period.
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (status.isOverBudget) "Over budget by" else "Remaining",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = remainingColor,
                    )
                    RollingAmount(
                        amount = status.remaining,
                        color = remainingColor,
                        large = true,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Spent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RollingAmount(amount = status.spent, color = MaterialTheme.colorScheme.onSurface, large = false)
                }
            } else {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Spent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RollingAmount(amount = status.spent, color = MaterialTheme.colorScheme.onSurface, large = true)
                }
                if (showRemaining && onSetBudget != null) {
                    BounceSurface(
                        onClick = onSetBudget,
                        shape = AppShapes.pill,
                        color = MaterialTheme.colorScheme.primary,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Text(
                            text = "Set a budget",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }

        if (budgetApplies) {
            BudgetBar(status = status, color = remainingColor)
            Text(
                text = "${(status.usedFraction * 100).toInt()}% of ${FormatUtils.money(status.budget)} used",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (!showRemaining) {
            Text(
                text = "Pick a month to see what's left of your budget.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Compact one-line version for the Add screen: "P12,300 left this month" with a thin bar,
 * tappable to open the budget/summary. Shows a set-budget prompt when no budget exists.
 */
@Composable
fun RemainingBudgetBanner(
    status: BudgetStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (status.isOverBudget) MaterialTheme.colorScheme.error else onTrackColor()
    val label = when {
        !status.hasBudget -> "Set a monthly budget to see what's left"
        status.isOverBudget -> "Over budget by ${FormatUtils.money(status.remaining)} this month"
        else -> "${FormatUtils.money(status.remaining)} left this month"
    }
    BounceSurface(
        onClick = onClick,
        shape = AppShapes.field,
        color = MaterialTheme.colorScheme.surfaceContainer,
        pressedScale = 0.98f,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (status.hasBudget) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (status.hasBudget) {
                    Text(
                        text = "of ${FormatUtils.money(status.budget)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (status.hasBudget) BudgetBar(status = status, color = color, height = 4)
        }
    }
}

/** "Within budget" green: brand IncomeGreen, lifted to SageBright on dark surfaces for contrast. */
@Composable
private fun onTrackColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) SageBright else IncomeGreen

@Composable
private fun BudgetBar(status: BudgetStatus, color: Color, height: Int = 6) {
    val fraction by animateFloatAsState(
        targetValue = status.usedFraction,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "budgetUsed",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(AppShapes.pill)
            .background(color.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(AppShapes.pill)
                .background(color),
        )
    }
}

/** Amount that slides up/down to its new value instead of snapping (sign dropped). */
@Composable
private fun RollingAmount(amount: Double, color: Color, large: Boolean) {
    AnimatedContent(
        targetState = amount,
        transitionSpec = {
            val slide = tween<IntOffset>(260, easing = EaseOutQuart)
            val fade = tween<Float>(260, easing = EaseOutQuart)
            if (targetState >= initialState) {
                (slideInVertically(slide) { it } + fadeIn(fade)) togetherWith
                    (slideOutVertically(slide) { -it } + fadeOut(fade))
            } else {
                (slideInVertically(slide) { -it } + fadeIn(fade)) togetherWith
                    (slideOutVertically(slide) { it } + fadeOut(fade))
            }.using(SizeTransform(clip = false))
        },
        label = "rollingAmount",
    ) { value ->
        Text(
            text = FormatUtils.money(value),
            style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
    }
}
