package org.example.project.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.ui.theme.AppShapes

/**
 * Picks the calendar year on screen. The app reads one year at a time, so this is how the user
 * reaches earlier data; [canGoBack] is false once the ledger has nothing older, and forward stops
 * at the current year since later years can't have entries yet.
 */
@Composable
fun YearStepper(
    year: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onYearChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StepArrow(glyph = "‹", description = "Previous year", enabled = canGoBack) {
            onYearChange(year - 1)
        }
        AnimatedContent(
            targetState = year,
            transitionSpec = {
                val forward = targetState > initialState
                val offset = { full: Int -> if (forward) full else -full }
                (slideInHorizontally(initialOffsetX = offset) + fadeIn()) togetherWith
                    (slideOutHorizontally(targetOffsetX = { -offset(it) }) + fadeOut()) using
                    SizeTransform(clip = false)
            },
            label = "year",
        ) { value ->
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 52.dp),
            )
        }
        StepArrow(glyph = "›", description = "Next year", enabled = canGoForward) {
            onYearChange(year + 1)
        }
    }
}

@Composable
private fun StepArrow(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    BounceSurface(
        onClick = onClick,
        enabled = enabled,
        shape = AppShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainer,
        pressedScale = 0.9f,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = description },
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            // Disabled ends of the range stay visible but clearly inert.
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
    }
}
