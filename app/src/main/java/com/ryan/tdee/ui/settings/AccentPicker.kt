package com.ryan.tdee.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryan.tdee.core.AccentColor

/**
 * The row of theme colours under the Theme setting. "Default" follows the wallpaper (or Material's
 * baseline colours on Android versions without wallpaper colours); the rest seed a Material scheme.
 */
@Composable
fun AccentPicker(dynamicSelected: Boolean, accent: AccentColor, onDefault: () -> Unit, onAccent: (AccentColor) -> Unit) {
    val options: List<AccentColor?> = listOf(null) + AccentColor.entries
    LazyRow(
        modifier = Modifier.testTag("accent-picker"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(options, key = { it?.name ?: "DEFAULT" }) { option ->
            val selected = if (option == null) dynamicSelected else !dynamicSelected && option == accent
            Swatch(
                label = option?.label ?: "Default",
                fill = if (option == null) DefaultFill else SolidColor(Color(option.argb)),
                selected = selected,
                onClick = { if (option == null) onDefault() else onAccent(option) },
            )
        }
    }
}

private val DefaultFill = Brush.sweepGradient(
    listOf(
        Color(0xFFE88BB7), Color(0xFF8E97FF), Color(0xFF6FD3D0), Color(0xFF9BE08A),
        Color(0xFFF2C46B), Color(0xFFE88BB7),
    )
)

@Composable
private fun Swatch(label: String, fill: Brush, selected: Boolean, onClick: () -> Unit) {
    // A zero-width border still draws a hairline, so the ring fades out instead of shrinking away.
    val ring by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "ring",
    )
    val scale by animateFloatAsState(if (selected) 0.86f else 1f, label = "scale")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .border(3.dp, ring, CircleShape),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(fill)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
