@file:OptIn(ExperimentalWasmJsInterop::class)

package org.example.project.voice

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// --- Bridge to the Web Speech API (window.__voiceInput is defined in index.html) ---

private fun voiceSupported(): Boolean =
    js("(!!(window.__voiceInput && window.__voiceInput.supported()))")

/** Starts one recognition session; resolves to JSON { transcript, error } when it ends. */
private fun voiceListen(lang: String): Promise<JsString> =
    js("window.__voiceInput.listen(lang)")

private fun voiceStop(): Unit =
    js("window.__voiceInput.stop()")

@Serializable
private data class VoiceBridgeResult(val transcript: String = "", val error: String = "")

private val voiceJson = Json { ignoreUnknownKeys = true }

/**
 * Web speech recognition is only present on Chromium/Safari builds — gate the real controller on
 * the bridge's own capability check so unsupported browsers fall back to the hidden mic.
 */
actual fun provideVoiceInputController(): VoiceInputController =
    if (voiceSupported()) WebSpeechVoiceInputController() else UnsupportedVoiceInputController()

/**
 * Real speech-to-text on web via the browser SpeechRecognition API. One session per [start];
 * [stop] ends it early but still surfaces whatever was captured. Errors are mapped to
 * human-readable, retry-aware [VoiceState.Error]s.
 */
internal class WebSpeechVoiceInputController : VoiceInputController {

    override val isSupported: Boolean = true

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val state = _state.asStateFlow()

    private val scope = MainScope()
    private var listening = false

    override fun start() {
        if (listening) return
        listening = true
        _state.value = VoiceState.Listening()
        scope.launch {
            try {
                val raw: JsString = voiceListen("en-US").await()
                val res = voiceJson.decodeFromString(VoiceBridgeResult.serializer(), raw.toString())
                _state.value = when {
                    res.transcript.isNotBlank() -> VoiceState.Result(res.transcript)
                    res.error == "not-allowed" || res.error == "service-not-allowed" ->
                        VoiceState.Error("Microphone permission denied.", retryable = false)
                    else ->
                        VoiceState.Error("Didn't catch that — try again.", retryable = true)
                }
            } catch (e: Throwable) {
                _state.value = VoiceState.Error("Voice input failed.", retryable = true)
            } finally {
                listening = false
            }
        }
    }

    override fun stop() {
        if (listening) voiceStop()
    }

    override fun reset() {
        _state.value = VoiceState.Idle
    }
}
