package org.example.project.voice

// Kotlin/JS legacy target keeps the stub; the real Web Speech bridge lives in the wasmJs build,
// which is the primary web target. Swap this for a real impl if JS ships to users.
actual fun provideVoiceInputController(): VoiceInputController = UnsupportedVoiceInputController()
