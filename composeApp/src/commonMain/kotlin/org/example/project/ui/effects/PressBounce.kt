package org.example.project.ui.effects

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun rememberPressBounce(pressedScale: Float = 0.94f): PressBounce {
    val source = remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressBounce",
    )
    val modifier = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    return PressBounce(source, modifier)
}

class PressBounce(
    val interactionSource: MutableInteractionSource,
    val modifier: Modifier,
)
