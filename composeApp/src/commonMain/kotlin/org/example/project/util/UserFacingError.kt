package org.example.project.util

/**
 * An error whose [message] was written for the end user and is safe to show verbatim.
 *
 * Anything else (Ktor, platform, SDK exceptions) can embed request URLs — including the Sheets
 * `?key=` query parameter and the Apps Script `/exec` URL — so those never reach the UI directly.
 */
class UserFacingException(message: String) : Exception(message)

/**
 * The text to show the user for this failure: the message of a [UserFacingException], otherwise
 * [fallback]. Never returns a raw third-party exception message.
 */
fun Throwable.toUserMessage(fallback: String): String =
    (this as? UserFacingException)?.message ?: fallback
