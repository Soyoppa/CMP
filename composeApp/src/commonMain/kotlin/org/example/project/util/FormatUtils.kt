package org.example.project.util

import kotlin.math.abs
import kotlin.math.roundToLong

/** Cross-platform number formatting (`String.format` isn't available in commonMain). */
object FormatUtils {

    /**
     * [amount] with two decimals and thousands separators, e.g. 1234.5 -> "1,234.50",
     * -0.29 -> "-0.29". Works in whole cents, so values like 1.29 don't drift to "1.28".
     */
    fun formatPeso(amount: Double): String {
        val cents = (abs(amount) * 100).roundToLong()
        val whole = (cents / 100).toString()
        val fraction = (cents % 100).toString().padStart(2, '0')
        val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
        val sign = if (amount < 0 && cents != 0L) "-" else ""
        return "$sign$grouped.$fraction"
    }

    /**
     * An amount as shown to the user: "P12,346", or "P1,234.50" with [cents]. The sign is dropped —
     * callers say "over by" / "+" / "−" themselves. Uses a plain "P" because the peso glyph is
     * missing from the web font.
     */
    fun money(amount: Double, cents: Boolean = false): String =
        if (cents) CURRENCY + formatPeso(abs(amount))
        else CURRENCY + abs(amount).roundToLong().toString().reversed().chunked(3).joinToString(",").reversed()

    /** Currency marker used across the UI. */
    const val CURRENCY = "P"
}
