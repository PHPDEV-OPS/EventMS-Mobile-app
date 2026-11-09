package com.eventms.mobile.core

import retrofit2.HttpException
import java.io.IOException

/**
 * Success or a human-readable failure. Named Outcome rather than Result to stay
 * out of the way of kotlin.Result.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val message: String, val cause: Throwable? = null) : Outcome<Nothing>

    val successOrNull: T? get() = (this as? Success)?.data
}

/** Runs [block], turning the usual network failures into readable messages. */
suspend fun <T> outcomeOf(block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (e: IOException) {
    Outcome.Failure("No connection. Check your network and try again.", e)
} catch (e: HttpException) {
    Outcome.Failure(httpMessage(e), e)
} catch (e: Exception) {
    Outcome.Failure(e.message ?: "Something went wrong", e)
}

private fun httpMessage(e: HttpException): String = when (e.code()) {
    401 -> "Your session has expired. Please sign in again."
    403 -> "You do not have permission to do that."
    404 -> "Not found."
    409 -> "That conflicts with something that already exists."
    in 500..599 -> "Something went wrong on our side. Please try again."
    else -> {
        // Server-side validation messages are written for people ("Only 2
        // tickets left"), so they are worth showing. Anything that looks like
        // an internal diagnostic is not.
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        body?.let { extractMessage(it) }?.takeIf { it.isPresentable() }
            ?: "That didn't work. Please try again."
    }
}

/**
 * Keeps database and infrastructure wording out of the UI. A stack trace, a
 * relation name or a permission error tells a curious user how the system is
 * put together; a plain sentence does not.
 */
private fun String.isPresentable(): Boolean {
    val lowered = lowercase()
    val leaks = listOf(
        "permission denied", "relation", "function", "column", "constraint",
        "postgres", "sql", "pgrst", "jwt", "role", "schema", "duplicate key",
        "violates", "syntax", "exception", "null value"
    )
    return length in 1..160 && leaks.none { it in lowered }
}

private fun extractMessage(body: String): String? = runCatching {
    val json = org.json.JSONObject(body)
    when {
        json.has("message") -> json.getString("message")
        json.has("error") -> json.getString("error")
        else -> null
    }?.takeIf { it.isNotBlank() }
}.getOrNull()
