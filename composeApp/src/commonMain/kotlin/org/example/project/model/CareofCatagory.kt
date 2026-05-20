package org.example.project.model

/**
 * "care-of" assignments used by the tracker_2 schema.
 * Each value identifies who is responsible for paying back the charge.
 */
enum class CareofCatagory(val displayName: String) {
    GEN("Gen"),
    MAMA("Mama"),
    MIMI("Mimi"),
    REIMBURSE("Reimburse"),
    RG("R&G");

    companion object {
        /** Case-insensitive match against [displayName]; null if no match. */
        fun fromDisplayName(value: String): CareofCatagory? {
            val trimmed = value.trim()
            return entries.firstOrNull { it.displayName.equals(trimmed, ignoreCase = true) }
        }
    }
}
