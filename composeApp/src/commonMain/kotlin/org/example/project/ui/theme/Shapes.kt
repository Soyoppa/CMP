package org.example.project.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shared corner-radius tokens. These replace the per-file `FieldCorner` / `PillCorner` /
 * `CardCorner` constants that were duplicated across screens, so the rounding stays consistent
 * and lives in one place. One-off shapes (e.g. the asymmetric chat bubble) stay local to their file.
 */
object AppShapes {
    /** Inputs, choice rows, chips, list rows. */
    val field = RoundedCornerShape(14.dp)

    /** Surface cards (e.g. the debug screen panels). */
    val card = RoundedCornerShape(20.dp)

    /** Fully-rounded buttons, pills, and circular badges. */
    val pill = RoundedCornerShape(50.dp)
}
