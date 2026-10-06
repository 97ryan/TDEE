package com.ryan.tdee.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.ryan.tdee.core.parseNumber

/** Text whose characters roll like an odometer when the value changes. */
@Composable
fun RollingNumber(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val last = remember { arrayOf(text) }
    val rollingUp = remember(text) {
        (parseNumber(text) ?: 0.0) >= (parseNumber(last[0]) ?: 0.0)
    }
    SideEffect { last[0] = text }

    val tabular = style.copy(fontFeatureSettings = "tnum")
    Row(modifier) {
        text.forEachIndexed { index, char ->
            // Keyed from the right so units, tens and hundreds stay put when the length changes.
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val dir = if (rollingUp) 1 else -1
                        (slideInVertically { it * dir } + fadeIn()) togetherWith
                            (slideOutVertically { -it * dir } + fadeOut()) using SizeTransform(clip = false)
                    },
                    label = "digit",
                ) { Text(it.toString(), style = tabular) }
            }
        }
    }
}
