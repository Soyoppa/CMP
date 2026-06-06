package org.example.project.voice

// Stub until SFSpeechRecognizer is wired. A real impl will need the NSSpeechRecognitionUsageDescription
// Info.plist key plus speech + microphone permission prompts.
actual fun provideVoiceInputController(): VoiceInputController = UnsupportedVoiceInputController()
