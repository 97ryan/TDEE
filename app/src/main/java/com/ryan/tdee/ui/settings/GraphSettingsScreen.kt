@file:OptIn(ExperimentalMaterial3Api::class)

package com.ryan.tdee.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.tdee.core.Settings
import com.ryan.tdee.ui.TdeeViewModel
import com.ryan.tdee.ui.theme.LocalSeriesColors
import kotlin.math.roundToInt

@Composable
fun GraphSettingsScreen(viewModel: TdeeViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val colors = LocalSeriesColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Graph settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                "Trend lines are moving averages. Higher values give smoother lines that react more slowly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            SmoothingSlider("Weight trend line smoothing", s.weightSmoothing, colors.weight) { v ->
                viewModel.updateSettings { it.copy(weightSmoothing = v) }
            }
            SmoothingSlider("Calorie trend line smoothing", s.calorieSmoothing, colors.calories) { v ->
                viewModel.updateSettings { it.copy(calorieSmoothing = v) }
            }
            SmoothingSlider("TDEE trend line smoothing", s.tdeeSmoothing, colors.tdee) { v ->
                viewModel.updateSettings { it.copy(tdeeSmoothing = v) }
            }
            TextButton(
                onClick = {
                    val d = Settings()
                    viewModel.updateSettings {
                        it.copy(weightSmoothing = d.weightSmoothing, calorieSmoothing = d.calorieSmoothing, tdeeSmoothing = d.tdeeSmoothing)
                    }
                },
            ) { Text("Reset to defaults") }
        }
    }
}

@Composable
private fun SmoothingSlider(title: String, value: Int, color: Color, onChange: (Int) -> Unit) {
    // Local state keeps dragging smooth; the setting is written when the drag ends.
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (current.roundToInt() <= 1) "Off" else "${current.roundToInt()} days",
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onChange(current.roundToInt()) },
            valueRange = 1f..60f,
            steps = 58,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
        )
    }
}
