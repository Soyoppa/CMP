package org.example.project.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.fraunces_bold
import kotlinproject.composeapp.generated.resources.fraunces_regular
import kotlinproject.composeapp.generated.resources.fraunces_semibold
import kotlinproject.composeapp.generated.resources.inter_bold
import kotlinproject.composeapp.generated.resources.inter_medium
import kotlinproject.composeapp.generated.resources.inter_regular
import kotlinproject.composeapp.generated.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/**
 * Free lookalikes for Anthropic / Claude's commercial type:
 *   - Inter     ≈ Styrene  (sans UI + body)
 *   - Fraunces  ≈ Copernicus / Tiempos (serif headings + display)
 *
 * Font resources are loaded inside a composable, so the families are
 * exposed as @Composable helpers and consumed by [appTypography].
 */
@Composable
private fun interFamily() = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

@Composable
private fun frauncesFamily() = FontFamily(
    Font(Res.font.fraunces_regular, FontWeight.Normal),
    Font(Res.font.fraunces_semibold, FontWeight.SemiBold),
    Font(Res.font.fraunces_bold, FontWeight.Bold),
)

/**
 * Claude-style type scale: characterful serif for the big moments,
 * a clean grotesque sans for reading and UI chrome.
 */
@Composable
fun appTypography(): Typography {
    val sans = interFamily()
    val serif = frauncesFamily()

    return Typography(
        // Hero / display — serif, like Claude's marketing & empty-state headers
        displayLarge = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.SemiBold,
            fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.5).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.SemiBold,
            fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.25).sp,
        ),
        displaySmall = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp, lineHeight = 44.sp,
        ),

        // Section titles — serif
        headlineLarge = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp, lineHeight = 40.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp, lineHeight = 36.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp, lineHeight = 32.sp,
        ),

        // Card / list titles — large stays serif, smaller switch to sans for legibility
        titleLarge = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp, lineHeight = 28.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
        ),

        // Body — sans, generous line height for comfortable reading
        bodyLarge = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
        ),

        // Buttons, chips, captions — sans
        labelLarge = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Medium,
            fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
        ),
    )
}
