package io.github.skofqq.dnscryptmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.skofqq.dnscryptmanager.data.AppPreferences
import io.github.skofqq.dnscryptmanager.data.AppSettings
import io.github.skofqq.dnscryptmanager.data.ModuleRepository
import io.github.skofqq.dnscryptmanager.data.ModuleSettings
import io.github.skofqq.dnscryptmanager.data.UiMessage
import io.github.skofqq.dnscryptmanager.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModuleSettingsUiState(
    val loading: Boolean = true,
    /** What module.conf says right now. */
    val saved: Map<String, String> = emptyMap(),
    /** Local edits, applied only when the user asks for it. */
    val draft: Map<String, String> = emptyMap(),
    val applying: Boolean = false,
    val error: String? = null,
) {
    val pendingKeys: Set<String>
        get() = draft.filter { (key, value) -> saved[key] != value }.keys

    fun boolean(key: String, fallback: Boolean = false): Boolean =
        draft[key]?.let { it == "true" } ?: fallback

    fun text(key: String, fallback: String): String = draft[key] ?: fallback

    fun number(key: String, fallback: Int): Int = draft[key]?.toIntOrNull() ?: fallback
}

class SettingsViewModel(
    private val repository: ModuleRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModuleSettingsUiState())
    val uiState: StateFlow<ModuleSettingsUiState> = _uiState.asStateFlow()

    val appSettings: StateFlow<AppSettings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            repository.readModuleConf().fold(
                onSuccess = { conf ->
                    _uiState.value = ModuleSettingsUiState(
                        loading = false,
                        saved = conf,
                        draft = conf,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = error.message ?: error.javaClass.simpleName,
                    )
                },
            )
        }
    }

    /** Records an edit locally. Nothing reaches the module until [applyAndRestart]. */
    fun edit(key: String, value: String) {
        val normalized = ModuleSettings.validate(key, value).getOrNull() ?: return
        _uiState.value = _uiState.value.copy(
            draft = _uiState.value.draft + (normalized.first to normalized.second)
        )
    }

    fun discardEdits() {
        _uiState.value = _uiState.value.copy(draft = _uiState.value.saved)
    }

    fun applyAndRestart(onApplied: () -> Unit) {
        val state = _uiState.value
        if (state.applying || state.pendingKeys.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = state.copy(applying = true)
            for (key in state.pendingKeys) {
                val value = state.draft.getValue(key)
                val failure = UiMessage.ofFailure(repository.setOption(key, value))
                if (failure != null) {
                    _messages.emit(failure)
                    _uiState.value = _uiState.value.copy(applying = false)
                    return@launch
                }
            }
            val failure = UiMessage.ofFailure(repository.restart())
            _uiState.value = _uiState.value.copy(applying = false)
            if (failure != null) {
                _messages.emit(failure)
            } else {
                onApplied()
            }
            reload()
        }
    }

    /** Puts every module setting back to the value the module ships with. */
    fun resetToDefaults(onDone: () -> Unit) {
        if (_uiState.value.applying) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(applying = true)
            val failure = UiMessage.ofFailure(repository.resetSettings())
                ?: UiMessage.ofFailure(repository.restart())
            _uiState.value = _uiState.value.copy(applying = false)
            if (failure != null) _messages.emit(failure) else onDone()
            reload()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }

    fun setPollInterval(seconds: Int) {
        viewModelScope.launch { preferences.setPollInterval(seconds) }
    }
}
