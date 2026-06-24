package org.example.project.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight, outline category icons drawn with [Canvas] — no resource vectors, so they're
 * compile-checked and render identically on every target (same approach as the form's MicGlyph).
 *
 * Categories arrive as free-form strings (the summary sheet reads them from Google Sheets) as well
 * as the typed enums on the add form, so resolution is keyword-based via [categoryGlyphKind] with a
 * neutral [CategoryGlyphKind.TAG] fallback. All glyphs are authored on a 24-unit grid and scaled to
 * the requested size; stroke colour is the caller's (usually the category's accent), so the icon
 * carries the same colour coding as the legacy dot it replaces.
 */
enum class CategoryGlyphKind {
    HOME, FOOD, BOLT, CAR, PLANE, WALLET, COINS, GIFT, RECEIPT, CHURCH, SHIRT, USER, BOX, TAG
}

/** Resolves a (possibly free-form) category/source name to the closest glyph. Case-insensitive. */
fun categoryGlyphKind(name: String): CategoryGlyphKind {
    val n = name.lowercase()
    fun has(vararg keys: String) = keys.any { it in n }
    return when {
        has("food", "grocer", "market", "dining", "restaurant", "meal", "eat") -> CategoryGlyphKind.FOOD
        has("church", "peter", "tithe", "worship", "offering") -> CategoryGlyphKind.CHURCH
        has("cloth", "personal", "apparel", "shirt") -> CategoryGlyphKind.SHIRT
        has("electric", "utilit", "power", "water", "internet", "wifi") -> CategoryGlyphKind.BOLT
        has("grab", "transport", "fuel", "gas", "car", "taxi", "ride", "commute") -> CategoryGlyphKind.CAR
        has("travel", "flight", "trip", "vacation", "plane", "tour") -> CategoryGlyphKind.PLANE
        has("home", "rent", "balay", "house", "mortgage") -> CategoryGlyphKind.HOME
        has("salary", "income", "renz", "wage", "cash", "pay") -> CategoryGlyphKind.WALLET
        has("saving", "invest", "fund") -> CategoryGlyphKind.COINS
        has("gift", "present", "donation", "charity", "benevolent") -> CategoryGlyphKind.GIFT
        has("bill", "reimburse", "receipt", "tax", "subscription", "subs") -> CategoryGlyphKind.RECEIPT
        has("mama", "mimi", "mom", "dad", "care", "r&g", "family", "gen") -> CategoryGlyphKind.USER
        has("thing", "misc", "stuff", "item") -> CategoryGlyphKind.BOX
        else -> CategoryGlyphKind.TAG
    }
}

@Composable
fun CategoryGlyph(
    kind: CategoryGlyphKind,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.minDimension / 24f
        // Stroke is authored in grid units; scale() multiplies it up to device pixels.
        val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(scaleX = u, scaleY = u, pivot = Offset.Zero) {
            when (kind) {
                CategoryGlyphKind.HOME -> drawHome(color, stroke)
                CategoryGlyphKind.FOOD -> drawCart(color, stroke)
                CategoryGlyphKind.BOLT -> drawBolt(color, stroke)
                CategoryGlyphKind.CAR -> drawCar(color, stroke)
                CategoryGlyphKind.PLANE -> drawPlane(color, stroke)
                CategoryGlyphKind.WALLET -> drawWallet(color, stroke)
                CategoryGlyphKind.COINS -> drawCoins(color, stroke)
                CategoryGlyphKind.GIFT -> drawGift(color, stroke)
                CategoryGlyphKind.RECEIPT -> drawReceipt(color, stroke)
                CategoryGlyphKind.CHURCH -> drawChurch(color, stroke)
                CategoryGlyphKind.SHIRT -> drawShirt(color, stroke)
                CategoryGlyphKind.USER -> drawUser(color, stroke)
                CategoryGlyphKind.BOX -> drawBox(color, stroke)
                CategoryGlyphKind.TAG -> drawTag(color, stroke)
            }
        }
    }
}

// All paths below live on a 24×24 grid.

private fun DrawScope.path(stroke: Stroke, color: Color, build: Path.() -> Unit) =
    drawPath(Path().apply(build), color, style = stroke)

private fun DrawScope.drawHome(c: Color, s: Stroke) = path(s, c) {
    moveTo(4f, 11f); lineTo(12f, 4f); lineTo(20f, 11f)
    moveTo(6.5f, 9.5f); lineTo(6.5f, 20f); lineTo(17.5f, 20f); lineTo(17.5f, 9.5f)
}

private fun DrawScope.drawCart(c: Color, s: Stroke) {
    path(s, c) {
        moveTo(4f, 5f); lineTo(6f, 5f); lineTo(8.5f, 15f)
        lineTo(18f, 15f); lineTo(20f, 7.5f); lineTo(7f, 7.5f)
    }
    drawCircle(c, radius = 1.4f, center = Offset(9.5f, 18.5f), style = s)
    drawCircle(c, radius = 1.4f, center = Offset(16.5f, 18.5f), style = s)
}

private fun DrawScope.drawBolt(c: Color, s: Stroke) = path(s, c) {
    moveTo(13f, 3f); lineTo(5f, 13f); lineTo(11f, 13f)
    lineTo(9f, 21f); lineTo(19f, 10f); lineTo(13f, 10f); close()
}

private fun DrawScope.drawCar(c: Color, s: Stroke) {
    path(s, c) {
        moveTo(4f, 15f); lineTo(5.5f, 10f); lineTo(8f, 7.5f); lineTo(16f, 7.5f)
        lineTo(18.5f, 10f); lineTo(20f, 15f); lineTo(20f, 17f); lineTo(4f, 17f); close()
        moveTo(7f, 10.5f); lineTo(17f, 10.5f)
    }
    drawCircle(c, radius = 1.5f, center = Offset(8f, 17f), style = s)
    drawCircle(c, radius = 1.5f, center = Offset(16f, 17f), style = s)
}

private fun DrawScope.drawPlane(c: Color, s: Stroke) = path(s, c) {
    moveTo(21f, 4f); lineTo(3f, 11.5f); lineTo(10f, 13.5f); lineTo(12.5f, 20.5f); close()
    moveTo(21f, 4f); lineTo(10f, 13.5f)
}

private fun DrawScope.drawWallet(c: Color, s: Stroke) {
    path(s, c) {
        moveTo(5f, 7f); lineTo(20f, 7f); lineTo(20f, 18f); lineTo(5f, 18f); close()
    }
    drawCircle(c, radius = 1.4f, center = Offset(16.5f, 12.5f), style = s)
}

private fun DrawScope.drawCoins(c: Color, s: Stroke) {
    drawCircle(c, radius = 4f, center = Offset(9.5f, 10f), style = s)
    drawCircle(c, radius = 4f, center = Offset(14.5f, 14f), style = s)
}

private fun DrawScope.drawGift(c: Color, s: Stroke) = path(s, c) {
    moveTo(4f, 9f); lineTo(20f, 9f); lineTo(20f, 12f); lineTo(4f, 12f); close()
    moveTo(5.5f, 12f); lineTo(5.5f, 20f); lineTo(18.5f, 20f); lineTo(18.5f, 12f)
    moveTo(12f, 9f); lineTo(12f, 20f)
    moveTo(9f, 6f); lineTo(12f, 9f); lineTo(15f, 6f)
}

private fun DrawScope.drawReceipt(c: Color, s: Stroke) = path(s, c) {
    moveTo(6f, 3.5f); lineTo(18f, 3.5f); lineTo(18f, 20.5f); lineTo(15.5f, 18.8f)
    lineTo(13f, 20.5f); lineTo(10.5f, 18.8f); lineTo(8f, 20.5f); lineTo(6f, 18.8f); close()
    moveTo(9f, 8f); lineTo(15f, 8f)
    moveTo(9f, 12f); lineTo(15f, 12f)
}

private fun DrawScope.drawChurch(c: Color, s: Stroke) = path(s, c) {
    moveTo(12f, 2.5f); lineTo(12f, 6.5f)
    moveTo(10f, 4f); lineTo(14f, 4f)
    moveTo(6f, 20.5f); lineTo(6f, 11f); lineTo(12f, 7f); lineTo(18f, 11f); lineTo(18f, 20.5f); close()
    moveTo(10f, 20.5f); lineTo(10f, 15.5f); lineTo(14f, 15.5f); lineTo(14f, 20.5f)
}

private fun DrawScope.drawShirt(c: Color, s: Stroke) = path(s, c) {
    moveTo(8.5f, 4f); lineTo(5f, 7f); lineTo(7f, 9.5f); lineTo(7f, 20f); lineTo(17f, 20f)
    lineTo(17f, 9.5f); lineTo(19f, 7f); lineTo(15.5f, 4f); lineTo(13.5f, 4f)
    lineTo(12f, 6f); lineTo(10.5f, 4f); close()
}

private fun DrawScope.drawUser(c: Color, s: Stroke) {
    drawCircle(c, radius = 3.2f, center = Offset(12f, 8.5f), style = s)
    path(s, c) {
        moveTo(5.5f, 20f)
        cubicTo(6f, 15f, 18f, 15f, 18.5f, 20f)
    }
}

private fun DrawScope.drawBox(c: Color, s: Stroke) = path(s, c) {
    moveTo(12f, 3.5f); lineTo(20f, 7.5f); lineTo(20f, 16.5f); lineTo(12f, 20.5f)
    lineTo(4f, 16.5f); lineTo(4f, 7.5f); close()
    moveTo(4f, 7.5f); lineTo(12f, 11.5f); lineTo(20f, 7.5f)
    moveTo(12f, 11.5f); lineTo(12f, 20.5f)
}

private fun DrawScope.drawTag(c: Color, s: Stroke) {
    path(s, c) {
        moveTo(13f, 4f); lineTo(20f, 4f); lineTo(20f, 11f)
        lineTo(11.5f, 19.5f); lineTo(4.5f, 12.5f); close()
    }
    drawCircle(c, radius = 1.3f, center = Offset(16.5f, 7.5f), style = s)
}
