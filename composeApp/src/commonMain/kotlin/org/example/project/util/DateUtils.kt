package org.example.project.util

import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn

/** Cross-platform date helpers for the app's `M/d/yyyy` form dates and ledger date cells. */
@OptIn(ExperimentalTime::class)
object DateUtils {

    /** Parses the form's `M/d/yyyy` (e.g. 3/1/2026); null when malformed or not a real date. */
    fun parseDate(dateString: String): LocalDate? {
        val parts = dateString.trim().split("/")
        if (parts.size != 3) return null
        val (month, day, year) = parts.map { it.toIntOrNull() ?: return null }
        return runCatching { LocalDate(year, month, day) }.getOrNull()
    }

    /** Today's date in the device's time zone. */
    fun today(): LocalDate = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** Today's date in the form's `M/d/yyyy` format, in the device's time zone. */
    fun getCurrentDateFormatted(): String {
        val today = today()
        return "${today.month.number}/${today.day}/${today.year}"
    }

    /** Current calendar month as a 1..12 number, in the device's time zone. */
    fun currentMonthNumber(): Int = today().month.number

    /**
     * Best-effort full date from a ledger date cell: the app's `M/d/yyyy`, ISO `yyyy-MM-dd`,
     * `M-d-yyyy`, or text with a month name ("June 15, 2026", "15 Jun 2026", "June 15" — the
     * last assumes [defaultYear]). Null when no day can be determined.
     */
    fun parseLedgerDate(raw: String?, defaultYear: Int = today().year): LocalDate? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        fun date(year: Int, month: Int, day: Int) = runCatching { LocalDate(year, month, day) }.getOrNull()

        s.split("/").mapNotNull { it.trim().toIntOrNull() }.let { p ->
            if (p.size == 3 && s.count { it == '/' } == 2) return date(p[2], p[0], p[1])
        }
        s.split("-").mapNotNull { it.trim().toIntOrNull() }.let { p ->
            if (p.size == 3 && s.count { it == '-' } == 2) {
                return if (p[0] > 31) date(p[0], p[1], p[2]) else date(p[2], p[0], p[1])
            }
        }

        // Month-name formats: pick the month by name, the 4-digit number as the year, and the
        // remaining 1–31 number as the day.
        val low = s.lowercase()
        val month = monthNames.indexOfFirst { low.contains(it.take(3)) } + 1
        if (month == 0) return null
        val numbers = Regex("[0-9]+").findAll(s).map { it.value.toInt() }.toList()
        val year = numbers.firstOrNull { it > 31 } ?: defaultYear
        val day = numbers.firstOrNull { it in 1..31 } ?: return null
        return date(year, month, day)
    }

    private val monthNames = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )

    /**
     * Maps a Summary-tab month header ("January", "Jan", "May 2026") to its 1..12 number.
     * Returns 0 when it can't be resolved.
     */
    fun monthNumberFromName(name: String): Int {
        val key = name.trim().lowercase().take(3)
        if (key.length < 3) return 0
        return monthNames.indexOfFirst { it.startsWith(key) } + 1
    }

    /** Full month name for a 1..12 number ("May"). Returns "" for out-of-range input. */
    fun monthName(number: Int): String =
        monthNames.getOrNull(number - 1)?.replaceFirstChar { it.uppercase() } ?: ""

    /**
     * Best-effort month extraction from a Data Dump date cell. Handles the app's own
     * `M/d/yyyy`, ISO `yyyy-MM-dd`, and any string containing a month name. Returns 0 if unknown.
     */
    fun monthNumberFromDate(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        val s = raw.trim()

        // App's canonical format: M/d/yyyy (also tolerates M-d-yyyy via the dash branch below)
        s.split("/").let { p ->
            if (p.size == 3) p[0].toIntOrNull()?.let { if (it in 1..12) return it }
        }

        // ISO yyyy-MM-dd vs M-d-yyyy — disambiguate by which end looks like a 4-digit year.
        s.split("-").let { p ->
            if (p.size == 3) {
                val first = p[0].toIntOrNull()
                val last = p[2].toIntOrNull()
                if (first != null && first > 31) p[1].toIntOrNull()?.let { if (it in 1..12) return it }
                if (first != null && first in 1..12 && (last ?: 0) > 31) return first
            }
        }

        // Fallback: any month name embedded in the string ("June 15, 2026", "15 Jun 2026")
        val low = s.lowercase()
        monthNames.forEachIndexed { i, m -> if (low.contains(m.take(3))) return i + 1 }
        return 0
    }
}
