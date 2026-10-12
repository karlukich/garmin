package com.carlos.garmin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.carlos.garmin.AppSettings
import com.carlos.garmin.ThemeMode

/** Colores de los gráficos; cambian con los ajustes y los gráficos se redibujan solos. */
object Palette {
    var primary by mutableStateOf(Color(0xFF1F5FBF))
    var accent by mutableStateOf(Color(0xFFE9A23B))
    var dark by mutableStateOf(false)
}

@Composable
fun AppTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val base = Color(settings.primary.toInt())
    val primary = if (dark) lerp(base, Color.White, 0.4f) else base
    val accent = Color(settings.accent.toInt())

    // Se actualizan durante la composición: los gráficos leen estos valores.
    Palette.primary = primary
    Palette.accent = accent
    Palette.dark = dark

    val scheme = if (dark) {
        darkColorScheme(
            primary = primary, onPrimary = Color(0xFF0B1F3A),
            primaryContainer = Color(0xFF1E3A6E), onPrimaryContainer = Color(0xFFD6E6FB),
            secondary = Color(0xFF8FB2E3), onSecondary = Color(0xFF0B1F3A),
            secondaryContainer = Color(0xFF1E3A6E), onSecondaryContainer = Color(0xFFD6E6FB),
            background = Color(0xFF0B1526), onBackground = Color(0xFFE6EEFB),
            surface = Color(0xFF12203A), onSurface = Color(0xFFE6EEFB),
            surfaceVariant = Color(0xFF1B2D4D), onSurfaceVariant = Color(0xFFA9BBD6),
            outline = Color(0xFF3A5280), outlineVariant = Color(0xFF26395C),
            error = Color(0xFFFFB4AB),
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = Color.White,
            primaryContainer = Color(0xFFD6E6FB), onPrimaryContainer = Color(0xFF0F3A80),
            secondary = Color(0xFF4A6FA5), onSecondary = Color.White,
            secondaryContainer = Color(0xFFD6E6FB), onSecondaryContainer = Color(0xFF0F3A80),
            background = Color(0xFFEEF3FA), onBackground = Color(0xFF0B1F3A),
            surface = Color.White, onSurface = Color(0xFF0B1F3A),
            surfaceVariant = Color(0xFFE3EAF5), onSurfaceVariant = Color(0xFF51627D),
            outline = Color(0xFFBCCBE0), outlineVariant = Color(0xFFDCE5F2),
            error = Color(0xFFB3261E),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
