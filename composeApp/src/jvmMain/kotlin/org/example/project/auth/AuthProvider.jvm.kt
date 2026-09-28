package org.example.project.auth

// Desktop has no Firebase SDK; use the REST API. The credential is kept in memory only, so a
// desktop session doesn't outlive the process (desktop isn't a store target).
actual fun createAuthProvider(): AuthProvider = RestAuthProvider(InMemoryCredentialStore())
