package org.example.project.auth

// TODO: implement with com.google.firebase:firebase-auth (FirebaseAuth.getInstance()) once
// google-services.json is restored. Bypassed for now so the Android build isn't gated on auth.
actual fun createAuthRepository(): AuthRepository = BypassAuthRepository()
