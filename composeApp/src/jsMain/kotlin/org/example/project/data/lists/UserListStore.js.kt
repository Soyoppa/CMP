package org.example.project.data.lists

// Legacy JS target is not deployed; no cloud binding — use the in-memory fallback.
actual fun createUserListStore(): UserListStore = InMemoryUserListStore()
