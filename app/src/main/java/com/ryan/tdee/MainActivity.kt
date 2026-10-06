package com.ryan.tdee

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.tdee.core.ThemeMode
import com.ryan.tdee.ui.TdeeNavHost
import com.ryan.tdee.ui.TdeeViewModel
import com.ryan.tdee.ui.theme.TdeeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TdeeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> systemDark
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            TdeeTheme(darkTheme = dark, dynamicColor = settings?.dynamicColor ?: true) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    // Wait for settings so the first frame already has the right units and theme.
                    if (settings != null) TdeeNavHost(viewModel)
                }
            }
        }
    }
}
