package org.example.project.model

data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val isStreaming: Boolean = false
) {
    enum class Role { USER, ASSISTANT }
}
