package dev.injun.fullkeys.ui.keyboard

import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import dev.injun.fullkeys.core.layout.outline

/**
 * Two looks. Opaque keeps the caps readable over anything; see-through lets the app
 * underneath show behind the keyboard, and gives the labels a shadow so they still read
 * over whatever text is underneath.
 */
internal data class KeyPalette(
    val ground: Color,
    val character: Color,
    val modifier: Color,
    val function: Color,
    val pressed: Color,
    val accent: Color,
    val accentDim: Color,
    val text: Color,
    val secondaryText: Color,
    val secondaryScript: Color,
    val outline: Color?,
    val textShadow: Shadow?,
) {
    companion object {
        val Opaque = KeyPalette(
            ground = Color(0xFF15171C),
            character = Color(0xFF353947),
            modifier = Color(0xFF23262E),
            function = Color(0xFF1A1C22),
            pressed = Color(0xFF4A5060),
            accent = Color(0xFF3D59A1),
            accentDim = Color(0xFF2C3A5E),
            text = Color(0xFFE6E8EE),
            secondaryText = Color(0xFF9AA0AD),
            secondaryScript = Color(0xFF8C9AD0),
            outline = null,
            textShadow = null,
        )

        val Translucent = KeyPalette(
            ground = Color(0x4D0C0D11),
            character = Color(0x2CFFFFFF),
            modifier = Color(0x18FFFFFF),
            function = Color(0x0CFFFFFF),
            pressed = Color(0x66FFFFFF),
            accent = Color(0x997AA2F7),
            accentDim = Color(0x557AA2F7),
            text = Color.White,
            secondaryText = Color(0xDDFFFFFF),
            secondaryScript = Color(0xFFC9D4FF),
            outline = Color(0x38FFFFFF),
            textShadow = Shadow(Color(0xCC000000), Offset(0f, 1f), blurRadius = 3f),
        )
    }
}

/**
 * What every cap on the keyboard shares whatever it is for: the gap between caps, the
 * radius of their corners, and the width of the line round a see-through one.
 */
internal val KEY_GAP = 4.dp
internal val KEY_RADIUS = 5.dp
internal val OUTLINE_WIDTH = 0.8.dp
