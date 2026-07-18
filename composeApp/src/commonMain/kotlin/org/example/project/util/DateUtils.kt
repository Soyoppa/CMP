package org.example.project.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlin.time.ExperimentalTime

/**
 * Cross-platform date formatting utilities
 */
@OptIn(ExperimentalTime::class)
object DateUtils {

    /**
     * Format LocalDate to M/d/yyyy format (e.g., 3/1/2026)
     */
//    fun formatDate(date: LocalDate): String {
//        return "${date.month.value}/${date.dayOfMonth}/${date.year}"
//    }

    /**
     * Parse date from M/d/yyyy format to LocalDate
     */
    fun parseDate(dateString: String): LocalDate? {
        return try {
            val parts = dateString.split("/")
            if (parts.size == 3) {
                val month = parts[0].toInt()
                val day = parts[1].toInt()
                val year = parts[2].toInt()
                LocalDate(year, month, day)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }


    /**
     * Get current date formatted as M/d/yyyy
     * Returns today's date in M/d/yyyy format
     */
    fun getCurrentDateFormatted(): String {
        val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
        return "${today.month.number}/${today.day}/${today.year}"
    }

    /** Current calendar month as a 1..12 number, in the device's time zone. */
    fun currentMonthNumber(): Int =
        kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()).month.number

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
