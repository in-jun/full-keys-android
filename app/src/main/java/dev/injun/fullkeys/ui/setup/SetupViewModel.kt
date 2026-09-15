package dev.injun.fullkeys.ui.setup

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.injun.fullkeys.settings.KeyboardSettings
import dev.injun.fullkeys.settings.SettingsStore
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SetupViewModel @Inject constructor(private val store: SettingsStore) : ViewModel() {

    val settings: StateFlow<KeyboardSettings> = store.settings

    /** [id] null follows the phone's language and country. */
    fun setLayout(id: String?) = store.update { it.copy(layoutId = id) }
}
