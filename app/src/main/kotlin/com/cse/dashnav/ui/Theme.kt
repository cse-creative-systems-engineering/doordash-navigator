package com.cse.dashnav.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Distinct, colorblind-friendly colors for telling dashes apart on the map. */
val DashColors = listOf(
    Color(0xFF1F77B4),
    Color(0xFFD55E00),
    Color(0xFF009E73),
    Color(0xFFCC79A7),
    Color(0xFFE69F00),
    Color(0xFF56B4E9),
)

fun dashColor(index: Int) = DashColors[index % DashColors.size]

@Composable
fun DashNavTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
