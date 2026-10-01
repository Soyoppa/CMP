package org.example.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.ui.components.AppSheet
import org.example.project.ui.components.BounceSurface
import org.example.project.ui.components.CategoryGlyph
import org.example.project.ui.components.categoryGlyphKind
import org.example.project.ui.theme.AmberBrown
import org.example.project.ui.theme.AppShapes
import org.example.project.ui.theme.ExpenseTerracotta
import org.example.project.ui.theme.GoldenYellow
import org.example.project.ui.theme.IncomeGreen
import org.example.project.ui.theme.SageBright
import org.example.project.ui.theme.SageGreen
import org.example.project.util.FormatUtils
import org.example.project.viewmodel.BudgetEvent
import org.example.project.viewmodel.BudgetViewModel
import org.example.project.viewmodel.createBudgetViewModel

private val BucketColors = listOf(
    GoldenYellow, SageGreen, AmberBrown, IncomeGreen, ExpenseTerracotta, SageBright,
)

private fun colorForIndex(index: Int): Color = BucketColors[index % BucketColors.size]

/**
 * Budget editor sheet for the current cut-off (1st–15th or 16th–end of month): an overall budget
 * (what "Remaining" is measured against) and optional per-category budgets. The Save bar stays
 * pinned at the bottom however long the list is.
 */
@Composable
fun BudgetScreen(
    onClose: () -> Unit,
    /** Opens the category editor (from the "no categories yet" hint). */
    onManageCategories: () -> Unit = {},
    viewModel: BudgetViewModel = createBudgetViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val period = uiState.period
    AppSheet(
        title = "Budget · ${period.label}",
        subtitle = when {
            uiState.isUpcoming -> "Upcoming cut-off · set it once your salary is in"
            uiState.daysLeft == 0 -> "This cut-off ends today"
            uiState.daysLeft == 1 -> "1 day left in this cut-off"
            else -> "${uiState.daysLeft} days left in this cut-off"
        },
        onClose = onClose,
        footer = if (uiState.isLoading) null else ({
            SaveBar(
                label = "Save budget for ${period.label}",
                enabled = uiState.canSave,
                isSaving = uiState.isSaving,
                saved = uiState.saved,
                error = uiState.error,
                onSave = { viewModel.onEvent(BudgetEvent.SaveClicked) },
            )
        }),
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(32.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Just before a cut-off starts, both it and the one ending can be budgeted.
                if (uiState.selectablePeriods.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.selectablePeriods.forEach { option ->
                            PeriodPill(
                                label = option.label,
                                selected = option == period,
                                onClick = { viewModel.onEvent(BudgetEvent.PeriodSelected(option)) },
                            )
                        }
                    }
                }

                if (uiState.isSuggestion) NewCutOffNotice(periodLabel = period.label)

                TotalBudgetCard(
                    periodLabel = period.label,
                    value = uiState.totalInput,
                    categoryTotal = uiState.categoryTotal,
                    categoriesExceedTotal = uiState.categoriesExceedTotal,
                    onValueChange = { viewModel.onEvent(BudgetEvent.TotalChanged(it)) },
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "BY CATEGORY · OPTIONAL",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )

                if (uiState.buckets.isEmpty()) {
                    NoCategoriesHint(onManageCategories = onManageCategories)
                }

                uiState.buckets.forEachIndexed { index, bucket ->
                    BudgetRow(
                        bucket = bucket,
                        value = uiState.amounts[bucket].orEmpty(),
                        accent = colorForIndex(index),
                        onValueChange = { viewModel.onEvent(BudgetEvent.AmountChanged(bucket, it)) },
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** Category budgets need categories; the user creates their own, so point the way. */
@Composable
private fun NoCategoriesHint(onManageCategories: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Add categories to budget each one separately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        BounceSurface(
            onClick = onManageCategories,
            shape = AppShapes.pill,
            color = MaterialTheme.colorScheme.surface,
            pressedScale = 0.94f,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Text(
                text = "Add categories",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PeriodPill(label: String, selected: Boolean, onClick: () -> Unit) {
    BounceSurface(
        onClick = onClick,
        shape = AppShapes.pill,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        pressedScale = 0.94f,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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

/** Shown when the cut-off has no budget yet and the form was pre-filled from the last one. */
@Composable
private fun NewCutOffNotice(periodLabel: String) {
    Text(
        text = "New cut-off — set your budget for $periodLabel. " +
            "The amounts below are copied from your last budget; adjust them and save.",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Pinned footer: the save result (if any) above the Save button. */
@Composable
private fun SaveBar(
    label: String,
    enabled: Boolean,
    isSaving: Boolean,
    saved: Boolean,
    error: String?,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(visible = saved || error != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = error ?: "Budget saved",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (error != null) MaterialTheme.colorScheme.error else IncomeGreen,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SaveButton(label = label, enabled = enabled, isSaving = isSaving, onSave = onSave)
    }
}

/**
 * The overall budget for the cut-off — the number "Remaining" on the Summary and Add screens
 * counts down from. Left blank, the per-category budgets below add up to it instead.
 */
@Composable
private fun TotalBudgetCard(
    periodLabel: String,
    value: String,
    categoryTotal: Double,
    categoriesExceedTotal: Boolean,
    onValueChange: (String) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Total budget for $periodLabel",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            placeholder = {
                Text(
                    text = if (categoryTotal > 0.0) FormatUtils.money(categoryTotal).removePrefix(FormatUtils.CURRENCY) else "0",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            },
            prefix = {
                Text(
                    text = "${FormatUtils.CURRENCY} ",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = AppShapes.field,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                cursorColor = accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = when {
                categoriesExceedTotal ->
                    "Your category budgets add up to ${FormatUtils.money(categoryTotal)} — more than this total."
                value.isBlank() && categoryTotal > 0.0 ->
                    "Leave blank to use your category budgets: ${FormatUtils.money(categoryTotal)} for this cut-off."
                else -> "What's left of this is shown on the Summary and Add screens."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (categoriesExceedTotal) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BudgetRow(
    bucket: String,
    value: String,
    accent: Color,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryGlyph(kind = categoryGlyphKind(bucket), color = accent, size = 22.dp)
        Text(
            text = bucket,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            },
            prefix = {
                Text(
                    text = "${FormatUtils.CURRENCY} ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = AppShapes.field,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                cursorColor = accent,
            ),
            modifier = Modifier.width(148.dp),
        )
    }
}

@Composable
private fun SaveButton(label: String, enabled: Boolean, isSaving: Boolean, onSave: () -> Unit) {
    BounceSurface(
        onClick = onSave,
        enabled = enabled,
        shape = AppShapes.pill,
        color = MaterialTheme.colorScheme.primary,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
