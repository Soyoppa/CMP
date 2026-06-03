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
    BALAY_KAB("Balay Kab"),
    CHURCH("Church"),
    CLOTHING("Clothing"),
    ELECTRICITY("Electricity"),
    FOOD("Food"),
    GRAB("Grab"),
    GROCERY("Grocery"),
    HOME("Home"),
    OTHER("Other"),
    PERSONAL("Personal"),
    RENT("Rent"),
    SALARY("Salary"),
    SAVINGS("Savings"),
    ST_PETER("St Peter"),
    SUBSCRIPTION("Subscription"),
    TRAVEL("Travel"),
    WET_MARKET("Wet Market")
}

/** Income sources for the tracker_1 schema — shown only when the Income type is selected. */
enum class IncomeCategory(val displayName: String) {
    RENZ_INCOME("Renz Income"),
    GEN_INCOME("Gen Income"),
    OTHER("Other"),
}

enum class PaymentMode(val displayName: String) {
    AMEX("Amex"),
    BPI("BPI"),
    CASH("Cash"),
    CITI_REWARDS("Citi Rewards"),
    EASTWEST_GOLD("Eastwest Gold"),
    GCASH("Gcash"),
    HEXAGON("Hexagon"),
    LANDERS("Landers"),
    MARIBANK("Maribank"),
    MAYA("Maya"),
    OTHER("Other"),
    RCBC_FLEX("Rcbc Flex"),
    SECURITY_BANK("Security Bank"),
}