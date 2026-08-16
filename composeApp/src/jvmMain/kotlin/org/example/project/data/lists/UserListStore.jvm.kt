package org.example.project.data.lists

// JVM desktop is a dev target running BypassAuthRepository — use the in-memory fallback.
actual fun createUserListStore(): UserListStore = InMemoryUserListStore()
