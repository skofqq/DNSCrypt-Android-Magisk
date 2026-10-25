package com.skofqq.shieldy.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skofqq.shieldy.data.ConfigBackup
import com.skofqq.shieldy.data.ConfigValidation
import com.skofqq.shieldy.data.ModuleRepository
import com.skofqq.shieldy.data.UiMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the config screen needs to know. */
data class ConfigUiState(
    val loading: Boolean = true,
    val text: String = "",
    val original: String = "",
    val backups: List<ConfigBackup> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    /** Bumped whenever the text is replaced from disk, so the editor can reset. */
    val loadToken: Int = 0,
) {
    val dirty: Boolean get() = text != original
}

/** Messages the screen turns into localized text. */
sealed interface ConfigEvent {
    data object Saved : ConfigEvent
    data object InvalidEmpty : ConfigEvent
    data object InvalidListen : ConfigEvent
    data object Restored : ConfigEvent
    data class BackupCreated(val name: String) : ConfigEvent
    data class Failed(val message: UiMessage) : ConfigEvent
}

class ConfigViewModel(
    private val repository: ModuleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ConfigEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ConfigEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            repository.readConfig().fold(
                onSuccess = { text ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        text = text,
                        original = text,
                        loadToken = _uiState.value.loadToken + 1,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = error.message ?: error.javaClass.simpleName,
                    )
                },
            )
            refreshBackups()
        }
    }

    fun onTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(text = text)
    }

    fun refreshBackups() {
        viewModelScope.launch {
            repository.listBackups().onSuccess { backups ->
                _uiState.value = _uiState.value.copy(backups = backups)
            }
        }
    }

    fun saveAndRestart() {
        val state = _uiState.value
        if (state.busy) return
        val validation = ConfigValidation.validate(state.text)
        if (validation.isFailure) {
            viewModelScope.launch {
                _events.emit(
                    when (validation.exceptionOrNull()?.message) {
                        "EMPTY" -> ConfigEvent.InvalidEmpty
                        else -> ConfigEvent.InvalidListen
                    }
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(busy = true)
            val written = repository.writeConfig(state.text)
            val writeError = written.exceptionOrNull()
            if (writeError != null) {
                _uiState.value = _uiState.value.copy(busy = false)
                _events.emit(ConfigEvent.Failed(UiMessage.of(writeError)))
                return@launch
            }
            _uiState.value = _uiState.value.copy(busy = false, original = state.text)
            val restart = repository.restartIfRunning()
            val failure = restart?.let(UiMessage::ofFailure)
            when {
                failure != null -> _events.emit(ConfigEvent.Failed(failure))
                restart == null -> _events.emit(ConfigEvent.Failed(UiMessage.SavedForNextStart))
                else -> _events.emit(ConfigEvent.Saved)
            }
            refreshBackups()
        }
    }

    fun createBackup() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            repository.createBackup().fold(
                onSuccess = { path ->
                    _events.emit(ConfigEvent.BackupCreated(path.substringAfterLast('/')))
                },
                onFailure = { error -> _events.emit(ConfigEvent.Failed(UiMessage.of(error))) },
            )
            _uiState.value = _uiState.value.copy(busy = false)
            refreshBackups()
        }
    }

    fun restore(backup: ConfigBackup) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            repository.restoreBackup(backup).fold(
                onSuccess = { _events.emit(ConfigEvent.Restored) },
                onFailure = { error -> _events.emit(ConfigEvent.Failed(UiMessage.of(error))) },
            )
            _uiState.value = _uiState.value.copy(busy = false)
            load()
        }
    }

    fun exportConfig() = runCtl { repository.exportConfig() }

    fun importConfig() = runCtl {
        val result = repository.importConfig()
        load()
        result
    }

    private fun runCtl(block: suspend () -> Result<com.skofqq.shieldy.root.ShellOutput>) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            val failure = UiMessage.ofFailure(block())
            _uiState.value = _uiState.value.copy(busy = false)
            if (failure != null) _events.emit(ConfigEvent.Failed(failure))
        }
    }
}
