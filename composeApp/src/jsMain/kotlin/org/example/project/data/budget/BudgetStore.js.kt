package org.example.project.data.budget

// Legacy JS target is not deployed; no cloud binding — use the in-memory fallback.
actual fun createBudgetStore(): BudgetStore = InMemoryBudgetStore()
