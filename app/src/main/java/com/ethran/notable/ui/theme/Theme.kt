package com.ethran.notable.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.ethran.notable.data.datastore.GlobalAppSettings

/** Paper and ink only: the app never draws a dark theme (see docs/DESIGN.md). */
private val InkColors = lightColors(
    primary = Ink.ink,
    primaryVariant = Ink.ink,
    secondary = Ink.ink,
    secondaryVariant = Ink.ink,
    background = Ink.paper,
    surface = Ink.paper,
    error = Ink.ink,
    onPrimary = Ink.paper,
    onSecondary = Ink.paper,
    onBackground = Ink.ink,
    onSurface = Ink.ink,
    onError = Ink.paper,
)

@Composable
fun InkaTheme(
    scaleFactor: Float = GlobalAppSettings.current.uiScale,
    content: @Composable () -> Unit
) {

    CompositionLocalProvider(LocalDensity provides scaledDensity(scaleFactor)) {
        MaterialTheme(
            colors = InkColors,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}

/**
 * Device density multiplied by [scale]. Starts from the configuration, not LocalDensity.current,
 * so a nested InkaTheme does not scale twice. fontScale is left as is: sp already follow density.
 */
@Composable
private fun scaledDensity(scale: Float): Density {
    val configuration = LocalConfiguration.current
    return Density(configuration.densityDpi / 160f * scale, configuration.fontScale)
}
