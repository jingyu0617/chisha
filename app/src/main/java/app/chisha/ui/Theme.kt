@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.chisha.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 当前外观：安卓 17（Material 3 Expressive）或 苹果（Liquid Glass 风格） */
enum class Skin { ANDROID, APPLE }

val LocalSkin = staticCompositionLocalOf { Skin.ANDROID }

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

/* ---------- 苹果：iOS 系统色（浅色/深色） ---------- */
private val IosLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF), onPrimaryContainer = Color(0xFF003C8A),
    secondary = Color(0xFF8E8E93), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA), onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFFFF9500), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBCC), onTertiaryContainer = Color(0xFF7A4100),
    background = Color(0xFFF2F2F7), onBackground = Color.Black,
    surface = Color(0xFFF2F2F7), onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF8A8A8E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF2F2F7),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    outline = Color(0xFFC6C6C8), outlineVariant = Color(0xFFD1D1D6),
    error = Color(0xFFFF3B30), onError = Color.White,
    errorContainer = Color(0xFFFFE5E3), onErrorContainer = Color(0xFFB3261E),
)

private val IosDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF), onPrimary = Color.White,
    primaryContainer = Color(0xFF0A3D7A), onPrimaryContainer = Color(0xFFCFE4FF),
    secondary = Color(0xFF8E8E93), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFF9F0A), onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF3D2600), onTertiaryContainer = Color(0xFFFFD08A),
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFF98989F),
    surfaceContainerLowest = Color(0xFF1C1C1E), surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF545458), outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A), onError = Color.White,
    errorContainer = Color(0xFF3B1312), onErrorContainer = Color(0xFFFF9A93),
)

/* iOS：胶囊按钮和芯片、更圆的卡片 */
private val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(50),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private fun iosTypography(): Typography {
    val t = Typography()
    return t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = 0.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 17.sp, letterSpacing = 0.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    )
}

@Composable
fun ChishaTheme(skin: Skin, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalSkin provides skin) {
        if (skin == Skin.APPLE) {
            MaterialTheme(
                colorScheme = if (dark) IosDark else IosLight,
                shapes = IosShapes,
                typography = iosTypography(),
                content = content,
            )
        } else {
            val ctx = LocalContext.current
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
    }
}
