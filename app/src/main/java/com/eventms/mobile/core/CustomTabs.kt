package com.eventms.mobile.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.eventms.mobile.R

/**
 * Opens the payment page in a Chrome Custom Tab.
 *
 * Why not a WebView: the card fields would then live in a view this app owns,
 * which drags the whole app into PCI scope and means a bug here could read what
 * someone types. A Custom Tab runs in the browser's own process — the app can
 * hand it a URL and nothing else. The provider keeps its address bar and
 * padlock, so the customer can see who they are actually paying.
 *
 * Why not a plain browser Intent: that throws the customer out to another app
 * and leaves them to find their way back. A Custom Tab is themed like the app,
 * animates up from the bottom, and its close button returns straight here.
 */
class CustomTabsLauncher internal constructor(
    private val context: Context,
    private val session: CustomTabsSession?
) {

    fun launch(url: String, toolbarColor: Color, onSurface: Color) {
        val uri = Uri.parse(url)

        val colors = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColor.toArgb())
            .setNavigationBarColor(toolbarColor.toArgb())
            .build()

        val intent = CustomTabsIntent.Builder(session)
            .setDefaultColorSchemeParams(colors)
            .setShowTitle(true)
            // Collapses the address bar as the page scrolls, so the payment form
            // gets the room it needs once the customer starts filling it in.
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setCloseButtonPosition(CustomTabsIntent.CLOSE_BUTTON_POSITION_START)
            // A partial tab: the sheet covers most of the screen but leaves the
            // checkout visible behind it, so the payment reads as a step within
            // the app rather than a jump out of it. ADJUSTABLE lets the customer
            // drag it to full height when the card form needs the space.
            .setInitialActivityHeightPx(
                sheetHeightPx(),
                CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE
            )
            .setToolbarCornerRadiusDp(SHEET_CORNER_RADIUS_DP)
            .setStartAnimations(context, R.anim.slide_in_up, R.anim.settle_back)
            .setExitAnimations(context, R.anim.settle_forward, R.anim.slide_out_down)
            .build()

        try {
            intent.launchUrl(context, uri)
        } catch (e: Exception) {
            // No browser supports Custom Tabs (rare, but some minimal ROMs).
            // Falling back keeps the payment possible; it just leaves the app.
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }
    }

    /**
     * How tall the sheet opens.
     *
     * 72% leaves a clear band of the app on top — enough to read as "still in
     * EventMS" without cramping the payment form. Chrome ignores this below
     * version 107 and simply opens full height, which is a fine degradation.
     */
    private fun sheetHeightPx(): Int {
        val screenHeight = context.resources.displayMetrics.heightPixels
        return (screenHeight * SHEET_HEIGHT_FRACTION).toInt()
    }

    private companion object {
        const val SHEET_HEIGHT_FRACTION = 0.72f
        const val SHEET_CORNER_RADIUS_DP = 16
    }
}

/**
 * Binds to the browser's Custom Tabs service for the lifetime of the screen.
 *
 * The binding is what makes the launch feel instant: warming up starts the
 * browser process ahead of time, and [prefetchUrl] lets it fetch and render the
 * payment page before the customer taps. Without it the tab slides up onto a
 * white rectangle and the page appears a second later, which reads as a stall
 * no matter how good the animation is.
 */
@Composable
fun rememberCustomTabsLauncher(prefetchUrl: String? = null): CustomTabsLauncher {
    val context = LocalContext.current
    val holder = remember { SessionHolder() }

    DisposableEffect(context) {
        val packageName = CustomTabsClient.getPackageName(context, null)
        if (packageName == null) {
            // No Custom Tabs provider; launch() falls back to a plain intent.
            return@DisposableEffect onDispose { }
        }

        val connection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(
                name: ComponentName,
                client: CustomTabsClient
            ) {
                client.warmup(0L)
                holder.session = client.newSession(null)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                holder.session = null
            }
        }

        val bound = runCatching {
            CustomTabsClient.bindCustomTabsService(context, packageName, connection)
        }.getOrDefault(false)

        onDispose {
            if (bound) runCatching { context.unbindService(connection) }
            holder.session = null
        }
    }

    // Re-issued whenever the URL arrives, so the page is already warm by the
    // time the customer decides to pay.
    DisposableEffect(prefetchUrl, holder.session) {
        if (!prefetchUrl.isNullOrBlank()) {
            runCatching {
                holder.session?.mayLaunchUrl(Uri.parse(prefetchUrl), null, null)
            }
        }
        onDispose { }
    }

    return remember(holder.session) { CustomTabsLauncher(context, holder.session) }
}

/** Mutable box so the service callback can hand the session back to Compose. */
private class SessionHolder {
    var session: CustomTabsSession? by mutableStateOf<CustomTabsSession?>(null)
}
