package com.example.whereabouts.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Light colour scheme ───────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary            = PastelBlue_Primary,
    onPrimary          = PastelBlue_OnPrimary,
    primaryContainer   = PastelBlue_PrimaryContainer,
    onPrimaryContainer = PastelBlue_OnPrimaryContainer,

    secondary            = PastelBlue_Secondary,
    onSecondary          = PastelBlue_OnSecondary,
    secondaryContainer   = PastelBlue_SecondaryContainer,
    onSecondaryContainer = PastelBlue_OnSecondaryContainer,

    tertiary            = PastelBlue_Tertiary,
    onTertiary          = PastelBlue_OnTertiary,
    tertiaryContainer   = PastelBlue_TertiaryContainer,
    onTertiaryContainer = PastelBlue_OnTertiaryContainer,

    background   = PastelBlue_Background,
    onBackground = PastelBlue_OnBackground,
    surface      = PastelBlue_Surface,
    onSurface    = PastelBlue_OnSurface,

    surfaceVariant   = PastelBlue_SurfaceVariant,
    onSurfaceVariant = PastelBlue_OnSurfaceVariant,

    error              = PastelBlue_Error,
    onError            = PastelBlue_OnError,
    errorContainer     = PastelBlue_ErrorContainer,
    onErrorContainer   = PastelBlue_OnErrorContainer,

    outline        = PastelBlue_Outline,
    outlineVariant = PastelBlue_OutlineVariant,

    inverseSurface   = PastelBlue_InverseSurface,
    inverseOnSurface = PastelBlue_InverseOnSurface,
    inversePrimary   = PastelBlue_InversePrimary,
)

// ── Dark colour scheme ────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary            = PastelBlueDark_Primary,
    onPrimary          = PastelBlueDark_OnPrimary,
    primaryContainer   = PastelBlueDark_PrimaryContainer,
    onPrimaryContainer = PastelBlueDark_OnPrimaryContainer,

    secondary            = PastelBlueDark_Secondary,
    onSecondary          = PastelBlueDark_OnSecondary,
    secondaryContainer   = PastelBlueDark_SecondaryContainer,
    onSecondaryContainer = PastelBlueDark_OnSecondaryContainer,

    tertiary            = PastelBlueDark_Tertiary,
    onTertiary          = PastelBlueDark_OnTertiary,
    tertiaryContainer   = PastelBlueDark_TertiaryContainer,
    onTertiaryContainer = PastelBlueDark_OnTertiaryContainer,

    background   = PastelBlueDark_Background,
    onBackground = PastelBlueDark_OnBackground,
    surface      = PastelBlueDark_Surface,
    onSurface    = PastelBlueDark_OnSurface,

    surfaceVariant   = PastelBlueDark_SurfaceVariant,
    onSurfaceVariant = PastelBlueDark_OnSurfaceVariant,

    error              = PastelBlueDark_Error,
    onError            = PastelBlueDark_OnError,
    errorContainer     = PastelBlueDark_ErrorContainer,
    onErrorContainer   = PastelBlueDark_OnErrorContainer,

    outline        = PastelBlueDark_Outline,
    outlineVariant = PastelBlueDark_OutlineVariant,

    inverseSurface   = PastelBlueDark_InverseSurface,
    inverseOnSurface = PastelBlueDark_InverseOnSurface,
    inversePrimary   = PastelBlueDark_InversePrimary,
)

// ── Theme entry point ─────────────────────────────────────────────────────────

@Composable
fun WhereAboutsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set dynamicColor = false to always use our pastel palette
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    // Tint the system status bar to match the theme background
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
