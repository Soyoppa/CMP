package org.example.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.chat_bubble
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.viewmodel.AiViewModel
import org.jetbrains.compose.resources.painterResource

// Strong ease-out (cubic-bezier(0.23, 1, 0.32, 1)) — starts fast so the modal feels
// responsive the instant it's summoned, then settles. See the design-eng skill.
private val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

// Bubble lives at the bottom-end; the modal grows from — and collapses back to — that
// corner, so both transforms share this origin for visual continuity.
private val BubbleOrigin = TransformOrigin(1f, 1f)

// Drag-and-drop tuning. The bubble's *home* (undragged) position matches the pre-drag
// design so nothing shifts for a user who never touches it.
private val BubbleSize = 60.dp
private val HomeEndPadding = 20.dp
private val HomeBottomPadding = 112.dp
private val EdgeMargin = 20.dp
private val TopSafeMargin = 24.dp

// Bouncy but controlled — reserved for drag-released motion per the design-eng skill
// ("keep bounce subtle; reserve it for drag-to-dismiss and playful interactions").
private val SnapSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
)

/**
 * Messenger-style floating chat head. Tap opens [ChatModal]; drag repositions it anywhere
 * on screen, snapping back to whichever side edge it's closer to on release (spring
 * physics, vertical position preserved).
 *
 * It recedes into the bottom-end corner (scale + fade) as the modal grows out of the same
 * point, so the two read as one continuous gesture rather than two separate elements.
 */
@Composable
fun ChatBubble(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()

        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()
        val bubbleSizePx = with(density) { BubbleSize.toPx() }
        val edgeMarginPx = with(density) { EdgeMargin.toPx() }
        val topSafeMarginPx = with(density) { TopSafeMargin.toPx() }
        val homeLeft = containerWidthPx - bubbleSizePx - with(density) { HomeEndPadding.toPx() }
        val homeTop = containerHeightPx - bubbleSizePx - with(density) { HomeBottomPadding.toPx() }

        // Plain (non-suspend) state driven directly and synchronously from detectDragGestures'
        // callbacks (which are NOT suspend functions, so they can't call Animatable.snapTo) for
        // exact 1:1 finger tracking. `animate()` runs separately, from a regular coroutine, to
        // spring these values to a target once the finger lifts.
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }
        var bubbleScale by remember { mutableStateOf(1f) }
        var isDragging by remember { mutableStateOf(false) }
        var settleJob by remember { mutableStateOf<Job?>(null) }

        fun settle(targetX: Float, targetY: Float, targetScale: Float, spec: AnimationSpec<Float>) {
            settleJob?.cancel()
            settleJob = scope.launch {
                launch { animate(offsetX, targetX, animationSpec = spec) { v, _ -> offsetX = v } }
                launch { animate(offsetY, targetY, animationSpec = spec) { v, _ -> offsetY = v } }
                animate(bubbleScale, targetScale, animationSpec = spec) { v, _ -> bubbleScale = v }
            }
        }

        fun growOnDragStart() {
            isDragging = true
            settleJob?.cancel()
            settleJob = scope.launch { animate(bubbleScale, 1.08f, animationSpec = SnapSpring) { v, _ -> bubbleScale = v } }
        }

        fun settleOnRelease() {
            isDragging = false
            val currentCenterX = homeLeft + offsetX + bubbleSizePx / 2f
            val targetLeft = if (currentCenterX < containerWidthPx / 2f) {
                edgeMarginPx
            } else {
                containerWidthPx - bubbleSizePx - edgeMarginPx
            }
            val clampedTop = (homeTop + offsetY).coerceIn(
                topSafeMarginPx,
                containerHeightPx - bubbleSizePx - edgeMarginPx,
            )
            settle(targetLeft - homeLeft, clampedTop - homeTop, 1f, SnapSpring)
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = HomeEndPadding, bottom = HomeBottomPadding),
            enter = fadeIn(tween(200, easing = EaseOutStrong)) +
                scaleIn(tween(260, easing = EaseOutStrong), initialScale = 0.6f, transformOrigin = BubbleOrigin),
            exit = fadeOut(tween(140)) +
                scaleOut(tween(160), targetScale = 0.6f, transformOrigin = BubbleOrigin),
        ) {
            val bounce = rememberPressBounce(pressedScale = 0.9f)
            val shadowElevation by animateDpAsState(if (isDragging) 20.dp else 12.dp, label = "bubbleShadow")

            Box(
                modifier = Modifier
                    .then(bounce.modifier)
                    .graphicsLayer {
                        translationX = offsetX
                        translationY = offsetY
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                    }
                    .size(BubbleSize)
                    .shadow(elevation = shadowElevation, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    // Drag first (its own touch-slop decides when a press becomes a drag and
                    // consumes the gesture); a plain tap that never exceeds slop is left
                    // unconsumed and falls through to the clickable below.
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { growOnDragStart() },
                            onDragEnd = { settleOnRelease() },
                            onDragCancel = { settleOnRelease() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            },
                        )
                    }
                    .clickable(
                        interactionSource = bounce.interactionSource,
                        indication = null,
                        onClick = onClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chat_bubble),
                    contentDescription = "Open AI assistant. Drag to move.",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

/**
 * The AI chat as a floating modal. A dim scrim (tap to dismiss) sits behind a near-full-screen
 * panel that scales up from the bubble's corner. The panel hosts the regular [ChatScreen] with
 * a collapse affordance wired to [onClose].
 *
 * [viewModel] is owned by the caller so the conversation survives open/close cycles.
 */
@Composable
fun ChatModal(
    visible: Boolean,
    onClose: () -> Unit,
    viewModel: AiViewModel,
    onRequestSignUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Scrim — fades independently of the panel and dismisses on tap.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
            )
        }

        // Panel — grows from the bubble corner. A no-op clickable absorbs taps on empty
        // regions so they don't fall through the scrim and dismiss the sheet.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(240, easing = EaseOutStrong)) +
                scaleIn(tween(300, easing = EaseOutStrong), initialScale = 0.92f, transformOrigin = BubbleOrigin),
            exit = fadeOut(tween(170)) +
                scaleOut(tween(190), targetScale = 0.92f, transformOrigin = BubbleOrigin),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 44.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                ChatScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    bottomPadding = 24.dp,
                    onRequestSignUp = onRequestSignUp,
                    onClose = onClose,
                )
            }
        }
    }
}
