package com.memora.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.memora.app.prefs.FontChoice
import com.memora.app.prefs.PaletteChoice
import com.memora.app.prefs.ThemeMode
import com.memora.app.prefs.UserSettings

data class MemoraPalette(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val accent: Color,
    val ink: Color,
    val muted: Color
)

fun fontFamily(choice: FontChoice): FontFamily = when (choice) {
    FontChoice.SANS -> FontFamily.SansSerif
    FontChoice.SERIF -> FontFamily.Serif
    FontChoice.MONO -> FontFamily.Monospace
    FontChoice.CURSIVE -> FontFamily.Cursive
}

fun palette(choice: PaletteChoice, dark: Boolean): MemoraPalette {
    if (dark) {
        return when (choice) {
            PaletteChoice.PAPER -> MemoraPalette(
                Color(0xFF171819), Color(0xFF202223), Color(0xFF292C2D),
                Color(0xFFAABFC4), Color(0xFFF1EEE8), Color(0xFFAAA9A5)
            )
            PaletteChoice.IVORY -> MemoraPalette(
                Color(0xFF181714), Color(0xFF22211D), Color(0xFF2B2924),
                Color(0xFFC4B99E), Color(0xFFF4F0E6), Color(0xFFAAA59A)
            )
            PaletteChoice.MIST -> MemoraPalette(
                Color(0xFF15191A), Color(0xFF1E2425), Color(0xFF293031),
                Color(0xFFA7BEC2), Color(0xFFEAF0F0), Color(0xFFA2AEB0)
            )
            PaletteChoice.SAGE -> MemoraPalette(
                Color(0xFF151A18), Color(0xFF1F2623), Color(0xFF29312E),
                Color(0xFFA7BDAF), Color(0xFFEDF1EC), Color(0xFFA3ADA6)
            )
            PaletteChoice.SLATE -> MemoraPalette(
                Color(0xFF121719), Color(0xFF1A2225), Color(0xFF243034),
                Color(0xFF9DBBC4), Color(0xFFE9F0F2), Color(0xFF9EACB1)
            )
        }
    }
    return when (choice) {
        PaletteChoice.PAPER -> MemoraPalette(
            Color(0xFFF7F0E6), Color(0xFFFFFBF5), Color(0xFFF1E8DC),
            Color(0xFF506A72), Color(0xFF252525), Color(0xFF77736E)
        )
        PaletteChoice.IVORY -> MemoraPalette(
            Color(0xFFFBF7EE), Color(0xFFFFFCF5), Color(0xFFF2ECDD),
            Color(0xFF6A6758), Color(0xFF282722), Color(0xFF77746B)
        )
        PaletteChoice.MIST -> MemoraPalette(
            Color(0xFFF1F5F4), Color(0xFFFAFCFB), Color(0xFFE6ECEB),
            Color(0xFF557278), Color(0xFF202627), Color(0xFF687579)
        )
        PaletteChoice.SAGE -> MemoraPalette(
            Color(0xFFF1F4EE), Color(0xFFFAFCF8), Color(0xFFE5EBE2),
            Color(0xFF5E7768), Color(0xFF222823), Color(0xFF6C766E)
        )
        PaletteChoice.SLATE -> MemoraPalette(
            Color(0xFFF0F4F5), Color(0xFFFBFCFD), Color(0xFFE3EAEC),
            Color(0xFF465F69), Color(0xFF20282B), Color(0xFF68777C)
        )
    }
}

@Composable
fun MemoraTheme(
    settings: UserSettings,
    content: @Composable (MemoraPalette, FontFamily) -> Unit
) {
    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val p = palette(settings.palette, dark)
    // Every Material 3 tonal surface is derived from Memora's selected palette.
    // Unspecified Material defaults previously introduced pink/purple backgrounds in dialogs.
    val onAccent = if (dark) p.background else Color.White
    val common = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = onAccent,
            primaryContainer = p.surfaceAlt, onPrimaryContainer = p.ink,
            secondary = p.accent, onSecondary = p.background,
            secondaryContainer = p.surfaceAlt, onSecondaryContainer = p.ink,
            tertiary = p.accent, onTertiary = p.background,
            tertiaryContainer = p.surfaceAlt, onTertiaryContainer = p.ink,
            background = p.background, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink,
            surfaceVariant = p.surfaceAlt, onSurfaceVariant = p.muted,
            outline = p.muted.copy(alpha = .70f),
            outlineVariant = p.muted.copy(alpha = .28f),
            surfaceTint = p.accent,
            surfaceDim = p.background, surfaceBright = p.surfaceAlt,
            surfaceContainerLowest = p.background,
            surfaceContainerLow = p.surface,
            surfaceContainer = p.surface,
            surfaceContainerHigh = p.surfaceAlt,
            surfaceContainerHighest = p.surfaceAlt,
            inverseSurface = p.ink, inverseOnSurface = p.background,
            inversePrimary = p.accent, scrim = Color.Black
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = onAccent,
            primaryContainer = p.surfaceAlt, onPrimaryContainer = p.ink,
            secondary = p.accent, onSecondary = Color.White,
            secondaryContainer = p.surfaceAlt, onSecondaryContainer = p.ink,
            tertiary = p.accent, onTertiary = Color.White,
            tertiaryContainer = p.surfaceAlt, onTertiaryContainer = p.ink,
            background = p.background, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink,
            surfaceVariant = p.surfaceAlt, onSurfaceVariant = p.muted,
            outline = p.muted.copy(alpha = .70f),
            outlineVariant = p.muted.copy(alpha = .28f),
            surfaceTint = p.accent,
            surfaceDim = p.surfaceAlt, surfaceBright = p.surface,
            surfaceContainerLowest = p.background,
            surfaceContainerLow = p.surface,
            surfaceContainer = p.surface,
            surfaceContainerHigh = p.surfaceAlt,
            surfaceContainerHighest = p.surfaceAlt,
            inverseSurface = p.ink, inverseOnSurface = p.background,
            inversePrimary = p.accent, scrim = Color.Black
        )
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(26.dp)
    )
    val uiFamily = fontFamily(settings.uiFont)
    // Define every text style used by the app so screens never fall back to the platform font.
    // This is important when the user changes the UI font in Theme.
    val typography = Typography(
        displayLarge = TextStyle(fontFamily = uiFamily, fontSize = 44.sp),
        displayMedium = TextStyle(fontFamily = uiFamily, fontSize = 38.sp),
        displaySmall = TextStyle(fontFamily = uiFamily, fontSize = 34.sp),
        headlineLarge = TextStyle(fontFamily = uiFamily, fontSize = 32.sp),
        headlineMedium = TextStyle(fontFamily = uiFamily, fontSize = 27.sp),
        headlineSmall = TextStyle(fontFamily = uiFamily, fontSize = 24.sp),
        titleLarge = TextStyle(fontFamily = uiFamily, fontSize = 22.sp),
        titleMedium = TextStyle(fontFamily = uiFamily, fontSize = 17.sp),
        titleSmall = TextStyle(fontFamily = uiFamily, fontSize = 15.sp),
        bodyLarge = TextStyle(fontFamily = uiFamily, fontSize = 17.sp, lineHeight = 25.sp),
        bodyMedium = TextStyle(fontFamily = uiFamily, fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = TextStyle(fontFamily = uiFamily, fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = TextStyle(fontFamily = uiFamily, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = uiFamily, fontSize = 12.sp),
        labelSmall = TextStyle(fontFamily = uiFamily, fontSize = 11.sp)
    )
    MaterialTheme(
        colorScheme = common,
        shapes = shapes,
        typography = typography
    ) {
        content(p, fontFamily(settings.writingFont))
    }
}
