package com.eventms.mobile.ui.navigation

import androidx.lifecycle.SavedStateHandle

/**
 * Reads a navigation argument as a Long without assuming how it was boxed.
 *
 * `SavedStateHandle.get<T>` casts unchecked, so asking for a String when the
 * route declared `NavType.LongType` throws ClassCastException at the call site.
 * Pulling the raw value out and converting avoids depending on that detail.
 */
fun SavedStateHandle.longArg(key: String, default: Long = 0L): Long =
    when (val raw = get<Any?>(key)) {
        is Long -> raw
        is Int -> raw.toLong()
        is String -> raw.toLongOrNull() ?: default
        is Number -> raw.toLong()
        else -> default
    }

/** Same idea for string arguments, tolerating a non-String box. */
fun SavedStateHandle.stringArg(key: String, default: String = ""): String =
    when (val raw = get<Any?>(key)) {
        is String -> raw
        null -> default
        else -> raw.toString()
    }
