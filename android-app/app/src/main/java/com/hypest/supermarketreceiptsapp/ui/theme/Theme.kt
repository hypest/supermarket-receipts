package com.hypest.supermarketreceiptsapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Define more comprehensive dark color scheme
private val DarkColorScheme = darkColorScheme(
    primary = Purple80, // Keep primary as defined
    onPrimary = Color(0xFF381E72), // Darker purple for text/icons on primary
    primaryContainer = Color(0xFF4F378A), // Darker container color
    onPrimaryContainer = Color(0xFFEADDFF), // Lighter text/icons on primary container

    secondary = PurpleGrey80, // Keep secondary as defined
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),

    tertiary = Pink80, // Keep tertiary as defined
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),

    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),

    background = Color(0xFF1C1B1F), // Dark background
    onBackground = Color(0xFFE6E1E5), // Light text/icons on dark background

    surface = Color(0xFF1C1B1F), // Dark surface (same as background often)
    onSurface = Color(0xFFE6E1E5), // Light text/icons on dark surface

    surfaceVariant = Color(0xFF49454F), // Slightly lighter dark surface variant
    onSurfaceVariant = Color(0xFFCAC4D0), // Light text/icons on surface variant

    outline = Color(0xFF938F99)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun SupermarketReceiptsAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
