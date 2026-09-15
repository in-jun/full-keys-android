package dev.injun.fullkeys.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The app keeps its own colours rather than following the wallpaper.
 *
 * The keyboard is drawn in one fixed palette whatever the wallpaper is, and the setup
 * screen shows that keyboard. Dynamic colour would put a lilac screen around a
 * blue-grey keyboard on one phone and a green one on the next, and the preview would
 * look like it belonged to a different app from the frame around it.
 */
private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Neutral99,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Blue90,
    onSurfaceVariant = Neutral40,
    outline = Neutral60,
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue10,
    primaryContainer = Blue40,
    onPrimaryContainer = Blue90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral60,
    outline = Neutral40,
)

@Composable
fun FullKeysTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
