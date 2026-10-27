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

// Brand: deep emerald on calm, slightly warm neutrals.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDE4),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4B635C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE8E3),
    onSecondaryContainer = Color(0xFF07201A),
    tertiary = Color(0xFFB4610F),
    tertiaryContainer = Color(0xFFFFDDBF),
    background = Color(0xFFF4F6F5),
    onBackground = Color(0xFF151B19),
    surface = Color(0xFFF4F6F5),
    onSurface = Color(0xFF151B19),
    surfaceVariant = Color(0xFFE3E9E6),
    onSurfaceVariant = Color(0xFF5B6662),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEDF1EF),
    surfaceContainerHigh = Color(0xFFE6EBE9),
    surfaceContainerHighest = Color(0xFFDFE5E2),
    outline = Color(0xFF8A9590),
    outlineVariant = Color(0xFFDCE3E0),
    error = Color(0xFFBA2D24),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410001),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD8C2),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF0F5146),
    onPrimaryContainer = Color(0xFFB5F1E1),
    secondary = Color(0xFFB3CCC4),
    onSecondary = Color(0xFF1E352F),
    secondaryContainer = Color(0xFF2A3D38),
    onSecondaryContainer = Color(0xFFCFE8DF),
    tertiary = Color(0xFFFFB878),
    tertiaryContainer = Color(0xFF5C3300),
    background = Color(0xFF0D1110),
    onBackground = Color(0xFFE1E6E4),
    surface = Color(0xFF0D1110),
    onSurface = Color(0xFFE1E6E4),
    surfaceVariant = Color(0xFF2A322F),
    onSurfaceVariant = Color(0xFFAAB5B0),
    surfaceContainerLowest = Color(0xFF090C0B),
    surfaceContainerLow = Color(0xFF151A19),
    surfaceContainer = Color(0xFF1A201E),
    surfaceContainerHigh = Color(0xFF232A28),
    surfaceContainerHighest = Color(0xFF2D3532),
    outline = Color(0xFF75807B),
    outlineVariant = Color(0xFF2A322F),
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
    tithe = Color(0xFF8E6A12), fixed = Color(0xFF2F5D8A), couple = Color(0xFF1F8FB0),
    personal = Color(0xFF7A4BB0), investment = Color(0xFF13855F), extra = Color(0xFFC2611A),
)
private val DarkBlocks = BlockColors(
    tithe = Color(0xFFE8C46A), fixed = Color(0xFF9CC2EC), couple = Color(0xFF7FD0EA),
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

private val LightHero = HeroColors(
    brush = Brush.linearGradient(listOf(Color(0xFF0B6E5F), Color(0xFF0F4C5C))),
    content = Color.White, muted = Color.White.copy(alpha = 0.72f), tile = Color.White.copy(alpha = 0.12f),
)
private val DarkHero = HeroColors(
    brush = Brush.linearGradient(listOf(Color(0xFF0F5146), Color(0xFF123744))),
    content = Color(0xFFE9FFF8), muted = Color(0xFFE9FFF8).copy(alpha = 0.7f), tile = Color.White.copy(alpha = 0.08f),
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
