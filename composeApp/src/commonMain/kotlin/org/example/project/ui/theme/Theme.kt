package org.example.project.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = LightOnPrimary,
    primaryContainer = PrimaryGreen.copy(alpha = 0.2f),
    onPrimaryContainer = DarkBlue,
    
    secondary = DarkBlue,
    onSecondary = LightOnSecondary,
    secondaryContainer = DarkBlue.copy(alpha = 0.2f),
    onSecondaryContainer = DarkBlue,
    
    background = LightBackground,
    onBackground = LightOnBackground,
    
    surface = LightSurface,
    onSurface = LightOnSurface,
    
    outline = DarkBlue,
    outlineVariant = DarkBlue.copy(alpha = 0.5f),
    
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = DarkOnPrimary,
    primaryContainer = PrimaryGreen.copy(alpha = 0.3f),
    onPrimaryContainer = PrimaryGreen,
    
    secondary = PrimaryGreen,
    onSecondary = DarkOnSecondary,
    secondaryContainer = PrimaryGreen.copy(alpha = 0.2f),
    onSecondaryContainer = PrimaryGreen,
    
    background = DarkBackground,
    onBackground = DarkOnBackground,
    
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    
    outline = PrimaryGreen,
    outlineVariant = PrimaryGreen.copy(alpha = 0.5f),
    
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
        content = content
    )
}