package com.nabla.chatovoice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val primaryColor = Color(0xFF1565C0)
private val secondaryColor = Color(0xFF0288D1)
private val errorColor = Color(0xFFB00020)

private val LightColors = lightColorScheme(
    primary = primaryColor,
    secondary = secondaryColor,
    error = errorColor,
)

@Composable
fun ChatoVoiceTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
