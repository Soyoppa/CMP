package org.example.project.model

data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val isStreaming: Boolean = false,
    // Usage metadata for assistant messages (null for user/streaming messages).
    val model: String? = null,
    val totalTokens: Int? = null,
) {
    enum class Role { USER, ASSISTANT }
}
