package org.example.project.model

/**
 * Represents one row from the AISummaryRecords sheet.
 *
 * Each row is a parent expense category with monthly totals for the year.
 *
 * @property category The parent category name (e.g. BILLS, FOOD, THING).
 * @property monthlyAmounts Map of month name to amount (e.g. "January" -> 19211.0).
 */
data class AiSummaryRecord(
    val category: String,
    val monthlyAmounts: Map<String, Double>
) {
    /** Total spend across all months for this category. */
    val yearTotal: Double get() = monthlyAmounts.values.sum()
}

/**
 * Maps each parent category to its sub-categories from the raw transaction data.
 * Used to give the AI context on how individual categories roll up.
 */
val CATEGORY_GROUP_MAP: Map<String, List<String>> = mapOf(
    "BILLS"  to listOf("Rent", "Electricity", "Subscription", "St Peter", "Investment"),
    "FOOD"   to listOf("Food", "Grocery", "Wet Market"),
    "THING"  to listOf("Clothing", "Shoppee", "Personal"),
    "TRAVEL" to listOf("Transportation", "Grab", "Travel"),
    "CHURCH" to listOf("Church", "Fenders", "Tithes"),
    "GIFTS"  to listOf("Home", "Balay Kab", "Benevolent Fund")
)

/** The ordered list of months as they appear in the sheet columns. */
val SHEET_MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)
