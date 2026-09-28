package org.example.project.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.ui.effects.rememberPressBounce
import org.example.project.ui.theme.AppShapes

/**
 * The app's one "pick one of these" control: a tappable [ChoiceField] tile that opens a
 * [ChoicePickerSheet] modal.
 *
 * Shared by the Add Transaction form and the Paid & Unpaid filters so both read and behave
 * identically — a scrolling chip strip stops working the moment a list grows past a handful of
 * options, while the modal lays them all out at once.
 */

/**
 * The tile that shows the current selection and opens the picker.
 *
 * [leadingGlyph] adds a tinted category icon; [leading] overrides it with a custom slot (e.g. a
 * payment brand badge). The compact two-up tiles drop the chevron to claw back width for long
 * values.
 */
@Composable
fun ChoiceField(
    label: String,
    placeholder: String,
    selected: String,
    isExpanded: Boolean,
    isEnabled: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    leadingGlyph: CategoryGlyphKind? = null,
    leading: (@Composable () -> Unit)? = null,
    showChevron: Boolean = true,
) {
    val bounce = rememberPressBounce(pressedScale = 0.98f)
    val hasValue = selected.isNotBlank()
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "chevronRotation",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) accentColor else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "choiceBorder",
    )
    val containerAlpha by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.5f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "choiceAlpha",
    )

    Row(
        modifier = modifier
            .clip(AppShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = AppShapes.field,
            )
            .clickable(
                interactionSource = bounce.interactionSource,
                indication = null,
                enabled = isEnabled,
                onClick = onToggle,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (hasValue) "$label: $selected" else "$label: $placeholder"
                stateDescription = if (isExpanded) "expanded" else "collapsed"
            }
            .then(bounce.modifier)
            .graphicsLayer { alpha = containerAlpha }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) {
            leading()
        } else if (leadingGlyph != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(AppShapes.field)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                CategoryGlyph(
                    kind = leadingGlyph,
                    color = accentColor,
                    size = 20.dp,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isExpanded) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (hasValue) selected else placeholder,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (hasValue) FontWeight.SemiBold else FontWeight.Normal,
                color = if (hasValue) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showChevron) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(AppShapes.pill)
                    .background(
                        if (isExpanded) accentColor.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surface
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val chevronColor =
                    if (isExpanded) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                Canvas(
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                ) {
                    val stroke = 2.dp.toPx()
                    val w = size.width
                    val h = size.height
                    // Downward chevron: left -> bottom-center -> right
                    drawLine(
                        color = chevronColor,
                        start = Offset(w * 0.5f, h * 0.7f),
                        end = Offset(w * 0.08f, h * 0.32f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = chevronColor,
                        start = Offset(w * 0.5f, h * 0.7f),
                        end = Offset(w * 0.92f, h * 0.32f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/**
 * The modal itself: every option laid out as wrapping chips, so nothing hides off-screen.
 *
 * [badges] maps an option to a small trailing count (the Paid & Unpaid screen uses it for
 * outstanding rows per card). Options absent from the map, or mapped to 0, render no badge.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChoicePickerSheet(
    title: String,
    options: List<String>,
    selected: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    showGlyphs: Boolean = false,
    showPaymentBadges: Boolean = false,
    badges: Map<String, Int> = emptyMap(),
    badgeColor: Color? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AppShapes.card,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    ChoiceChip(
                        text = option,
                        isSelected = option == selected,
                        accentColor = accentColor,
                        onClick = { onSelect(option) },
                        showGlyph = showGlyphs,
                        showPaymentBadge = showPaymentBadges,
                        badgeCount = badges[option]?.takeIf { it > 0 },
                        badgeColor = badgeColor ?: accentColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(
    text: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    showGlyph: Boolean = false,
    showPaymentBadge: Boolean = false,
    badgeCount: Int? = null,
    badgeColor: Color = accentColor,
) {
    val container by animateColorAsState(
        targetValue = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipContent",
    )
    BounceSurface(
        onClick = onClick,
        shape = AppShapes.pill,
        color = container,
        pressedScale = 0.92f,
        contentPadding = PaddingValues(
            start = if (showGlyph) 12.dp else if (showPaymentBadge) 6.dp else 16.dp,
            end = 16.dp,
            top = if (showPaymentBadge) 6.dp else 10.dp,
            bottom = if (showPaymentBadge) 6.dp else 10.dp,
        ),
        modifier = Modifier.semantics {
            role = Role.Button
            contentDescription = if (badgeCount == null) text else "$text, $badgeCount unpaid"
            stateDescription = if (isSelected) "selected" else "not selected"
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showPaymentBadge) {
                // The badge keeps its own brand colour + white initials, so it stands on both
                // the resting and the accent-filled (selected) chip without extra treatment.
                PaymentBadge(name = text, size = 26.dp)
            } else if (showGlyph) {
                // Selected chips tint the glyph to the on-accent surface so it reads on the
                // filled pill; unselected chips keep the brand accent for a pop of colour.
                CategoryGlyph(
                    kind = categoryGlyphKind(text),
                    color = content,
                    size = 18.dp,
                )
            }
            Text(
                text = text,
                color = content,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (badgeCount != null) {
                Box(
                    modifier = Modifier
                        .clip(AppShapes.pill)
                        .background(
                            // On a filled (selected) chip the accent tint would vanish into the
                            // background, so the badge flips to a translucent scrim instead.
                            if (isSelected) MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)
                            else badgeColor.copy(alpha = 0.18f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = badgeCount.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) content else badgeColor,
                    )
                }
            }
        }
    }
}
