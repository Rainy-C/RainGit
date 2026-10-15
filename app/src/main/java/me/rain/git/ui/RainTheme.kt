package me.rain.git.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

private val lightPalette = lightColorScheme(
    primary = Color(0xFF181818), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E8E8), onPrimaryContainer = Color(0xFF181818),
    inversePrimary = Color(0xFFE2E2E2),
    secondary = Color(0xFF555555), onSecondary = Color.White,
    secondaryContainer = Color(0xFFECECEC), onSecondaryContainer = Color(0xFF252525),
    tertiary = Color(0xFF555555), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECECEC), onTertiaryContainer = Color(0xFF252525),
    background = Color(0xFFF6F6F6), onBackground = Color(0xFF181818),
    surface = Color(0xFFF6F6F6), onSurface = Color(0xFF181818),
    surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF606060),
    surfaceTint = Color.Transparent, inverseSurface = Color(0xFF292929), inverseOnSurface = Color(0xFFF5F5F5),
    surfaceDim = Color(0xFFDEDEDE), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0F0F0), surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE3E3E3),
    outline = Color(0xFF808080), outlineVariant = Color(0xFFDDDDDD),
    error = Color(0xFF252525), onError = Color.White,
    errorContainer = Color(0xFFE4E4E4), onErrorContainer = Color(0xFF181818), scrim = Color.Black
)
private val darkPalette = darkColorScheme(
    primary = Color(0xFFF2F2F2), onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF303030), onPrimaryContainer = Color(0xFFF2F2F2),
    inversePrimary = Color(0xFF252525),
    secondary = Color(0xFFC2C2C2), onSecondary = Color(0xFF191919),
    secondaryContainer = Color(0xFF2B2B2B), onSecondaryContainer = Color(0xFFE5E5E5),
    tertiary = Color(0xFFC2C2C2), onTertiary = Color(0xFF191919),
    tertiaryContainer = Color(0xFF2B2B2B), onTertiaryContainer = Color(0xFFE5E5E5),
    background = Color(0xFF101010), onBackground = Color(0xFFECECEC),
    surface = Color(0xFF101010), onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF292929), onSurfaceVariant = Color(0xFFB3B3B3),
    surfaceTint = Color.Transparent, inverseSurface = Color(0xFFE5E5E5), inverseOnSurface = Color(0xFF202020),
    surfaceDim = Color(0xFF101010), surfaceBright = Color(0xFF363636),
    surfaceContainerLowest = Color(0xFF0A0A0A), surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF222222), surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF333333),
    outline = Color(0xFF858585), outlineVariant = Color(0xFF393939),
    error = Color(0xFFE8E8E8), onError = Color(0xFF151515),
    errorContainer = Color(0xFF363636), onErrorContainer = Color(0xFFF2F2F2), scrim = Color.Black
)

@Composable
internal fun RainTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) darkPalette else lightPalette,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content
    )
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Suppress("DEPRECATION")
@Composable
internal fun TransparentSystemBars() {
    val view = LocalView.current
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val window = (view.parent as? DialogWindowProvider)?.window ?: view.context.activity()?.window
    SideEffect {
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            it.statusBarColor = AndroidColor.TRANSPARENT
            it.navigationBarColor = AndroidColor.TRANSPARENT
            it.navigationBarDividerColor = AndroidColor.TRANSPARENT
            it.isNavigationBarContrastEnforced = false
            it.isStatusBarContrastEnforced = false
            it.attributes = it.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}

@Composable
internal fun FullScreenPanel(close: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        TransparentSystemBars()
        // Paint behind system bars; apply safe insets only to interactive content.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) { content() }
        }
    }
}
