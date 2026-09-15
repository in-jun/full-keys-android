package dev.injun.fullkeys.ime

import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.InputMethodService.Insets
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import dev.injun.fullkeys.core.KeyId
import dev.injun.fullkeys.core.input.Action
import dev.injun.fullkeys.core.input.KeyboardEngine
import dev.injun.fullkeys.core.input.Latch
import dev.injun.fullkeys.core.input.Stroke
import dev.injun.fullkeys.core.layout.KeyCap
import dev.injun.fullkeys.settings.SettingsStore
import dev.injun.fullkeys.settings.layout
import dev.injun.fullkeys.ui.keyboard.KeyboardPanel
import dev.injun.fullkeys.ui.keyboard.Resize
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the keyboard shows beyond its caps: whether Fn is on, and how. */
data class FnState(val latch: Latch = Latch.OFF, val active: Boolean = false)

/**
 * The input method: draws the keyboard and sends every touch as a key press or release.
 *
 * Nothing is committed as text. The focused app gets the key events a hardware keyboard
 * would produce, and its own key handling decides what is typed.
 */
@AndroidEntryPoint
class FullKeysService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    @Inject lateinit var settingsStore: SettingsStore

    // An input method is not an activity, so it has to be the lifecycle and
    // saved-state owner its Compose view looks for up the view tree.
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val engine = KeyboardEngine()
    private val downTimes = HashMap<KeyId, Long>()
    private val repeats = HashMap<Long, Job>()

    private val fn = MutableStateFlow(FnState())
    val fnState: StateFlow<FnState> = fn.asStateFlow()

    /**
     * Whether the keyboard is being resized. It is resized on the keyboard itself, where
     * the height it is given can be seen against the screen it leaves; Fn held on its own
     * starts it, since Fn is the one key that never reaches the app.
     */
    private val resizing = MutableStateFlow(false)

    /** Where the keyboard was drawn, in the window: what the app underneath has to go round. */
    private var board: IntRect? = null

    /**
     * The room the app underneath was given when the keyboard started being resized. It keeps
     * that room until the resizing is done: an app laid out again on every frame of a drag
     * stutters, and the keyboard growing over it for a moment is what a resize looks like.
     */
    private var roomWhileResizing: Int? = null
    private var room: Int = 0
    private var fnHold: Job? = null
    private val resize = Resize(
        onRowHeight = { dp: Float ->
            val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            settingsStore.update { if (landscape) it.copy(landscapeRowHeightDp = dp) else it.copy(portraitRowHeightDp = dp) }
        },
        onTranslucent = { translucent -> settingsStore.update { it.copy(translucent = translucent) } },
        onFloating = { floating -> settingsStore.update { it.copy(floating = floating) } },
        onDone = {
            resizing.value = false
            roomWhileResizing = null
        },
    )

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        window.window?.let { dialogWindow ->
            // The keyboard pads itself for the navigation bar and cutouts. Left to the
            // window, that padding differs between Android versions and manufacturers.
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
            // Let the keyboard's background reach under a camera cutout at the side of a
            // landscape screen; the keys themselves still keep clear of it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dialogWindow.attributes = dialogWindow.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        window.window?.let { dialogWindow ->
            dialogWindow.decorView.setViewTreeLifecycleOwner(this)
            dialogWindow.decorView.setViewTreeSavedStateRegistryOwner(this)
            // Transparent so that see-through keys show what is behind the keyboard
            // rather than the window's own background.
            dialogWindow.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }
        return ComposeView(this).apply {
            setContent {
                val settings by settingsStore.settings.collectAsStateWithLifecycle()
                val fnState by fnState.collectAsStateWithLifecycle()
                val resizing by resizing.collectAsStateWithLifecycle()
                val locale = LocalConfiguration.current.locales[0]
                val layout = remember(settings.layoutId, locale) { settings.layout(locale) }
                // A keyboard's rows do not mirror under a right-to-left system language,
                // any more than a hardware keyboard's do.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    KeyboardPanel(
                        layout = layout,
                        settings = settings,
                        fn = fnState,
                        onPress = ::onPress,
                        onRelease = ::onRelease,
                        resize = resize.takeIf { resizing },
                        onMove = { x, y -> settingsStore.update { it.copy(floatX = x, floatY = y) } },
                        // Read again on the next pass over the window, which is when the
                        // insets that keep the app clear of the keyboard are worked out.
                        onBounds = { board = it },
                    )
                }
            }
        }
    }

    /**
     * A floating keyboard is a board lying on top of the app rather than one the app makes
     * room for: the app keeps the whole screen, and every touch outside the board reaches it.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        room = outInsets.contentTopInsets
        if (settingsStore.settings.value.floating) {
            val bounds = board ?: return
            val height = window?.window?.decorView?.height ?: return
            outInsets.contentTopInsets = height
            outInsets.visibleTopInsets = height
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(bounds.left, bounds.top, bounds.right, bounds.bottom)
            return
        }
        roomWhileResizing?.let {
            outInsets.contentTopInsets = it
            outInsets.visibleTopInsets = it
        }
    }

    /**
     * Never the fullscreen editor Android defaults to in landscape. An app that reads raw
     * key events has no text field to mirror, and the editor would cover the very screen
     * the keys are typed into.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onWindowHidden() {
        releaseEverything()
        resizing.value = false
        roomWhileResizing = null
        super.onWindowHidden()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    // Focus can move to another field, or another app, while a finger is still on a key.
    // Its release would then arrive somewhere that never saw the press, and the field
    // that did would keep the key held.
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        releaseEverything()
        super.onStartInput(attribute, restarting)
    }

    override fun onFinishInput() {
        releaseEverything()
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        releaseEverything()
        resizing.value = false
        roomWhileResizing = null
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun onPress(pointer: Long, key: KeyCap) {
        fnHold?.cancel()
        val strokes = engine.press(pointer, key.id)
        send(strokes)
        strokes.singleOrNull { it.action == Action.DOWN && it.key.repeats }?.let { repeat(pointer, it) }
        if (key.id == KeyId.FN) {
            fnHold = lifecycleScope.launch {
                delay(ViewConfiguration.getLongPressTimeout().toLong())
                if (engine.withdrawFnHold(pointer)) {
                    roomWhileResizing = room
                    resizing.value = true
                    // The hold has been taken back, so Fn is no longer down on the keyboard.
                    publishFn()
                }
            }
        }
        publishFn()
    }

    private fun onRelease(pointer: Long) {
        fnHold?.cancel()
        repeats.remove(pointer)?.cancel()
        send(engine.release(pointer))
        publishFn()
    }

    /**
     * Repeats a held key with the system's own key-repeat timing, as a hardware keyboard's
     * events do, so holding Backspace or an arrow works in an ordinary text field. An app
     * that repeats held keys itself ignores events that carry a repeat count, as it does
     * for a hardware keyboard, so the same events serve both.
     */
    private fun repeat(pointer: Long, press: Stroke) {
        repeats.remove(pointer)?.cancel()
        repeats[pointer] = lifecycleScope.launch {
            delay(ViewConfiguration.getKeyRepeatTimeout().toLong())
            var count = 1
            while (true) {
                val downTime = downTimes[press.key] ?: return@launch
                currentInputConnection?.sendKeyEvent(keyEventOf(press, downTime, SystemClock.uptimeMillis(), count++))
                delay(ViewConfiguration.getKeyRepeatDelay().toLong())
            }
        }
    }

    private fun releaseEverything() {
        fnHold?.cancel()
        repeats.values.forEach { it.cancel() }
        repeats.clear()
        send(engine.releaseAll())
        publishFn()
    }

    private fun publishFn() {
        fn.value = FnState(latch = engine.fnLatch, active = engine.fnActive)
    }

    private fun send(strokes: List<Stroke>) {
        if (strokes.isEmpty()) return
        val connection = currentInputConnection
        for (stroke in strokes) {
            val now = SystemClock.uptimeMillis()
            val downTime = when (stroke.action) {
                Action.DOWN -> now.also { downTimes[stroke.key] = it }
                Action.UP -> downTimes.remove(stroke.key) ?: now
            }
            connection?.sendKeyEvent(keyEventOf(stroke, downTime, now))
        }
    }
}
