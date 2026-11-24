package com.accesolibre.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PrimaryBlue = Color(0xFF1B4B8F)
private val SecondaryTeal = Color(0xFF0E7C7B)
private val AccentGreen = Color(0xFF1F7A4C)
private val ErrorRed = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3F8),
    onPrimaryContainer = Color(0xFF0B254A),
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCEBE9),
    onSecondaryContainer = Color(0xFF063A39),
    tertiary = AccentGreen,
    onTertiary = Color.White,
    error = ErrorRed,
    onError = Color.White,
    background = Color(0xFFF6F8FB),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AC0FF),
    onPrimary = Color(0xFF082752),
    primaryContainer = Color(0xFF163D73),
    secondary = Color(0xFF7ADAD7),
    tertiary = Color(0xFF7FD9A8),
    error = Color(0xFFFF8A80),
)

@Composable
fun AccesoLibreTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
