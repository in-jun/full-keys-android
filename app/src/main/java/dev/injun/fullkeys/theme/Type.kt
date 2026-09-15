package dev.injun.fullkeys.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The default Material scale with one change: the label style the setup screen uses
 * for its section titles, which it prints in capitals. Capitals set at the default
 * spacing read as a word run together; a little more air between them is what
 * printed small caps have.
 */
val Typography = Typography().let { base ->
    base.copy(
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
    )
}
