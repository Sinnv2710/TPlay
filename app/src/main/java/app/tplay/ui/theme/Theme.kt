package app.tplay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF6D8DFF)
val BrandViolet = Color(0xFF9B6DFF)

@Composable
fun TPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BrandBlue,
            secondary = BrandViolet,
            surface = Color(0xFF171A22),
            background = Color(0xFF0F1117)
        ),
        content = content
    )
}
