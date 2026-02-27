package org.example.project.util

import kotlin.math.round

/**
 * Cross-platform utility functions for formatting
 */
object FormatUtils {
    
    /**
     * Format a double to 2 decimal places
     * Works across all Kotlin Multiplatform targets
     */
    fun formatCurrency(amount: Double): String {
        val rounded = round(amount * 100) / 100
        val integerPart = rounded.toInt()
        val decimalPart = ((rounded - integerPart) * 100).toInt()
        
        return if (decimalPart == 0) {
            "$integerPart.00"
        } else if (decimalPart < 10) {
            "$integerPart.0$decimalPart"
        } else {
            "$integerPart.$decimalPart"
        }
    }
    
    /**
     * Format amount with peso symbol
     */
    fun formatPeso(amount: Double): String {
        return "${formatCurrency(amount)}"
    }
}