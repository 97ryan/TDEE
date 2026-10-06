package com.ryan.tdee.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Colours for the three graph series, kept consistent between the graph and its toggles. */
data class SeriesColors(val weight: Color, val calories: Color, val tdee: Color)

private val LightSeries = SeriesColors(Color(0xFF0B6BA8), Color(0xFFB8336A), Color(0xFF4C7A1E))
private val DarkSeries = SeriesColors(Color(0xFF7CCBF5), Color(0xFFFF9EC1), Color(0xFFA6D86F))

val LocalSeriesColors = staticCompositionLocalOf { DarkSeries }

val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun TdeeTheme(darkTheme: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val target = when {
        dynamicColor && supportsDynamicColor ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    CompositionLocalProvider(LocalSeriesColors provides if (darkTheme) DarkSeries else LightSeries) {
        MaterialTheme(colorScheme = target.animated(), content = content)
    }
}

/** Cross-fades the whole palette when switching theme or wallpaper colours. */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable
    fun Color.anim() = animateColorAsState(this, tween(450), label = "scheme").value
    return copy(
        primary = primary.anim(),
        onPrimary = onPrimary.anim(),
        primaryContainer = primaryContainer.anim(),
        onPrimaryContainer = onPrimaryContainer.anim(),
        secondary = secondary.anim(),
        onSecondary = onSecondary.anim(),
        secondaryContainer = secondaryContainer.anim(),
        onSecondaryContainer = onSecondaryContainer.anim(),
        tertiary = tertiary.anim(),
        onTertiary = onTertiary.anim(),
        tertiaryContainer = tertiaryContainer.anim(),
        onTertiaryContainer = onTertiaryContainer.anim(),
        background = background.anim(),
        onBackground = onBackground.anim(),
        surface = surface.anim(),
        onSurface = onSurface.anim(),
        surfaceVariant = surfaceVariant.anim(),
        onSurfaceVariant = onSurfaceVariant.anim(),
        surfaceContainerLowest = surfaceContainerLowest.anim(),
        surfaceContainerLow = surfaceContainerLow.anim(),
        surfaceContainer = surfaceContainer.anim(),
        surfaceContainerHigh = surfaceContainerHigh.anim(),
        surfaceContainerHighest = surfaceContainerHighest.anim(),
        outline = outline.anim(),
        outlineVariant = outlineVariant.anim(),
        error = error.anim(),
        errorContainer = errorContainer.anim(),
        onErrorContainer = onErrorContainer.anim(),
    )
}
