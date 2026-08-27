package vn.nhip2phut.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class Nhip2PhutSpacing(
    val contentGap: Dp,
    val cardPadding: Dp,
    val screenPadding: Dp,
)

object Nhip2PhutSemanticTokens {
    val colorScheme = lightColorScheme(
        primary = Color(0xFF116A5C),
        secondary = Color(0xFF6B5E2E),
        tertiary = Color(0xFF7A4B58),
        background = Color(0xFFFBFCFA),
        surface = Color(0xFFFFFFFF),
        error = Color(0xFFBA1A1A),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = Color(0xFF1B1D1A),
        onSurface = Color(0xFF1B1D1A),
        onError = Color.White,
    )

    val typography = Typography(
        headlineMedium = TextStyle(
            color = colorScheme.onSurface,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 34.sp,
        ),
        bodyLarge = TextStyle(
            color = colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 26.sp,
        ),
    )

    val shapes = Shapes(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(20.dp),
    )

    val spacing = Nhip2PhutSpacing(
        contentGap = 12.dp,
        cardPadding = 20.dp,
        screenPadding = 24.dp,
    )
}

private val LocalNhip2PhutSpacing = staticCompositionLocalOf {
    Nhip2PhutSemanticTokens.spacing
}

object Nhip2PhutThemeTokens {
    @Composable
    @ReadOnlyComposable
    fun spacing(): Nhip2PhutSpacing = LocalNhip2PhutSpacing.current
}

@Composable
fun Nhip2PhutTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNhip2PhutSpacing provides Nhip2PhutSemanticTokens.spacing) {
        MaterialTheme(
            colorScheme = Nhip2PhutSemanticTokens.colorScheme,
            typography = Nhip2PhutSemanticTokens.typography,
            shapes = Nhip2PhutSemanticTokens.shapes,
            content = content,
        )
    }
}

