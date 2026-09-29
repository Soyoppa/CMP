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
import org.example.project.viewmodel.BudgetEvent
import org.example.project.viewmodel.BudgetViewModel
import org.example.project.viewmodel.createBudgetViewModel

private val BucketColors = listOf(
    GoldenYellow, SageGreen, AmberBrown, IncomeGreen, ExpenseTerracotta, SageBright,
)

private fun colorForIndex(index: Int): Color = BucketColors[index % BucketColors.size]

private fun formatPhp(amount: Double): String {
    val whole = amount.toLong()
    return "PHP " + buildString {
        whole.toString().reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) append(',')
            append(c)
        }
    }.reversed()
}

/**
 * Full-screen budget editor: an overall monthly budget (what "Remaining" is measured against) and
 * optional per-category budgets. Persists via [BudgetViewModel]; shown only to real (non-guest) users.
 */
@Composable
fun BudgetScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    viewModel: BudgetViewModel = createBudgetViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Top bar ─────────────────────────────────────────────
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
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
                    text = "Budgets",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Your monthly spending limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(32.dp),
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TotalBudgetCard(
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

            uiState.buckets.forEachIndexed { index, bucket ->
                BudgetRow(
                    bucket = bucket,
                    value = uiState.amounts[bucket].orEmpty(),
                    accent = colorForIndex(index),
                    onValueChange = { viewModel.onEvent(BudgetEvent.AmountChanged(bucket, it)) },
                )
            }

            Spacer(Modifier.height(4.dp))

            SaveButton(
                isSaving = uiState.isSaving,
                onSave = { viewModel.onEvent(BudgetEvent.SaveClicked) },
            )

            AnimatedVisibility(
                visible = uiState.saved || uiState.error != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = uiState.error ?: "Budgets saved",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (uiState.error != null) MaterialTheme.colorScheme.error
                            else IncomeGreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(120.dp)) // clears the floating nav pill
        }
    }
}

/**
 * The overall monthly budget — the number "Remaining" on the Summary and Add screens counts down
 * from. Left blank, the per-category budgets below add up to it instead.
 */
@Composable
private fun TotalBudgetCard(
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
            text = "Total monthly budget",
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
                    text = if (categoryTotal > 0.0) formatPhp(categoryTotal).removePrefix("PHP ") else "0",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            },
            prefix = {
                Text(
                    text = "₱ ",
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
                    "Your category budgets add up to ${formatPhp(categoryTotal)} — more than this total."
                value.isBlank() && categoryTotal > 0.0 ->
                    "Leave blank to use your category budgets: ${formatPhp(categoryTotal)} / month."
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
                    text = "₱ ",
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
private fun SaveButton(isSaving: Boolean, onSave: () -> Unit) {
    BounceSurface(
        onClick = onSave,
        enabled = !isSaving,
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
                text = "Save budgets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
