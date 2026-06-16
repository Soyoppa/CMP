package org.example.project.voice

/**
 * Result of turning a spoken sentence into form fields.
 *
 * [category] is null when the spoken words don't obviously match one of the available options —
 * the ViewModel then asks the AI to pick one. [isIncome] is null when no income/expense cue was
 * heard, so the caller keeps the current type.
 */
data class ParsedVoiceTransaction(
    val amount: String?,
    val description: String,
    val category: String?,
    val isIncome: Boolean?,
)

/**
 * Pure, dependency-free parser: spoken sentence → [ParsedVoiceTransaction].
 *
 * Deliberately conservative — it extracts what it's confident about (the amount, a cleaned
 * description, an obvious category keyword) and leaves the rest null for the AI fallback. Pure so
 * it's trivially testable and shared across every platform.
 *
 * Handles inputs like:
 *  - "spent two hundred fifty on groceries"  (digits only; words are left to AI/description)
 *  - "250 pesos for groceries"
 *  - "I paid 1,200 for the electricity bill"
 *  - "received 5000 from salary"  → isIncome = true
 */
object VoiceTransactionParser {

    // Words that signal money but add no description value.
    private val currencyNoise = setOf(
        "php", "peso", "pesos", "piso", "₱", "dollars", "dollar", "bucks",
    )

    // Leading verbs/fillers that shouldn't survive into the description.
    private val leadingFillers = setOf(
        "i", "spent", "paid", "pay", "bought", "buy", "for", "on", "the", "a", "an",
        "of", "to", "got", "add", "added", "log", "record", "my", "some", "this",
    )

    private val incomeCues = setOf(
        "received", "receive", "earned", "earn", "income", "salary", "sweldo",
        "deposit", "deposited", "refund", "refunded", "got paid", "allowance",
    )

    private val expenseCues = setOf(
        "spent", "paid", "bought", "buy", "expense", "bill", "cost",
    )

    // First standalone number, optional thousands separators + up to 2 decimals.
    private val amountRegex = Regex("""(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d{1,2}))?""")

    // Matches any numeric token (with optional thousands separators and decimals) used to scrub
    // the spoken amount from the raw transcript when building the description. Hoisted so the
    // Regex is compiled once for the lifetime of the object rather than on every parse call.
    private val numericScrubRegex = Regex("""\d[\d,]*(\.\d{1,2})?""")

    /**
     * @param transcript raw recognized speech.
     * @param categoryOptions the active schema's category labels, used for a best-effort keyword match.
     * @param incomeKeywordDetection when false (schema has no income concept) [isIncome] stays null.
     */
    fun parse(
        transcript: String,
        categoryOptions: List<String>,
        incomeKeywordDetection: Boolean = true,
    ): ParsedVoiceTransaction {
        // Cap at 500 chars before any heap-allocating operations. The description reducer
        // will enforce its own 200-char limit downstream; this stops a pathologically long
        // speech-engine result from causing unbounded allocations during regex/split work.
        val normalized = transcript.take(500).trim()
        val lower = normalized.lowercase()

        val amount = extractAmount(lower)
        val isIncome = if (incomeKeywordDetection) detectIncome(lower) else null
        val category = matchCategory(lower, categoryOptions)
        val description = buildDescription(normalized, amount, category)

        return ParsedVoiceTransaction(
            amount = amount,
            description = description,
            category = category,
            isIncome = isIncome,
        )
    }

    private fun extractAmount(lower: String): String? {
        val match = amountRegex.find(lower) ?: return null
        val whole = match.groupValues[1].replace(",", "")
        val decimals = match.groupValues[2]
        return if (decimals.isNotEmpty()) "$whole.$decimals" else whole
    }

    private fun detectIncome(lower: String): Boolean? {
        val tokens = lower.split(' ')
        if (incomeCues.any { cue -> cue in lower || cue in tokens }) return true
        if (expenseCues.any { cue -> cue in lower || cue in tokens }) return false
        return null
    }

    /** Best-effort: the spoken text contains a category name (or a category name contains a spoken word). */
    private fun matchCategory(lower: String, options: List<String>): String? {
        // Exact phrase hit first ("groceries" → "Groceries").
        options.firstOrNull { it.lowercase() in lower }?.let { return it }
        // Token overlap for multi-word categories ("eating out" ↔ "ate out").
        val tokens = lower.split(' ', ',', '.').filter { it.length > 2 }.toSet()
        return options.firstOrNull { option ->
            option.lowercase().split(' ', '/', '&').any { it.length > 2 && it in tokens }
        }
    }

    /** Strip the recognized amount, currency noise and leading fillers; keep the human-meaningful remainder. */
    private fun buildDescription(original: String, amount: String?, category: String?): String {
        var working = original
        if (amount != null) {
            // Remove the spoken amount in either comma or plain form. Plain literal substring
            // replace — no need to build/compile a Regex (via Regex.escape) just to match a
            // fixed string on every parse call.
            working = working.replace(amount, " ")
            working = working.replace(numericScrubRegex, " ")
        }

        val words = working.split(' ', ',', '.')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.lowercase() in currencyNoise }

        // Drop leading fillers only (a mid-sentence "for" inside "for the kids" is fine to keep
        // once we hit the first content word).
        val cleaned = words.dropWhile { it.lowercase() in leadingFillers }

        val result = cleaned.joinToString(" ").trim()
        return result.replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }
    }
}
