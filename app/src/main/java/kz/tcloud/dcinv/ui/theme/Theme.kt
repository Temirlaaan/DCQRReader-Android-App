package kz.tcloud.dcinv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kz.tcloud.dcinv.ui.theme.BackgroundLight
import kz.tcloud.dcinv.ui.theme.OnSurfaceLight

/**
 * App-specific colors that don't map cleanly onto the M3 ColorScheme slots
 * (hairline borders, the "secondary text" gray, the green accent).
 */
data class DcInvExtraColors(
    val border: Color,
    val secondaryText: Color,
    val accent: Color,
)

val LocalExtraColors = staticCompositionLocalOf {
    DcInvExtraColors(border = BorderLight, secondaryText = SecondaryTextLight, accent = Accent)
}

object DcInvTheme {
    val extra: DcInvExtraColors
        @Composable get() = LocalExtraColors.current
}

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = Primary,
    tertiary = Accent,
    onTertiary = Color.White,
    tertiaryContainer = AccentContainerLight,
    onTertiaryContainer = OnAccentContainerLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = BackgroundLight,
    onSurfaceVariant = SecondaryTextLight,
    outline = BorderLight,
    error = ErrorLight,
    onError = OnPrimary,
)

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = Color(0xFFACC7FF),
    tertiary = Accent,
    onTertiary = Color.White,
    tertiaryContainer = AccentContainerDark,
    onTertiaryContainer = OnAccentContainerDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = SecondaryTextDark,
    outline = BorderDark,
    error = ErrorDark,
    onError = Color(0xFF1A1A1A),
)

@Composable
fun DcInvTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extra = if (darkTheme) {
        DcInvExtraColors(border = BorderDark, secondaryText = SecondaryTextDark, accent = Accent)
    } else {
        DcInvExtraColors(border = BorderLight, secondaryText = SecondaryTextLight, accent = Accent)
    }
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
