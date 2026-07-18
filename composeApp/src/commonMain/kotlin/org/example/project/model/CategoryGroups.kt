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

    /** Catch-all bucket for Data-Dump categories that map to none of the known buckets. */
    const val OTHER = "Other"

    /**
     * The fixed display buckets, in the order the Summary + Budget screens show them.
     * These are the canonical spending categories; [OTHER] is appended only when there is
     * unmapped spend (or a budget) so it never clutters the view unnecessarily.
     */
    val buckets: List<String> = listOf("Thing", "Gifts", "Travel", "Church", "Bills", "Food")

    /** bucket (normalized) -> member categories (normalized). */
    private val groups: Map<String, Set<String>> = mapOf(
        "thing" to setOf("clothing", "shoppee", "shopee", "personal", "home"),
        "gifts" to setOf("balay kab", "benevolent fund", "benevolent"),
        "travel" to setOf("transportation", "grab", "travel"),
        "church" to setOf("church", "fenders", "tithes", "tithe"),
        "bills" to setOf("electricity", "rent", "subscription", "st peter", "investment"),
        "food" to setOf("food", "grocery", "wet market"),
    )

    /** Display name keyed by normalized bucket id, so [bucketFor] can return "Thing" not "thing". */
    private val displayByKey: Map<String, String> = buckets.associateBy { normalize(it) }

    private fun normalize(value: String): String =
        value.trim().lowercase().split(Regex("\\s+")).joinToString(" ")

    /** True when [transactionCategory] belongs to the Summary [bucket]. */
    fun matches(transactionCategory: String, bucket: String): Boolean {
        val key = normalize(bucket)
        val members = groups[key].orEmpty() + key
        return normalize(transactionCategory) in members
    }

    /**
     * The display bucket a Data-Dump [transactionCategory] rolls up into, or [OTHER] when it
     * matches none. Reverse of [matches] — used to aggregate the raw ledger into the Summary.
     */
    fun bucketFor(transactionCategory: String): String {
        val normalized = normalize(transactionCategory)
        val key = groups.entries.firstOrNull { (bucket, members) ->
            normalized == bucket || normalized in members
        }?.key
        return key?.let { displayByKey[it] } ?: OTHER
    }
}
