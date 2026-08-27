package vn.nhip2phut.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = lightColorScheme(
    primary = Color(0xFF116A5C),
    secondary = Color(0xFF6B5E2E),
    tertiary = Color(0xFF7A4B58),
    background = Color(0xFFFBFCFA),
    surface = Color(0xFFFBFCFA),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1B1D1A),
    onSurface = Color(0xFF1B1D1A),
)

@Composable
fun Nhip2PhutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}

