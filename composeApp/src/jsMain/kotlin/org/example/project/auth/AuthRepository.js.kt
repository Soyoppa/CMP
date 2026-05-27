package org.example.project.auth

// Legacy JS target is not deployed; auth is bypassed.
actual fun createAuthRepository(): AuthRepository = BypassAuthRepository()
