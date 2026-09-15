package dev.injun.fullkeys.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class KeyboardSettings(
    val portraitRowHeightDp: Float = DEFAULT_ROW_HEIGHT_DP,
    val landscapeRowHeightDp: Float = DEFAULT_ROW_HEIGHT_DP,
    val translucent: Boolean = false,
    /** Floating: the keyboard is a window of its own that can be put anywhere on the screen. */
    val floating: Boolean = false,
    /** Where a floating keyboard sits, as a share of the room it has to move in. */
    val floatX: Float = 0.5f,
    val floatY: Float = 1f,
    /** The chosen keyboard layout, or null to follow the phone's language and country. */
    val layoutId: String? = null,
) {
    companion object {
        const val DEFAULT_ROW_HEIGHT_DP = 36f

        /**
         * Below 28dp a key is shorter than it is wide in portrait and misses start to
         * land on the row above; above 48dp the keyboard covers more than half of a
         * landscape screen.
         */
        val ROW_HEIGHT_RANGE_DP = 28f..48f
    }
}

/**
 * The keyboard's settings, shared by the settings screen and the keyboard itself.
 *
 * Both run in the one process, so a single flow is enough for a change on the settings
 * screen to reach a keyboard that is already open.
 *
 * Kept in device-protected storage. After a restart the lock screen asks for the PIN
 * before the phone's ordinary storage can be read, and it asks with the keyboard in
 * use; settings stored the ordinary way would be unreadable at exactly that moment.
 * Nothing here is sensitive, which is what makes that storage the right place for it.
 */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.createDeviceProtectedStorageContext().let { protected ->
        // Settings saved before this moved are carried over once; while the phone is still
        // locked the move fails quietly and is tried again next time.
        protected.moveSharedPreferencesFrom(context, PREFS)
        protected.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val state = MutableStateFlow(load())
    val settings: StateFlow<KeyboardSettings> = state.asStateFlow()

    fun update(transform: (KeyboardSettings) -> KeyboardSettings) {
        state.update { current ->
            transform(current).let {
                it.copy(
                    portraitRowHeightDp = it.portraitRowHeightDp.coerceIn(KeyboardSettings.ROW_HEIGHT_RANGE_DP),
                    landscapeRowHeightDp = it.landscapeRowHeightDp.coerceIn(KeyboardSettings.ROW_HEIGHT_RANGE_DP),
                    floatX = it.floatX.coerceIn(0f, 1f),
                    floatY = it.floatY.coerceIn(0f, 1f),
                )
            }
        }
        save(state.value)
    }

    private fun load(): KeyboardSettings {
        val defaults = KeyboardSettings()
        return KeyboardSettings(
            portraitRowHeightDp = prefs.getFloat(K_PORTRAIT, defaults.portraitRowHeightDp)
                .coerceIn(KeyboardSettings.ROW_HEIGHT_RANGE_DP),
            landscapeRowHeightDp = prefs.getFloat(K_LANDSCAPE, defaults.landscapeRowHeightDp)
                .coerceIn(KeyboardSettings.ROW_HEIGHT_RANGE_DP),
            translucent = prefs.getBoolean(K_TRANSLUCENT, defaults.translucent),
            floating = prefs.getBoolean(K_FLOATING, defaults.floating),
            floatX = prefs.getFloat(K_FLOAT_X, defaults.floatX).coerceIn(0f, 1f),
            floatY = prefs.getFloat(K_FLOAT_Y, defaults.floatY).coerceIn(0f, 1f),
            layoutId = prefs.getString(K_LAYOUT, defaults.layoutId),
        )
    }

    private fun save(settings: KeyboardSettings) {
        prefs.edit {
            putFloat(K_PORTRAIT, settings.portraitRowHeightDp)
            putFloat(K_LANDSCAPE, settings.landscapeRowHeightDp)
            putBoolean(K_TRANSLUCENT, settings.translucent)
            putBoolean(K_FLOATING, settings.floating)
            putFloat(K_FLOAT_X, settings.floatX)
            putFloat(K_FLOAT_Y, settings.floatY)
            putString(K_LAYOUT, settings.layoutId)
        }
    }

    private companion object {
        const val PREFS = "keyboard"
        const val K_PORTRAIT = "portraitRowHeightDp"
        const val K_LANDSCAPE = "landscapeRowHeightDp"
        const val K_TRANSLUCENT = "translucent"
        const val K_FLOATING = "floating"
        const val K_FLOAT_X = "floatX"
        const val K_FLOAT_Y = "floatY"
        const val K_LAYOUT = "layoutId"
    }
}
