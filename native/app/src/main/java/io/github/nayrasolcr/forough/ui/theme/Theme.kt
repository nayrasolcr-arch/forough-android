package io.github.nayrasolcr.forough.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nayrasolcr.forough.R

/**
 * Forough theme — a faithful mapping of the Linux client's libadwaita dark
 * style (the desktop app uses standard Adwaita widgets with the default
 * accent) plus the Forough brand gold from the logo.
 *
 * libadwaita dark palette:
 *   window_bg #242428 · shade #1d1d20 · card #2e2e32 (rgba white .08)
 *   fg #ffffff · dim fg ~#dedede
 *   accent/suggested #3584e4 · destructive #e01b24
 * Forough brand: gold #fab649 on deep navy #0c2d5e/#052351.
 *
 * Typography is Vazirmatn — a polished Persian+Latin family that gives the
 * RTL interface real typographic quality (the system default renders
 * Persian flat and small).
 */

val AdwWindowBg = Color(0xFF242428)
val AdwShade = Color(0xFF1D1D20)
val AdwCard = Color(0xFF2E2E32)
val AdwCardHigher = Color(0xFF36363A)
val AdwFg = Color(0xFFFFFFFF)
val AdwDimFg = Color(0xFFDEDEDE)
val AdwOutline = Color(0xFF4E4E54)
val AdwAccent = Color(0xFF3584E4)
val AdwAccentDim = Color(0xFF1B5FA8)
val AdwAccentGlow = Color(0x663584E4)
val AdwDestructive = Color(0xFFE01B24)
val ConnectedGreen = Color(0xFF26BF59)
val ConnectedGreenGlow = Color(0x4D26BF59)
val ForoughGold = Color(0xFFFAB649)
val ForoughNavy = Color(0xFF052351)
val GraphRx = Color(0xFF338CF0)   // desktop draw color rgb(0.20, 0.55, 0.95)
val GraphTx = Color(0xFF26BF59)   // desktop draw color rgb(0.15, 0.75, 0.35)

// Vazirmatn font family — Persian + Latin, regular through bold.
val Vazirmatn = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.vazirmatn_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.vazirmatn_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private val ForoughTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,
        fontSize = 42.sp, lineHeight = 50.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 34.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 27.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp, lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp
    )
)

private val ForoughShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val ForoughDarkColors: ColorScheme = darkColorScheme(
    primary = AdwAccent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = AdwAccentDim,
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = ForoughGold,
    onSecondary = Color(0xFF1D1D20),
    secondaryContainer = Color(0xFF3A3A40),
    onSecondaryContainer = ForoughGold,
    tertiary = ForoughGold,
    error = AdwDestructive,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF5A1015),
    onErrorContainer = Color(0xFFFFB4AB),
    background = AdwWindowBg,
    onBackground = AdwFg,
    surface = AdwWindowBg,
    onSurface = AdwFg,
    surfaceVariant = AdwCard,
    onSurfaceVariant = AdwDimFg,
    surfaceContainerLowest = AdwShade,
    surfaceContainerLow = Color(0xFF2A2A2E),
    surfaceContainer = AdwCard,
    surfaceContainerHigh = AdwCardHigher,
    surfaceContainerHighest = Color(0xFF3E3E44),
    outline = AdwOutline,
    outlineVariant = Color(0xFF3E3E44),
    inverseSurface = Color(0xFFE6E6E6),
    inverseOnSurface = Color(0xFF1D1D20)
)

/**
 * The Forough client always presents the libadwaita-style dark look —
 * same as the desktop client's dark style preference.
 */
@Composable
fun ForoughTheme(content: @Composable () -> Unit) {
    // Dark is the Forough look on both platforms; keep MaterialTheme API
    // surface for future light-variant support.
    MaterialTheme(
        colorScheme = ForoughDarkColors,
        typography = ForoughTypography,
        shapes = ForoughShapes,
        content = content
    )
}
