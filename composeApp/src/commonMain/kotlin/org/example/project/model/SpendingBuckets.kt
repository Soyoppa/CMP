package org.example.project.model

/**
 * The Summary and Budget screens show a handful of roll-up buckets (Food, Bills, …) while the
 * ledger records finer per-transaction categories. A [SpendingBuckets] set maps each bucket to
 * the categories that feed it.
 *
 * Matching is normalized (lower-cased, trimmed, collapsed whitespace). A bucket always includes
 * its own name as a member, so data that already uses the bucket name directly still resolves.
 */
class SpendingBuckets(
    /** Display buckets, in the order the Summary + Budget screens show them. */
    val names: List<String>,
    /** bucket display name -> member category names. */
    members: Map<String, Set<String>>,
) {
    /** normalized bucket -> normalized member categories (bucket name included). */
    private val groups: Map<String, Set<String>> =
        members.entries.associate { (bucket, cats) -> normalize(bucket) to (cats.map(::normalize).toSet() + normalize(bucket)) }

    private val displayByKey: Map<String, String> = names.associateBy(::normalize)

    /** True when [transactionCategory] belongs to [bucket]. */
    fun matches(transactionCategory: String, bucket: String): Boolean {
        val key = normalize(bucket)
        return normalize(transactionCategory) in (groups[key].orEmpty() + key)
    }

    /** The display bucket [transactionCategory] rolls up into, or [OTHER] when it matches none. */
    fun bucketFor(transactionCategory: String): String {
        val normalized = normalize(transactionCategory)
        val key = groups.entries.firstOrNull { (_, cats) -> normalized in cats }?.key
        return key?.let { displayByKey[it] } ?: OTHER
    }

    companion object {
        /** Catch-all bucket for categories that map to none of the named buckets. */
        const val OTHER = "Other"

        private fun normalize(value: String): String =
            value.trim().lowercase().split(Regex("\\s+")).joinToString(" ")

        /** Buckets for the default (store) category list, see [StandardCategories]. */
        val STANDARD = SpendingBuckets(
            names = listOf("Food", "Bills", "Transport", "Shopping", "Lifestyle", "Giving"),
            members = mapOf(
                "Food" to setOf("Food & Dining", "Groceries"),
                "Bills" to setOf("Bills & Utilities", "Rent"),
                "Transport" to setOf("Transportation", "Travel"),
                "Shopping" to setOf("Shopping", "Personal Care"),
                "Lifestyle" to setOf("Health", "Entertainment", "Education"),
                "Giving" to setOf("Gifts & Donations"),
            ),
        )

        /** Buckets for the tracker_1 household sheet ('Data Dump' categories). */
        val HOUSEHOLD = SpendingBuckets(
            names = listOf("Thing", "Gifts", "Travel", "Church", "Bills", "Food"),
            members = mapOf(
                "Thing" to setOf("clothing", "shoppee", "shopee", "personal", "home"),
                "Gifts" to setOf("balay kab", "benevolent fund", "benevolent"),
                "Travel" to setOf("transportation", "grab", "travel"),
                "Church" to setOf("church", "fenders", "tithes", "tithe"),
                "Bills" to setOf("electricity", "rent", "subscription", "st peter", "investment"),
                "Food" to setOf("food", "grocery", "wet market"),
            ),
        )
    }
}
