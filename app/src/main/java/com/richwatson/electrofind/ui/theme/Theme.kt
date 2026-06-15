package com.richwatson.electrofind.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ElectroGreen = Color(0xFF00C853)
private val ElectroDark = Color(0xFF1A1A2E)

private val LightColors = lightColorScheme(
    primary = ElectroGreen,
    onPrimary = Color.White,
    secondary = ElectroDark,
    surface = Color.White,
    background = Color(0xFFF5F5F5)
)

@Composable
fun ElectroFindTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
