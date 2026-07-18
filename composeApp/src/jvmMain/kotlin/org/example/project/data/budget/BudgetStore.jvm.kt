package org.example.project.data.budget

// JVM desktop is a dev target running BypassAuthRepository — use the in-memory fallback.
actual fun createBudgetStore(): BudgetStore = InMemoryBudgetStore()
