@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.chisha.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/* ---------- 安卓：没有动态取色时的青瓷 + 酱色 ---------- */
private val LightFallback: ColorScheme = lightColorScheme(
    primary = Color(0xFF2E6A57), onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F0D7), onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4C6358), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE9DA), onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF8E4E14), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC5), onTertiaryContainer = Color(0xFF301400),
    background = Color(0xFFF5FBF5), onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF5), onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DE), onSurfaceVariant = Color(0xFF404944),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF5EF),
    surfaceContainer = Color(0xFFE9EFE9), surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDEE4DE),
    outline = Color(0xFF707974), outlineVariant = Color(0xFFBFC9C2),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

private val DarkFallback: ColorScheme = darkColorScheme(
    primary = Color(0xFF96D4BB), onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0E513F), onPrimaryContainer = Color(0xFFB2F0D7),
    secondary = Color(0xFFB3CCBF), onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF354B41), onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFFFB783), onTertiary = Color(0xFF4F2500),
    tertiaryContainer = Color(0xFF6F3800), onTertiaryContainer = Color(0xFFFFDCC5),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404944), onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E), surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    outline = Color(0xFF89938D), outlineVariant = Color(0xFF404944),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun ChishaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    // 安卓 12 及以上：配色跟随壁纸（官方动态取色）；更老的系统用青瓷配色
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkFallback
        else -> LightFallback
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        content = content,
    )
}
