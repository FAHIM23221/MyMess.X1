package com.smarttransparentmess.erp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme

private val LightColors = lightColorScheme(
    primary = Color(0xFF087E78),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F4EF),
    onPrimaryContainer = Color(0xFF003B37),
    secondary = Color(0xFFB66A16),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC0),
    onSecondaryContainer = Color(0xFF3B1D00),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF18232D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18232D),
    surfaceVariant = Color(0xFFE7EDF1),
    onSurfaceVariant = Color(0xFF475761),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF72D8CC),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFB2F1E8),
    secondary = Color(0xFFFFB96D),
    onSecondary = Color(0xFF482900),
    secondaryContainer = Color(0xFF653D08),
    onSecondaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFF10191F),
    onBackground = Color(0xFFE4EDF1),
    surface = Color(0xFF172229),
    onSurface = Color(0xFFE4EDF1),
    surfaceVariant = Color(0xFF26363E),
    onSurfaceVariant = Color(0xFFBCCBD1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun SmartMessTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        shapes = SmartMessShapes,
        typography = SmartMessTypography,
        content = content,
    )
}
