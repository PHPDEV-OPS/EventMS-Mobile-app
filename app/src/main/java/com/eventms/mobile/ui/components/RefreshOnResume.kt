package com.eventms.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Runs [onResume] every time the screen becomes visible.
 *
 * A ViewModel's `init` only fires the first time a destination is created, so
 * navigating back to a screen would otherwise show whatever was cached when it
 * was last open — a ticket bought a minute ago missing from the list, or an
 * event still showing yesterday's ticket count. Under Navigation Compose the
 * ambient lifecycle owner is the back stack entry, so this fires on return to
 * the destination as well as on app resume.
 *
 * Cheap by design: screens render from the Room cache immediately and the
 * refresh only swaps in newer rows behind it, so nothing flickers.
 */
@Composable
fun RefreshOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume by rememberUpdatedState(onResume)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentOnResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
