package org.example.project.data.ai

/**
 * Platform factory for the Firebase AI Logic (Gemini) provider.
 *
 * Only the **wasmJs** (web) target ships a real implementation — it bridges to the
 * `firebase/ai` JS SDK loaded in [index.html]. All other targets return `null`, so the
 * orchestrator transparently uses the Ollama fallback there.
 *
 * Returning `null` also covers the "not configured" case: callers should still gate on
 * [org.example.project.config.ConfigManager.ApiConfiguration.isFirebaseAiConfigured].
 */
internal expect fun geminiProviderOrNull(): AiProvider?
