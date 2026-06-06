package org.example.project.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform speech-to-text bridge for the add-transaction form.
 *
 * Intentionally tiny + provider-agnostic: an implementation listens to the microphone and
 * pushes [VoiceState] transitions; the orchestrator ([org.example.project.viewmodel.TransactionViewModel])
 * owns transcript→form parsing and the AI category fallback. Only the **web (wasmJs)** target
 * ships a real implementation today; every other platform gets [UnsupportedVoiceInputController]
 * so the mic is simply hidden there. Adding a platform later means writing one real `actual` for
 * [provideVoiceInputController] — nothing else changes.
 */
interface VoiceInputController {

    /** False on platforms/browsers without speech recognition — the UI hides the mic entirely. */
    val isSupported: Boolean

    /** Hot stream of recognition lifecycle. Always starts at [VoiceState.Idle]. */
    val state: StateFlow<VoiceState>

    /** Begin a single listening session. No-op if unsupported or already listening. */
    fun start()

    /** Stop listening early; any captured transcript is still emitted as a final result. */
    fun stop()

    /** Return the stream to [VoiceState.Idle] after a result/error has been consumed. */
    fun reset()
}

/** Lifecycle of one recognition session. */
sealed interface VoiceState {
    /** Not listening. */
    data object Idle : VoiceState

    /** Microphone is open. [partial] carries interim words when the engine provides them. */
    data class Listening(val partial: String = "") : VoiceState

    /** Final transcript captured — ready to parse. */
    data class Result(val transcript: String) : VoiceState

    /** Recognition failed (permission denied, no speech, network). [retryable] gates the UI hint. */
    data class Error(val message: String, val retryable: Boolean = true) : VoiceState
}

/**
 * Coarse status the form renders for the mic button. Decoupled from [VoiceState] so the
 * "AI is classifying the category" phase (owned by the ViewModel, not the controller) can be
 * represented too.
 */
enum class VoiceStatus {
    /** Mic idle, tap to talk. */
    Idle,

    /** Microphone open, listening. */
    Listening,

    /** Transcript captured; parsing / AI-classifying in progress. */
    Processing,
}

/**
 * No-op controller for platforms without a real speech bridge yet (everything but web).
 * Reports [isSupported] = false so the mic button never appears.
 */
class UnsupportedVoiceInputController : VoiceInputController {
    override val isSupported: Boolean = false
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()
    override fun start() = Unit
    override fun stop() = Unit
    override fun reset() = Unit
}

/**
 * Platform factory. wasmJs returns a real Web Speech API controller; all other targets return
 * [UnsupportedVoiceInputController]. Plain (non-Composable) so it can be a ViewModel constructor
 * default — when Android/iOS gain real impls they can inject platform context here.
 */
expect fun provideVoiceInputController(): VoiceInputController
