package org.example.project.config

// Desktop/JVM has no Remote Config binding; flags stay fully enabled.
actual fun createFeatureFlagLoader(): FeatureFlagLoader = NoopFeatureFlagLoader()
