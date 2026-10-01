package org.example.project.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.model.BudgetStatus
import org.example.project.ui.theme.AmberBright
import org.example.project.ui.theme.AmberBrown
import org.example.project.ui.theme.AppShapes
import org.example.project.ui.theme.IncomeGreen
import org.example.project.ui.theme.SageBright
import org.example.project.util.FormatUtils

/**
 * One-shot cue for [RemainingBudgetBanner]'s life bar: an expense "hit" or an income "heal".
 * [id] is bumped on every add (even repeats of the same kind) so the animation always replays.
 */
data class BudgetPulse(val id: Long, val isIncome: Boolean)

/**
 * The headline budget card: **what's left** first, what's been spent right beside it, and a bar
 * showing how much of the budget is used.
 *
 * - With a budget: "Remaining P12,300" (or "Over by P2,100" in the error colour) + "Spent".
 * - Without one: the spend alone, plus a "Set a budget" pill when [onSetBudget] is provided.
 * - [showRemaining] false (an "all periods" view, where one period's budget doesn't apply):
 *   spend only, with a hint to pick a period.
 *
 * [selector] sits between the title and the figures — the cut-off chips live inside the card, so
 * it's obvious that they pick which period every number below belongs to.
 */
@Composable
fun BudgetOverviewCard(
    title: String,
    status: BudgetStatus,
    modifier: Modifier = Modifier,
    showRemaining: Boolean = true,
    /** Names the period in the no-budget hint: "cut-off" or "month". */
    periodNoun: String = "period",
    onSetBudget: (() -> Unit)? = null,
    selector: @Composable (() -> Unit)? = null,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (selector != null) selector()
        }

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
                text = "Pick a $periodNoun to see what's left of its budget.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Compact one-line version for the Add screen: "P12,300 left · Sep 16–30" with a thin bar,
 * tappable to open the summary. With no budget it's a quiet "No budget set" row — the prominent
 * ask is [BudgetPromptBanner], shown only while the period's budgeting window is open.
 */
@Composable
fun RemainingBudgetBanner(
    status: BudgetStatus,
    /** The period the status covers, e.g. "Sep 16–30" or "Sep". */
    periodLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Bumped by the caller right after a save — plays a damage hit (expense) or heal (income). */
    pulse: BudgetPulse? = null,
) {
    val color = if (status.isOverBudget) MaterialTheme.colorScheme.error else onTrackColor()
    val label = when {
        !status.hasBudget -> "No budget set for $periodLabel"
        status.isOverBudget -> "Over budget by ${FormatUtils.money(status.remaining)} · $periodLabel"
        else -> "${FormatUtils.money(status.remaining)} left · $periodLabel"
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
            if (status.hasBudget) LifeBudgetBar(status = status, color = color, pulse = pulse, height = 4)
        }
    }
}

/**
 * The prominent "time to budget" ask, shown on the Add screen while a period's budgeting window
 * is open (around payday) and it has no budget yet. Tapping opens the budget sheet.
 */
@Composable
fun BudgetPromptBanner(
    /** The period to budget, e.g. "Sep 16–30" or "Sep". */
    periodLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = "Salary in? Set your budget for $periodLabel"
    BounceSurface(
        onClick = onClick,
        shape = AppShapes.field,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        pressedScale = 0.98f,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/** "Within budget" green: brand IncomeGreen, lifted to SageBright on dark surfaces for contrast. */
@Composable
private fun onTrackColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) SageBright else IncomeGreen

/** "Running low" amber: brand AmberBrown, lifted to AmberBright on dark surfaces for contrast. */
@Composable
private fun warningColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) AmberBright else AmberBrown

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

/**
 * The Add screen's budget bar, restyled as a game life bar: it starts full and depletes toward
 * zero as the period's budget is spent (the inverse of [BudgetBar]'s fill-as-you-spend meter).
 *
 * Always carries a soft diagonal shimmer for polish. [pulse] layers a one-shot reaction on top of
 * the steady drain/refill motion: a screen-shake + red flash for an expense ("hit"), or a green
 * glow sweep for an income ("heal"). Once spending passes the budget the bar simply holds at
 * zero — going negative wouldn't mean anything more depleted — but a slow red pulse around its
 * edge keeps "you're over" visible instead of looking like a dead, static bar.
 */
@Composable
private fun LifeBudgetBar(status: BudgetStatus, color: Color, pulse: BudgetPulse?, height: Int = 6) {
    val remainingFraction by animateFloatAsState(
        targetValue = (1f - status.usedFraction).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "lifeRemaining",
    )
    val criticalColor = MaterialTheme.colorScheme.error
    val warning = warningColor()
    val barColor by animateColorAsState(
        targetValue = when {
            status.isOverBudget || remainingFraction <= 0.15f -> criticalColor
            remainingFraction <= 0.4f -> warning
            else -> color
        },
        label = "lifeColor",
    )

    // Always-on gloss sweep — purely decorative, loops forever regardless of state.
    val shimmer = rememberInfiniteTransition(label = "lifeShimmer")
    val shimmerFraction by shimmer.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "shimmerFraction",
    )

    // One-shot combat feedback on top of the steady drain/refill.
    val shakeX = remember { Animatable(0f) }
    val hitFlash = remember { Animatable(0f) }
    val healGlow = remember { Animatable(0f) }
    LaunchedEffect(pulse?.id) {
        val event = pulse ?: return@LaunchedEffect
        if (event.isIncome) {
            healGlow.snapTo(0f)
            healGlow.animateTo(1f, tween(200, easing = EaseOutQuart))
            healGlow.animateTo(0f, tween(650, easing = EaseOutQuart))
        } else {
            hitFlash.snapTo(1f)
            launch { hitFlash.animateTo(0f, tween(500, easing = EaseOutQuart)) }
            shakeX.snapTo(0f)
            shakeX.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 380
                    0f at 0
                    -7f at 40
                    6f at 90
                    -4f at 150
                    3f at 220
                    0f at 380
                },
            )
        }
    }

    // Slow danger pulse around the edge once overspent — the fill itself stays pinned at zero.
    val dangerPulse = rememberInfiniteTransition(label = "lifeDanger")
    val dangerAlpha by dangerPulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(650, easing = EaseOutQuart), repeatMode = RepeatMode.Reverse),
        label = "dangerAlpha",
    )

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .graphicsLayer { translationX = shakeX.value },
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat()

        // Track — the "missing health" backdrop, full width.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(AppShapes.pill)
                .background(color.copy(alpha = 0.16f)),
        )

        if (status.isOverBudget) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(AppShapes.pill)
                    .border(1.dp, criticalColor.copy(alpha = dangerAlpha), AppShapes.pill),
            )
        }

        // Fill — remaining "health", shrinking toward zero as the budget is spent.
        Box(
            modifier = Modifier
                .fillMaxWidth(remainingFraction)
                .fillMaxHeight()
                .clip(AppShapes.pill)
                .background(barColor),
        ) {
            if (trackWidthPx > 0f) {
                val bandWidth = with(density) { (trackWidthPx * 0.3f).toDp() }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(bandWidth)
                        .offset { IntOffset((trackWidthPx * shimmerFraction).toInt(), 0) }
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                            ),
                        ),
                )
            }
        }

        // Heal glow spans the whole track (not just the current fill) so it still reads when
        // the bar is near empty — exactly when a regen flash matters most.
        if (healGlow.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(AppShapes.pill)
                    .background(SageBright.copy(alpha = healGlow.value * 0.55f)),
            )
        }

        if (hitFlash.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(AppShapes.pill)
                    .background(criticalColor.copy(alpha = hitFlash.value * 0.5f)),
            )
        }
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
