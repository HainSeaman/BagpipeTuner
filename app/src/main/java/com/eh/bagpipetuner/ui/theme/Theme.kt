package com.eh.bagpipetuner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Farbschema
private val BagpipeColorScheme = lightColorScheme(
    primary = BagpipeGreen,             // Hauptfarbe: Grün
    onPrimary = Color.White,             // Textfarbe auf Primärfarbe
    background = BackgroundGrey,         // App-Hintergrund
    onBackground = Color.Black,          // Textfarbe auf Hintergrund
    surface = BackgroundGrey,            // Oberflächen-Hintergrund
    onSurface = Color.Black               // Textfarbe auf Oberflächen
)

@Composable
fun BagpipeTunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BagpipeColorScheme,
        typography = MaterialTheme.typography, // bestehende Typografie
        shapes = MaterialTheme.shapes,         // bestehende Shapes
        content = content
    )
}
