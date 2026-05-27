package org.example.project.data.ai

// Android does not yet wire the firebase-ai SDK; AI falls back to Ollama.
// Future: implement via com.google.firebase:firebase-ai (FirebaseAI.getInstance()).
internal actual fun geminiProviderOrNull(): AiProvider? = null
