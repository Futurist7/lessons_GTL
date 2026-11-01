package com.linguascreen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.linguascreen.data.prefs.ThemeMode

private val Brand = Color(0xFF1B6EF3)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FD),
    onPrimaryContainer = Color(0xFF0B2F6B),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurface = Color(0xFF1B1C20),
    onSurfaceVariant = Color(0xFF5A5E68),
    outline = Color(0xFFD5D8DF),
    error = Color(0xFFD93025),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FADFF),
    onPrimary = Color(0xFF002E6E),
    primaryContainer = Color(0xFF1F3F7A),
    onPrimaryContainer = Color(0xFFDCE8FD),
    background = Color(0xFF111215),
    surface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFF26282E),
    onSurface = Color(0xFFE6E7EB),
    onSurfaceVariant = Color(0xFFA6AAB4),
    outline = Color(0xFF3A3D45),
    error = Color(0xFFFF8A80),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LinguaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
