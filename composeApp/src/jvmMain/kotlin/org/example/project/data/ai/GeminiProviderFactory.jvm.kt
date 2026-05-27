package org.example.project.data.ai

// Desktop/JVM has no Firebase AI Logic bridge; AI falls back to Ollama.
internal actual fun geminiProviderOrNull(): AiProvider? = null
