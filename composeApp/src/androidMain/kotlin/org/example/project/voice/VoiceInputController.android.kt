package org.example.project.voice

// Stub until Android SpeechRecognizer is wired. A real impl will need an Application Context
// (inject via the @Composable ViewModelProvider actual) plus RECORD_AUDIO permission handling.
actual fun provideVoiceInputController(): VoiceInputController = UnsupportedVoiceInputController()
