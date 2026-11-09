package com.eventms.mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/*
 * Mirrors the web app's CSS custom properties. Orange stays the primary in both
 * modes, exactly as --primary does on the web.
 */
private val LightColors = lightColorScheme(
    primary = Orange500,
    onPrimary = White,
    primaryContainer = Orange50,
    onPrimaryContainer = Orange600,
    secondary = Slate500,
    onSecondary = White,
    secondaryContainer = Slate50,
    onSecondaryContainer = Slate900,
    tertiary = Orange600,
    background = White,
    onBackground = Slate900,
    surface = White,
    onSurface = Slate900,
    surfaceVariant = Slate50,
    onSurfaceVariant = Slate500,
    outline = Slate200,
    outlineVariant = Slate200,
    error = Red500,
    onError = White
)

private val DarkColors = darkColorScheme(
    primary = Orange500,
    onPrimary = White,
    primaryContainer = Zinc800,
    onPrimaryContainer = Orange50,
    secondary = Zinc400,
    onSecondary = Zinc50,
    secondaryContainer = Zinc900,
    onSecondaryContainer = Zinc50,
    tertiary = Orange600,
    background = Black,
    onBackground = Zinc50,
    surface = Zinc950,
    onSurface = Zinc50,
    surfaceVariant = Zinc900,
    onSurfaceVariant = Zinc400,
    outline = Zinc800,
    outlineVariant = Zinc800,
    error = Red500,
    onError = White
)

/** --radius is 1.25rem on the web; these are the nearest Material steps. */
private val EventMsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun EventMsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = EventMsTypography,
        shapes = EventMsShapes,
        content = content
    )
}
