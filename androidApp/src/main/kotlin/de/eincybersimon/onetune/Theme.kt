package de.eincybersimon.onetune

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Android 12+ follows the wallpaper; these teal schemes only cover older devices.
private val LightFallback = lightColorScheme(
    primary = Color(0xFF00696F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EF0F6),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8EA),
    onSecondaryContainer = Color(0xFF051F21),
    background = Color(0xFFF4FAFA),
    onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FAFA),
    onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4949),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F5),
    surfaceContainer = Color(0xFFE9EFEF),
    surfaceContainerHigh = Color(0xFFE3E9E9),
    surfaceContainerHighest = Color(0xFFDDE4E4),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC8C9),
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFF82D3DA),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F54),
    onPrimaryContainer = Color(0xFF9EF0F6),
    secondary = Color(0xFFB0CCCE),
    onSecondary = Color(0xFF1B3436),
    secondaryContainer = Color(0xFF324B4D),
    onSecondaryContainer = Color(0xFFCCE8EA),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDDE4E4),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDDE4E4),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC8C9),
    surfaceContainerLowest = Color(0xFF090F10),
    surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF303637),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4949),
)

internal val CardBorder: BorderStroke
    @Composable get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

// Cards sit on the page/sheet tone: brighter than it in light mode, raised above it in dark mode.
internal val CardContainer: Color
    @Composable get() = with(MaterialTheme.colorScheme) { if (isSystemInDarkTheme()) surfaceContainerHigh else surfaceContainerLowest }

@Composable
fun OnetuneTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkFallback
        else -> LightFallback
    }
    MaterialTheme(colorScheme = colors, content = content)
}
