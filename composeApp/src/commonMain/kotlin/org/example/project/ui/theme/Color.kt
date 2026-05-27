package org.example.project.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// Brand palette
//   Sage green   #6D9773  → secondary / accents
//   Dark forest  #0C3B2E  → background anchor + on-primary text
//   Amber brown  #B46617  → tertiary / warm accent
//   Golden yellow #FFBA00 → primary / CTA / highlights
// ─────────────────────────────────────────────────────────────
val GoldenYellow = Color(0xFFFFBA00)
val SageGreen = Color(0xFF6D9773)
val AmberBrown = Color(0xFFB46617)
val DarkForest = Color(0xFF0C3B2E)

// Semantic transaction accents (not part of the M3 scheme): forest-leaning green for
// inflow, warm terracotta for outflow — tuned to sit on the earthy brand palette.
val IncomeGreen = Color(0xFF2E7D52)
val ExpenseTerracotta = Color(0xFFC0492B)

// Tonal variants (hand-tuned for theme contrast)
val GoldenLight = Color(0xFFFFE08A)   // light golden container
val GoldenDeep = Color(0xFF4A3600)    // deep golden for on-container text
val SageLight = Color(0xFFDCE7DD)     // sage tint container (light theme)
val SageBright = Color(0xFF9DC2A2)    // lighter sage for dark-theme accents
val SageDeepContainer = Color(0xFF2A4030) // sage container on dark bg
val AmberLight = Color(0xFFFFDCC2)    // amber tint container
val AmberBright = Color(0xFFE0883C)   // lighter amber for dark-theme accents
val AmberDeep = Color(0xFF3A1E00)     // deep amber for on-container text
val AmberContainerDark = Color(0xFF5A3200)

// Forest neutrals
val ForestSurfaceDark = Color(0xFF0F4536)     // raised surface in dark theme
val ForestSurfaceVariantDark = Color(0xFF1A4F40)
val ForestOnDark = Color(0xFFECF3ED)          // primary text on dark forest
val ForestOnDarkVariant = Color(0xFFB8CCBF)   // muted text on dark forest

// Light theme neutrals (warm cream, not stark white — pairs with the earthy palette)
val CreamBackground = Color(0xFFFCFAF6)
val CreamSurface = Color(0xFFFFFFFF)
val CreamSurfaceVariant = Color(0xFFEEEAE1)
val OnCream = DarkForest                       // dark forest reads as near-black text
val OnCreamVariant = Color(0xFF45504A)