package org.example.project.config

// iOS has no Remote Config binding wired; flags stay fully enabled.
actual fun createFeatureFlagLoader(): FeatureFlagLoader = NoopFeatureFlagLoader()
