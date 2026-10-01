package org.example.project.model

/**
 * The Summary and Budget screens show spending per bucket. For everyone that's simply each of their
 * own categories ([of]); the household sheet rolls its finer categories up into a few buckets
 * ([HOUSEHOLD]). A [SpendingBuckets] set maps each bucket to the categories that feed it.
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
        (names + members.keys).distinct().associate { bucket ->
            normalize(bucket) to (members[bucket].orEmpty().map(::normalize).toSet() + normalize(bucket))
        }

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

        /** One bucket per category: the user budgets exactly the categories they created. */
        fun of(categories: List<String>): SpendingBuckets =
            SpendingBuckets(names = categories, members = emptyMap())

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
