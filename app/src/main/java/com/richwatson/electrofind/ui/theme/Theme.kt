package com.richwatson.electrofind.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.richwatson.electrofind.viewmodel.ThemeMode

private val ElectroGreen = Color(0xFF00C853)
private val ElectroDark = Color(0xFF1A1A2E)

private val LightColors = lightColorScheme(
    primary = ElectroGreen,
    onPrimary = Color.White,
    secondary = ElectroDark,
    surface = Color.White,
    background = Color(0xFFF5F5F5)
)

private val DarkColors = darkColorScheme(
    primary = ElectroGreen,
    onPrimary = Color.Black,
    secondary = ElectroGreen,
)

@Composable
fun ElectroFindTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
