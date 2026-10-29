package com.controlefinanceiro.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controlefinanceiro.app.data.ThemeMode
import com.controlefinanceiro.app.data.db.ExpenseBlock

// Brand: black and yellow, in the line of investment apps like XP. Yellow is the accent; in light
// mode the main color is near-black (buttons in black with yellow text), since yellow text on
// white is hard to read.
private val XpYellow = Color(0xFFFFD400)

private val LightColors = lightColorScheme(
    primary = Color(0xFF151515),
    onPrimary = XpYellow,
    primaryContainer = XpYellow,
    onPrimaryContainer = Color(0xFF151515),
    secondary = Color(0xFF555555),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECECEC),
    onSecondaryContainer = Color(0xFF151515),
    tertiary = Color(0xFF9A6B00),
    tertiaryContainer = Color(0xFFFFF0B3),
    background = Color(0xFFF4F4F4),
    onBackground = Color(0xFF151515),
    surface = Color(0xFFF4F4F4),
    onSurface = Color(0xFF151515),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF616161),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEDEDED),
    surfaceContainerHigh = Color(0xFFE6E6E6),
    surfaceContainerHighest = Color(0xFFDDDDDD),
    outline = Color(0xFF8C8C8C),
    outlineVariant = Color(0xFFDDDDDD),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410001),
)

private val DarkColors = darkColorScheme(
    primary = XpYellow,
    onPrimary = Color(0xFF111111),
    primaryContainer = XpYellow,
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color(0xFFEDEDED),
    tertiary = Color(0xFFFFC44D),
    tertiaryContainer = Color(0xFF4A3A00),
    background = Color(0xFF0B0B0B),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF0B0B0B),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainerLowest = Color(0xFF060606),
    surfaceContainerLow = Color(0xFF161616),
    surfaceContainer = Color(0xFF1C1C1C),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),
    outline = Color(0xFF707070),
    outlineVariant = Color(0xFF2A2A2A),
    error = Color(0xFFFF8A7E),
    errorContainer = Color(0xFF5C1510),
    onErrorContainer = Color(0xFFFFDAD5),
)

/** One accent per block, matching the colors of the spreadsheet blocks. */
@Immutable
data class BlockColors(
    val tithe: Color,
    val fixed: Color,
    val couple: Color,
    val personal: Color,
    val investment: Color,
    val extra: Color,
) {
    fun of(block: ExpenseBlock): Color = when (block) {
        ExpenseBlock.TITHE -> tithe
        ExpenseBlock.FIXED -> fixed
        ExpenseBlock.COUPLE -> couple
        ExpenseBlock.PERSONAL -> personal
        ExpenseBlock.INVESTMENT -> investment
        ExpenseBlock.EXTRA -> extra
    }
}

private val LightBlocks = BlockColors(
    tithe = Color(0xFF9A6B00), fixed = Color(0xFF2F5D8A), couple = Color(0xFF1F8FB0),
    personal = Color(0xFF7A4BB0), investment = Color(0xFF13855F), extra = Color(0xFFC2611A),
)
private val DarkBlocks = BlockColors(
    tithe = Color(0xFFFFD400), fixed = Color(0xFF9CC2EC), couple = Color(0xFF7FD0EA),
    personal = Color(0xFFC9A6F2), investment = Color(0xFF79DBB0), extra = Color(0xFFF6AE76),
)

val LocalBlockColors = staticCompositionLocalOf { LightBlocks }

/** Money and status colors: positive, warning (close to the limit) and negative. */
@Immutable
data class MoneyColors(val positive: Color, val warning: Color, val negative: Color)

private val LightMoney = MoneyColors(Color(0xFF13855F), Color(0xFFC77700), Color(0xFFBA2D24))
private val DarkMoney = MoneyColors(Color(0xFF79DBB0), Color(0xFFFFC266), Color(0xFFFF8A7E))

val LocalMoneyColors = staticCompositionLocalOf { LightMoney }

/** Gradient used by the hero cards (month and year overview). */
@Immutable
data class HeroColors(val brush: Brush, val content: Color, val muted: Color, val tile: Color)

// The month and year overview cards are black in both modes, with white numbers, like XP's cards.
private val LightHero = HeroColors(
    brush = Brush.linearGradient(listOf(Color(0xFF111111), Color(0xFF2B2B2B))),
    content = Color.White, muted = Color.White.copy(alpha = 0.7f), tile = Color.White.copy(alpha = 0.1f),
)
private val DarkHero = HeroColors(
    brush = Brush.linearGradient(listOf(Color(0xFF1E1E1E), Color(0xFF141414))),
    content = Color.White, muted = Color.White.copy(alpha = 0.68f), tile = Color.White.copy(alpha = 0.08f),
)

val LocalHeroColors = staticCompositionLocalOf { LightHero }

/** Money is shown with tabular figures so columns of values line up. */
private const val TABULAR = "tnum"

private val AppTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, fontFeatureSettings = TABULAR),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = TABULAR),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = TABULAR),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TABULAR),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TABULAR),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FinanceTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(
        LocalBlockColors provides if (dark) DarkBlocks else LightBlocks,
        LocalMoneyColors provides if (dark) DarkMoney else LightMoney,
        LocalHeroColors provides if (dark) DarkHero else LightHero,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
