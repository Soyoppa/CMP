package org.example.project.auth

// Desktop/JVM has no Firebase Auth binding; auth is bypassed.
actual fun createAuthRepository(): AuthRepository = BypassAuthRepository()
