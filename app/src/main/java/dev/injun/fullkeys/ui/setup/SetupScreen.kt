package dev.injun.fullkeys.ui.setup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.injun.fullkeys.R
import dev.injun.fullkeys.core.layout.KeyboardLayout
import dev.injun.fullkeys.core.layout.KeyboardLayouts
import dev.injun.fullkeys.ime.FnState
import dev.injun.fullkeys.settings.KeyboardSettings
import dev.injun.fullkeys.settings.layout
import dev.injun.fullkeys.settings.phoneLayout
import dev.injun.fullkeys.ui.keyboard.KeyboardPanel

private val SCREEN_PADDING = 16.dp

/** Lays the element out [horizontal] wider on each side, reaching past its parent's side padding. */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx()
    val width = constraints.maxWidth + 2 * extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

/** Whether Android lets this keyboard be used, and whether it is the one in use. */
data class KeyboardStatus(val enabled: Boolean, val selected: Boolean)

@Composable
fun SetupRoute(viewModel: SetupViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status = rememberKeyboardStatus()
    val context = LocalContext.current

    SetupScreen(
        settings = settings,
        status = status,
        onOpenKeyboardList = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
        onChooseKeyboard = { context.getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
        onLayout = viewModel::setLayout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    settings: KeyboardSettings,
    status: KeyboardStatus,
    onOpenKeyboardList: () -> Unit,
    onChooseKeyboard: () -> Unit,
    onLayout: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val layout = remember(settings.layoutId, locale) { settings.layout(locale) }
    val phone = remember(locale) { phoneLayout(locale) }
    var choosing by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // The screen draws edge to edge, so the keyboard covers it rather than
                // moving it: without this the field for trying the keyboard sits under it.
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SCREEN_PADDING),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section(stringResource(R.string.section_keyboard)) {
                Step(
                    action = stringResource(R.string.setup_enable_action),
                    done = stringResource(R.string.setup_enable_done).takeIf { status.enabled },
                    onAction = onOpenKeyboardList,
                )
                Step(
                    action = stringResource(R.string.setup_select_action),
                    done = stringResource(R.string.setup_select_done).takeIf { status.selected },
                    onAction = onChooseKeyboard,
                    enabled = status.enabled,
                )
            }

            Section(stringResource(R.string.layout_title)) {
                Text(
                    if (settings.layoutId == null) stringResource(R.string.layout_follow_phone_current, phone.name) else layout.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = { choosing = true }) { Text(stringResource(R.string.layout_change)) }
                // The preview shows the keyboard as it will open: this layout, these
                // settings, drawn by the same code and as wide as the screen, since key
                // widths and label sizes both follow from the width.
                KeyboardPanel(
                    layout = layout,
                    settings = settings,
                    fn = FnState(),
                    onPress = { _, _ -> },
                    onRelease = {},
                    modifier = Modifier.bleed(SCREEN_PADDING),
                    clearSystemBars = false,
                )
            }

            Section(stringResource(R.string.try_title)) {
                OutlinedTextField(
                    state = rememberTextFieldState(),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.try_hint)) },
                )
                Text(
                    stringResource(R.string.try_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (choosing) {
        LayoutChooser(
            phone = phone,
            chosenId = settings.layoutId,
            onChoose = {
                onLayout(it)
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** A step of the setup: the button to take it, or a note that it is done. */
@Composable
private fun Step(action: String, done: String?, onAction: () -> Unit, enabled: Boolean = true) {
    if (done != null) {
        Text(
            "✓ $done",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Button(onClick = onAction, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(action) }
    }
}

/**
 * Every layout in one searchable list, with following the phone at the top: the choice
 * most people want, and the one that keeps up if the phone's language changes.
 */
@Composable
private fun LayoutChooser(
    phone: KeyboardLayout,
    chosenId: String?,
    onChoose: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val query: TextFieldState = rememberTextFieldState()
    val layouts = remember { KeyboardLayouts.all.sortedBy { it.name } }
    val shown = layouts.filter { it.name.contains(query.text, ignoreCase = true) || it.id.equals(query.text.toString(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layout_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    state = query,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.layout_search)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        Choice(stringResource(R.string.layout_follow_phone_current, phone.name), chosenId == null) { onChoose(null) }
                    }
                    items(shown, key = { it.id }) { layout ->
                        Choice(layout.name, chosenId == layout.id) { onChoose(layout.id) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.layout_cancel)) } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = if (selected) "✓  $label" else label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/**
 * Read again whenever this screen comes back or regains focus: the keyboard is turned
 * on in system settings, which leaves the app, and picked in a system dialog, which
 * does not but takes focus away while it is open.
 */
@Composable
private fun rememberKeyboardStatus(): KeyboardStatus {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val focused = LocalWindowInfo.current.isWindowFocused
    var status by remember { mutableStateOf(keyboardStatusOf(context)) }

    LaunchedEffect(focused) { status = keyboardStatusOf(context) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = keyboardStatusOf(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return status
}

private fun keyboardStatusOf(context: Context): KeyboardStatus {
    val manager = context.getSystemService(InputMethodManager::class.java)
    val enabled = manager.enabledInputMethodList.any { it.packageName == context.packageName }
    val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    val selected = current?.let(ComponentName::unflattenFromString)?.packageName == context.packageName
    return KeyboardStatus(enabled, selected)
}
