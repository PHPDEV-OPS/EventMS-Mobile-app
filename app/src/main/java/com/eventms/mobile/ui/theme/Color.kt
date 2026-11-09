package com.eventms.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Lifted from the web app's globals.css so the two products read as one brand.
 * Orange on white in light mode, orange on near-black in dark mode.
 */

// Brand
val Orange500 = Color(0xFFF97316)   // --primary
val Orange50 = Color(0xFFFFF7ED)    // --accent
val Orange100 = Color(0xFFFFEDD5)
val Orange600 = Color(0xFFEA580C)

// Light surfaces
val White = Color(0xFFFFFFFF)       // --background / --card
val Slate900 = Color(0xFF0F172A)    // --foreground
val Slate500 = Color(0xFF64748B)    // --muted-foreground
val Slate200 = Color(0xFFE2E8F0)    // --border / --input
val Slate50 = Color(0xFFF8FAFC)

// Dark surfaces
val Black = Color(0xFF000000)       // --background
val Zinc950 = Color(0xFF09090B)     // --card
val Zinc900 = Color(0xFF18181B)     // --secondary / --muted
val Zinc800 = Color(0xFF27272A)     // --accent / --border
val Zinc400 = Color(0xFFA1A1AA)     // --muted-foreground
val Zinc50 = Color(0xFFFAFAFA)      // --foreground

// Status
val Red500 = Color(0xFFEF4444)      // --destructive
val Green60 = Color(0xFF16A34A)
val Amber60 = Color(0xFFF59E0B)
val Slate40 = Slate500

/** Scanner verdicts — deliberately high-contrast for a dark venue. */
val ScanPass = Color(0xFF16A34A)
val ScanWarn = Color(0xFFF59E0B)
val ScanFail = Color(0xFFDC2626)
