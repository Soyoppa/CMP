package org.example.project.data.budget

// Android currently runs BypassAuthRepository (no Firebase uid yet), so use the in-memory
// fallback. Swap to a Firestore-backed store once Firebase Auth is wired on Android.
actual fun createBudgetStore(): BudgetStore = InMemoryBudgetStore()
