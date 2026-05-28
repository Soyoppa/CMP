package org.example.project.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.ui.theme.AppShapes

/**
 * A clickable, rounded, padded surface with the app's standard spring press-bounce.
 * Consolidates the `clip → background → clickable(bounce) → padding` block that was repeated
 * across the login, chat, and transaction screens.
 *
 * Layout is intentionally a centered [Box]: callers that need weighted rows or a [toggleable]
 * (rather than [clickable]) semantic keep their bespoke containers — this covers the common
 * button/pill/chip case without growing flags.
 */
@Composable
fun BounceSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = AppShapes.pill,
    color: Color = MaterialTheme.colorScheme.primary,
    border: BorderStroke? = null,
    pressedScale: Float = 0.97f,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val bounce = rememberPressBounce(pressedScale)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .then(bounce.modifier)
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content,
    )
}
