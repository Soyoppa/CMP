package org.example.project.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Golden = CTA/primary, Sage = secondary, Amber = tertiary/warm accent,
// Dark forest = neutral anchor (backgrounds in dark, text/on-primary in light).
private val LightColorScheme = lightColorScheme(
    primary = GoldenYellow,
    onPrimary = DarkForest,                    // dark-on-gold ≈ 7.3:1
    primaryContainer = GoldenLight,
    onPrimaryContainer = GoldenDeep,

    secondary = SageGreen,
    onSecondary = DarkForest,
    secondaryContainer = SageLight,
    onSecondaryContainer = DarkForest,

    tertiary = AmberBrown,
    onTertiary = Color.White,
    tertiaryContainer = AmberLight,
    onTertiaryContainer = AmberDeep,

    background = CreamBackground,
    onBackground = OnCream,

    surface = CreamSurface,
    onSurface = OnCream,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = OnCreamVariant,

    outline = Color(0xFF7A857E),
    outlineVariant = Color(0xFFC7CFC8),

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = GoldenYellow,
    onPrimary = DarkForest,
    primaryContainer = GoldenDeep,
    onPrimaryContainer = GoldenLight,

    secondary = SageBright,                    // lifted for contrast on forest bg
    onSecondary = DarkForest,
    secondaryContainer = SageDeepContainer,
    onSecondaryContainer = SageLight,

    tertiary = AmberBright,                     // lifted amber for dark bg
    onTertiary = DarkForest,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = AmberLight,

    background = DarkForest,
    onBackground = ForestOnDark,

    surface = ForestSurfaceDark,
    onSurface = ForestOnDark,
    surfaceVariant = ForestSurfaceVariantDark,
    onSurfaceVariant = ForestOnDarkVariant,

    outline = Color(0xFF7E9587),
    outlineVariant = Color(0xFF35463D),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun FinanceTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography(),
        content = content
    )
}
