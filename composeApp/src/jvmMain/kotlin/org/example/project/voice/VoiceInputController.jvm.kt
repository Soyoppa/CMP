package org.example.project.voice

// Desktop has no built-in speech engine; stays unsupported (mic hidden) unless a cloud STT is added.
actual fun provideVoiceInputController(): VoiceInputController = UnsupportedVoiceInputController()
