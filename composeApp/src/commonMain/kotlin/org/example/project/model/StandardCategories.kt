package org.example.project.model

/**
 * Default option lists for cloud-ledger accounts (every store user). Users can edit their
 * category and payment-mode lists afterwards; these only seed the first launch.
 */
object StandardCategories {
    val expense: List<String> = listOf(
        "Food & Dining", "Groceries", "Transportation", "Bills & Utilities", "Rent", "Shopping",
        "Health", "Entertainment", "Travel", "Education", "Gifts & Donations", "Personal Care", "Other",
    )

    val income: List<String> = listOf("Salary", "Business", "Freelance", "Gifts", "Other")

    val paymentModes: List<String> = listOf(
        "Cash", "Debit Card", "Credit Card", "Bank Transfer", "GCash", "Maya", "Other",
    )
}
