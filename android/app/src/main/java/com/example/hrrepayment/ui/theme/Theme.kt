package com.example.hrrepayment.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Khaki Sky colour scheme. Fixed light scheme: the design explicitly asks for one
 * deterministic look, so the system dark-mode setting is deliberately ignored.
 */
private val KhakiSkyScheme = lightColorScheme(
    primary = Tokens.Primary,
    onPrimary = Tokens.OnPrimary,
    primaryContainer = Tokens.PrimaryContainer,
    onPrimaryContainer = Tokens.OnPrimaryContainer,
    inversePrimary = Tokens.InversePrimary,

    secondary = Tokens.Secondary,
    onSecondary = Tokens.OnPrimary,
    secondaryContainer = Tokens.SecondaryContainer,
    onSecondaryContainer = Tokens.OnSecondaryContainer,

    tertiary = Tokens.Secondary,
    onTertiary = Tokens.OnPrimary,
    tertiaryContainer = Tokens.TertiaryContainer,
    onTertiaryContainer = Tokens.OnTertiaryContainer,

    background = Tokens.Surface,
    onBackground = Tokens.OnSurface,

    surface = Tokens.Surface,
    onSurface = Tokens.OnSurface,
    surfaceVariant = Tokens.SurfaceContainer,
    onSurfaceVariant = Tokens.OnSurfaceVariant,
    surfaceContainerLowest = Color(0xFFF8F3E7),
    surfaceContainerLow = Tokens.SurfaceContainerLow,
    surfaceContainer = Tokens.SurfaceContainer,
    surfaceContainerHigh = Tokens.SurfaceContainerHigh,
    surfaceContainerHighest = Tokens.SurfaceContainerHighest,

    outline = Tokens.Outline,
    outlineVariant = Tokens.OutlineVariant,
    inverseSurface = Tokens.InverseSurface,
    inverseOnSurface = Tokens.InverseOnSurface,

    error = Tokens.Error,
    onError = Tokens.OnError,
    errorContainer = Tokens.ErrorContainer,
    onErrorContainer = Tokens.OnErrorContainer,

    scrim = Color(0x99000000),
)

/** M3 Expressive shape scale: buttons are capsules, cards 20dp, sheets 28dp. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(Tokens.CornerCard.dp),
    extraLarge = RoundedCornerShape(Tokens.CornerPanel.dp),
)

/**
 * The one and only theme entry point.
 *
 * Uses `MaterialTheme` from material3 1.4.0 (the M3 Expressive release) with the
 * expressive shape scale and the Khaki Sky roles. The springy motion the design asks
 * for is declared in [AppMotion] and applied by the components themselves, because
 * `MaterialExpressiveTheme` is published behind an opt-in marker that cannot be
 * consumed by this offline toolchain.
 */
@Composable
fun HRPayDebtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KhakiSkyScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
