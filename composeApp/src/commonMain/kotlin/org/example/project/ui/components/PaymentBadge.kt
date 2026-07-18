package org.example.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.ui.theme.AppShapes

/**
 * A brand-coloured monogram tile for a payment method (BPI, GCash, Maya …).
 *
 * Deliberately *not* the banks' real logos: those are trademarked, come in inconsistent
 * full-colour artwork that can't be tinted, and would clash with the app's monochrome glyph
 * language. A coloured initial badge reads "that's my BPI card" just as fast, stays consistent
 * with the rest of the form, and carries no bundled-logo IP. Colours are brand-suggestive
 * approximations, not official swatches.
 *
 * Cash falls back to the wallet glyph and anything unrecognised to the neutral tag glyph, so the
 * badge is safe to call for every option in the list.
 */
@Composable
fun PaymentBadge(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val brand = payBrand(name)
    if (brand == null) {
        val n = name.lowercase()
        val kind = if ("cash" in n) CategoryGlyphKind.WALLET else CategoryGlyphKind.TAG
        Box(
            modifier = modifier
                .size(size)
                .clip(AppShapes.field)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            CategoryGlyph(
                kind = kind,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                size = size * 0.56f,
            )
        }
        return
    }

    // Initials scale down a touch when there are three letters so "BPI"/"RCB" still fit.
    val fontScale = if (brand.initials.length >= 3) 0.28f else 0.38f
    Box(
        modifier = modifier
            .size(size)
            .clip(AppShapes.field)
            .background(brand.color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = brand.initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * fontScale).sp,
            maxLines = 1,
        )
    }
}

private data class PayBrand(val initials: String, val color: Color)

/** Maps a payment-method display name to a brand-suggestive badge; null = use a glyph fallback. */
private fun payBrand(name: String): PayBrand? {
    val n = name.lowercase()
    return when {
        // BPI Gold (credit card) — gold tile to set it apart from the red BPI debit badge.
        // Checked before the generic "bpi" case so it isn't swallowed by it.
        "bpi gold" in n -> PayBrand("BPI", Color(0xFFC79100))
        "bpi" in n -> PayBrand("BPI", Color(0xFFB11116))
        "gcash" in n -> PayBrand("GC", Color(0xFF0072CE))
        "maya" in n -> PayBrand("My", Color(0xFF1FBF73))
        "security" in n -> PayBrand("SB", Color(0xFF0E7C3A))
        "rcbc" in n -> PayBrand("R", Color(0xFF003DA5))
        "citi" in n -> PayBrand("C", Color(0xFF0A4C8B))
        "eastwest" in n -> PayBrand("EW", Color(0xFFC8102E))
        "maribank" in n -> PayBrand("MB", Color(0xFFEC6A1E))
        "landers" in n -> PayBrand("L", Color(0xFF0E9A92))
        "hexagon" in n -> PayBrand("H", Color(0xFF5B3FA0))
        else -> null
    }
}
