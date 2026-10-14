package de.gpsradar.satellites.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color(0xFF00363F),
    primaryContainer = SurfaceNavy,
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = CardNavy,
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = SignalGreen,
    onTertiary = Color.Black,
    background = SpaceNavy,
    onBackground = TextLight,
    surface = DeepNavy,
    onSurface = TextLight,
    surfaceVariant = SurfaceNavy,
    onSurfaceVariant = TextMuted,
    outline = RadarGrid
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF006874),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF97F0FF),
    onSecondaryContainer = Color(0xFF001F24),
    tertiary = Color(0xFF006C4C),
    background = SurfaceLight,
    onBackground = Color(0xFF0F172A),
    surface = CardLight,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun GpsRadarTheme(
    darkTheme: Boolean = true, // Default to high-contrast dark telemetry theme for best radar visibility
    dynamicColor: Boolean = false,
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
