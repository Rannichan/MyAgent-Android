package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

import androidx.compose.ui.graphics.Color

private val VioletDark = darkColorScheme(
    primary = VioletPrimaryDark,
    onPrimary = Color(0xFF381E72),
    primaryContainer = VioletPrimaryContainerDark,
    onPrimaryContainer = VioletOnBackgroundDark,
    secondary = VioletSecondaryDark,
    background = VioletBackgroundDark,
    onBackground = VioletOnBackgroundDark,
    surface = VioletSurfaceDark,
    onSurface = VioletOnSurfaceDark,
    surfaceVariant = VioletSurfaceVariantDark,
    onSurfaceVariant = VioletOnSurfaceVariantDark,
    outline = VioletOutlineDark
)

private val VioletLight = VioletDark // Keep "Elegant Dark" layout as persistent theme for default Violet color scheme

private val BlueLight = lightColorScheme(
    primary = BluePrimary,
    primaryContainer = BluePrimaryContainer,
    secondary = BlueSecondary,
    background = BlueBackgroundLight,
    surface = BlueSurfaceLight,
    surfaceVariant = BlueBackgroundLight
)
private val BlueDark = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = BluePrimaryContainerDark,
    onPrimaryContainer = Color(0xFFE6E1E5),
    secondary = BlueSecondaryDark,
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF252329),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2B2930),
    onSurfaceVariant = Color(0xFF938F99),
    outline = Color(0xFF49454F)
)

private val GreenLight = lightColorScheme(
    primary = GreenPrimary,
    primaryContainer = GreenPrimaryContainer,
    secondary = GreenSecondary,
    background = GreenBackgroundLight,
    surface = GreenSurfaceLight,
    surfaceVariant = GreenBackgroundLight
)
private val GreenDark = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = Color(0xFF1B5E20),
    primaryContainer = GreenPrimaryContainerDark,
    onPrimaryContainer = Color(0xFFE6E1E5),
    secondary = GreenSecondaryDark,
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF252329),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2B2930),
    onSurfaceVariant = Color(0xFF938F99),
    outline = Color(0xFF49454F)
)

private val AmberLight = lightColorScheme(
    primary = AmberPrimary,
    primaryContainer = AmberPrimaryContainer,
    secondary = AmberSecondary,
    background = AmberBackgroundLight,
    surface = AmberSurfaceLight,
    surfaceVariant = AmberBackgroundLight
)
private val AmberDark = darkColorScheme(
    primary = AmberPrimaryDark,
    onPrimary = Color(0xFFE65100),
    primaryContainer = AmberPrimaryContainerDark,
    onPrimaryContainer = Color(0xFFE6E1E5),
    secondary = AmberSecondaryDark,
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF252329),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2B2930),
    onSurfaceVariant = Color(0xFF938F99),
    outline = Color(0xFF49454F)
)

@Composable
fun AgentHubTheme(
    themeMode: String = "system",
    themeColor: String = "violet",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when (themeColor.lowercase()) {
        "blue" -> if (darkTheme) BlueDark else BlueLight
        "green" -> if (darkTheme) GreenDark else GreenLight
        "amber" -> if (darkTheme) AmberDark else AmberLight
        else -> if (darkTheme) VioletDark else VioletLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
