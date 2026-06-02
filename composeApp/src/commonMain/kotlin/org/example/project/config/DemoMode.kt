package org.example.project.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DemoMode {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    val isEnabled get() = _enabled.value

    fun toggle() { _enabled.value = !_enabled.value }
    fun set(value: Boolean) { _enabled.value = value }
}
