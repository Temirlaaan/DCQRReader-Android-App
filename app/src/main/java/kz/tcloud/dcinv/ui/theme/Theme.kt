package kz.tcloud.dcinv.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode

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

/**
 * Disables the Material ripple (the touch "блики") app-wide: a draw-nothing
 * indication for plain clickables; Material3 components are silenced via a
 * null RippleConfiguration below.
 */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -1
}

@OptIn(ExperimentalMaterial3Api::class)
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
    CompositionLocalProvider(
        LocalExtraColors provides extra,
        LocalIndication provides NoIndication,
        LocalRippleConfiguration provides null,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
