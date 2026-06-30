package org.example.project.model

/**
 * The Summary Trend tab shows roll-up buckets (Bills, Thing, …) while the 'Data Dump'
 * tab records finer per-transaction categories. This maps each bucket to the granular
 * categories that feed it, so the per-category drill-down can gather the right rows.
 *
 * Matching is normalized (lower-cased, trimmed, collapsed whitespace). A bucket always
 * includes its own name as a member, so sheets/demo data that already use the bucket
 * name directly still resolve.
 */
object CategoryGroups {

    /** bucket (normalized) -> member categories (normalized). */
    private val groups: Map<String, Set<String>> = mapOf(
        "thing" to setOf("clothing", "shoppee", "shopee", "personal", "home"),
        "gifts" to setOf("balay kab", "benevolent fund", "benevolent"),
        "travel" to setOf("transportation", "grab", "travel"),
        "church" to setOf("church", "fenders", "tithes", "tithe"),
        "bills" to setOf("electricity", "rent", "subscription", "st peter", "investment"),
        "food" to setOf("food", "grocery", "wet market"),
    )

    private val whitespaceRegex = Regex("\\s+")

    private fun normalize(value: String): String =
        value.trim().lowercase().split(whitespaceRegex).joinToString(" ")

    /** True when [transactionCategory] belongs to the Summary [bucket]. */
    fun matches(transactionCategory: String, bucket: String): Boolean {
        val key = normalize(bucket)
        val members = groups[key].orEmpty() + key
        return normalize(transactionCategory) in members
    }
}
