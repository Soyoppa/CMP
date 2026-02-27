package org.example.project.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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
        return "${today.monthNumber}/${today.day}/${today.year}"
    }
}
