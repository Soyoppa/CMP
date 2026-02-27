package org.example.project.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable

data class Transaction(
    val id: String = "",
    val date: String,
    val description: String,
    val inflow: Double = 0.0,  // Income
    val outflow: Double = 0.0, // Expenses
    val category: String,
    val modeOfPayment: String = "",
    val isPaid: Boolean = false
)

enum class TransactionCategory(val displayName: String) {
    SALARY("Salary"),
    SAVINGS("Savings"),
    RENT("Rent"),
    SUBSCRIPTION("Subscription"),
    BALAY_KAB("Balay Kab"),
    ST_PETER("St Peter"),
    CHURCH("Church"),
    HOME("Home"),
    ELECTRICITY("Electricity"),
    WET_MARKET("Wet Market"),
    FOOD("Food"),
    GROCERY("Grocery"),
    CLOTHING("Clothing"),
    TRAVEL("Travel"),
    PERSONAL("Personal"),
    OTHER("Other")
}

enum class PaymentMode(val displayName: String) {
    MAYA("Maya"),
    SECURITY_BANK("Security Bank"),
    CITI_REWARDS("Citi Rewards"),
    GCASH("Gcash"),
    CASH("Cash"),
    BPI("BPI"),
    LANDERS("Landers"),
    EASTWEST_GOLD("Eastwest Gold"),
    HEXAGON("Hexagon"),
    RCBC_FLEX("Rcbc Flex"),
    OTHER("Other")
}