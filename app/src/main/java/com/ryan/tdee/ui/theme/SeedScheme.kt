package com.ryan.tdee.ui.theme

import android.annotation.SuppressLint
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.TonalPalette
import com.ryan.tdee.core.AccentColor

/**
 * A Material 3 colour scheme generated from a seed colour, the same way Android builds one from
 * the wallpaper ("tonal spot"): five tonal palettes, with each colour role taken at its standard
 * tone. [AccentColor.WHITE] uses Material's monochrome scheme instead.
 */
// The colour utilities are public classes in the Material library, marked for internal use.
@SuppressLint("RestrictedApi")
fun seedColorScheme(accent: AccentColor, dark: Boolean): ColorScheme {
    val mono = accent == AccentColor.WHITE
    val hue = Hct.fromInt(accent.argb).hue
    val primary = TonalPalette.fromHueAndChroma(hue, if (mono) 0.0 else 36.0)
    val secondary = TonalPalette.fromHueAndChroma(hue, if (mono) 0.0 else 16.0)
    val tertiary = TonalPalette.fromHueAndChroma((hue + 60.0) % 360.0, if (mono) 0.0 else 24.0)
    val neutral = TonalPalette.fromHueAndChroma(hue, if (mono) 0.0 else 6.0)
    val neutralVariant = TonalPalette.fromHueAndChroma(hue, if (mono) 0.0 else 8.0)
    val error = TonalPalette.fromHueAndChroma(25.0, 84.0)

    fun TonalPalette.at(level: Int) = Color(tone(level))
    fun pick(darkTone: Int, lightTone: Int) = if (dark) darkTone else lightTone

    // Monochrome swaps the accent roles for black/white extremes; everything else is shared.
    val p = if (mono) intArrayOf(pick(100, 0), pick(10, 90), pick(85, 25), pick(0, 100))
    else intArrayOf(pick(80, 40), pick(20, 100), pick(30, 90), pick(90, 10))
    val s = if (mono) intArrayOf(pick(80, 30), pick(10, 100), pick(30, 85), pick(90, 10))
    else intArrayOf(pick(80, 40), pick(20, 100), pick(30, 90), pick(90, 10))
    val t = if (mono) intArrayOf(pick(90, 25), pick(10, 90), pick(60, 49), pick(0, 100))
    else intArrayOf(pick(80, 40), pick(20, 100), pick(30, 90), pick(90, 10))

    return if (dark) {
        darkColorScheme(
            primary = primary.at(p[0]), onPrimary = primary.at(p[1]),
            primaryContainer = primary.at(p[2]), onPrimaryContainer = primary.at(p[3]),
            inversePrimary = primary.at(40),
            secondary = secondary.at(s[0]), onSecondary = secondary.at(s[1]),
            secondaryContainer = secondary.at(s[2]), onSecondaryContainer = secondary.at(s[3]),
            tertiary = tertiary.at(t[0]), onTertiary = tertiary.at(t[1]),
            tertiaryContainer = tertiary.at(t[2]), onTertiaryContainer = tertiary.at(t[3]),
            background = neutral.at(6), onBackground = neutral.at(90),
            surface = neutral.at(6), onSurface = neutral.at(90),
            surfaceVariant = neutralVariant.at(30), onSurfaceVariant = neutralVariant.at(80),
            surfaceTint = primary.at(p[0]),
            inverseSurface = neutral.at(90), inverseOnSurface = neutral.at(20),
            error = error.at(80), onError = error.at(20),
            errorContainer = error.at(30), onErrorContainer = error.at(90),
            outline = neutralVariant.at(60), outlineVariant = neutralVariant.at(30),
            scrim = neutral.at(0),
            surfaceBright = neutral.at(24), surfaceDim = neutral.at(6),
            surfaceContainerLowest = neutral.at(4), surfaceContainerLow = neutral.at(10),
            surfaceContainer = neutral.at(12), surfaceContainerHigh = neutral.at(17),
            surfaceContainerHighest = neutral.at(22),
        )
    } else {
        lightColorScheme(
            primary = primary.at(p[0]), onPrimary = primary.at(p[1]),
            primaryContainer = primary.at(p[2]), onPrimaryContainer = primary.at(p[3]),
            inversePrimary = primary.at(80),
            secondary = secondary.at(s[0]), onSecondary = secondary.at(s[1]),
            secondaryContainer = secondary.at(s[2]), onSecondaryContainer = secondary.at(s[3]),
            tertiary = tertiary.at(t[0]), onTertiary = tertiary.at(t[1]),
            tertiaryContainer = tertiary.at(t[2]), onTertiaryContainer = tertiary.at(t[3]),
            background = neutral.at(98), onBackground = neutral.at(10),
            surface = neutral.at(98), onSurface = neutral.at(10),
            surfaceVariant = neutralVariant.at(90), onSurfaceVariant = neutralVariant.at(30),
            surfaceTint = primary.at(p[0]),
            inverseSurface = neutral.at(20), inverseOnSurface = neutral.at(95),
            error = error.at(40), onError = error.at(100),
            errorContainer = error.at(90), onErrorContainer = error.at(10),
            outline = neutralVariant.at(50), outlineVariant = neutralVariant.at(80),
            scrim = neutral.at(0),
            surfaceBright = neutral.at(98), surfaceDim = neutral.at(87),
            surfaceContainerLowest = neutral.at(100), surfaceContainerLow = neutral.at(96),
            surfaceContainer = neutral.at(94), surfaceContainerHigh = neutral.at(92),
            surfaceContainerHighest = neutral.at(90),
        )
    }
}
