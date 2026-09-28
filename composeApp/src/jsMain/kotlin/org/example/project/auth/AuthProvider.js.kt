package org.example.project.auth

// The deployed web app is the wasmJs target; this JS fallback uses the REST API with an
// in-memory credential (the session ends when the tab closes).
actual fun createAuthProvider(): AuthProvider = RestAuthProvider(InMemoryCredentialStore())
