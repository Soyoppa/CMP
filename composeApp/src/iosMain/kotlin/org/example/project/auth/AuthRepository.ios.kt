package org.example.project.auth

// iOS has no Firebase Auth binding wired; auth is bypassed.
actual fun createAuthRepository(): AuthRepository = BypassAuthRepository()
