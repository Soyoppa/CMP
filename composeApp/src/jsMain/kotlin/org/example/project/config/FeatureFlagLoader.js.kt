package org.example.project.config

// Legacy JS target is not deployed; flags stay fully enabled.
actual fun createFeatureFlagLoader(): FeatureFlagLoader = NoopFeatureFlagLoader()
