package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ModernDarkColorScheme = darkColorScheme(
  primary = ElectricCyan,
  onPrimary = Color(0xFF03223A),
  primaryContainer = ElectricCyanContainer,
  onPrimaryContainer = OnElectricCyanContainer,
  secondary = NeonIndigo,
  onSecondary = Color(0xFF1E1B4B),
  secondaryContainer = NeonIndigoContainer,
  onSecondaryContainer = Color(0xFFE0E7FF),
  tertiary = ElectricEmerald,
  onTertiary = Color(0xFF022C22),
  tertiaryContainer = ElectricEmeraldContainer,
  onTertiaryContainer = Color(0xFFD1FAE5),
  background = DarkBackground,
  surface = DarkSurface,
  surfaceVariant = DarkSurfaceVariant,
  surfaceTint = ElectricCyan,
  outline = BorderStroke,
  outlineVariant = BorderStrokeSubtle,
  onBackground = TextPrimary,
  onSurface = TextPrimary,
  onSurfaceVariant = TextSecondary,
  error = Color(0xFFF87171),
  errorContainer = Color(0xFF7F1D1D),
  onError = Color(0xFF450A0A),
  onErrorContainer = Color(0xFFFEE2E2)
)

@Composable
fun MyApplicationTheme(
  // Force dark theme as requested by user
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = ModernDarkColorScheme,
    typography = Typography,
    content = content
  )
}
