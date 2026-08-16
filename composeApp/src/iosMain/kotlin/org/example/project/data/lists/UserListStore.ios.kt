package org.example.project.data.lists

// iOS currently runs BypassAuthRepository (no Firebase uid yet), so use the in-memory
// fallback. Swap to a Firestore-backed store once Firebase Auth is wired on iOS.
actual fun createUserListStore(): UserListStore = InMemoryUserListStore()
