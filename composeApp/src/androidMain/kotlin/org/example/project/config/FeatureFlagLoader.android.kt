package org.example.project.config

// TODO: implement with com.google.firebase:firebase-config once google-services.json is restored.
// Bypassed for now so the Android build isn't gated on Remote Config; flags stay fully enabled.
actual fun createFeatureFlagLoader(): FeatureFlagLoader = NoopFeatureFlagLoader()
