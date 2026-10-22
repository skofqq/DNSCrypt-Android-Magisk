package io.github.skofqq.dnscryptmanager.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.skofqq.dnscryptmanager.data.AppPreferences
import io.github.skofqq.dnscryptmanager.data.AppSettings
import io.github.skofqq.dnscryptmanager.data.ModuleContract
import io.github.skofqq.dnscryptmanager.data.ModuleRepository
import io.github.skofqq.dnscryptmanager.data.ModuleState
import io.github.skofqq.dnscryptmanager.data.UiMessage
import io.github.skofqq.dnscryptmanager.dns.DnsProbe
import io.github.skofqq.dnscryptmanager.dns.ProbeResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ProxyAction { START, STOP, RESTART }

data class StatusUiState(
    val state: ModuleState = ModuleState.Loading,
    val refreshing: Boolean = false,
    val busyAction: ProxyAction? = null,
    val probing: Boolean = false,
    val probe: ProbeResult? = null,
    /** null while unknown or when the module is too old to answer `conflicts`. */
    val boxDetected: Boolean? = null,
)

class StatusViewModel(
    private val repository: ModuleRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    /** Fires once an action really went through, so the screen can buzz. */
    private val _completions = MutableSharedFlow<ProxyAction>(extraBufferCapacity = 4)
    val completions: SharedFlow<ProxyAction> = _completions

    private var pollJob: Job? = null

    /** `conflicts` is not cheap, so it runs on open and on manual refresh only. */
    private var conflictsChecked = false

    init {
        refresh(showSpinner = true)
    }

    fun refresh(showSpinner: Boolean = false) {
        viewModelScope.launch {
            if (showSpinner) _uiState.update { it.copy(refreshing = true) }
            if (showSpinner) conflictsChecked = false
            val state = repository.loadState()
            _uiState.update { it.copy(state = state, refreshing = false) }
            checkConflicts(state)
        }
    }

    /**
     * Looks for one marker line in `dnscrypt-ctl conflicts`. The rest of that output is
     * shown verbatim on the conflicts tab and never parsed.
     */
    private suspend fun checkConflicts(state: ModuleState) {
        val ready = state as? ModuleState.Ready ?: return
        if (!ready.status.supportsConflicts) {
            _uiState.update { it.copy(boxDetected = null) }
            return
        }
        if (conflictsChecked) return
        conflictsChecked = true
        val output = repository.conflicts().getOrNull()?.takeIf { it.isSuccess }
        val detected = output?.let {
            it.out.contains(ModuleContract.BOX_MARKER) || it.err.contains(ModuleContract.BOX_MARKER)
        }
        _uiState.update { it.copy(boxDetected = detected) }
    }

    /** Polls while the status screen is on screen; cancelled as soon as it is not. */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            val interval = runCatching { preferences.settings.first().pollIntervalSeconds }
                .getOrDefault(AppSettings.DEFAULT_POLL_SECONDS)
            while (isActive) {
                delay(interval * 1000L)
                if (_uiState.value.busyAction == null) {
                    val state = repository.loadState()
                    _uiState.update { it.copy(state = state) }
                }
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun run(action: ProxyAction) {
        if (_uiState.value.busyAction != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(busyAction = action) }
            val result = when (action) {
                ProxyAction.START -> repository.start()
                ProxyAction.STOP -> repository.stop()
                ProxyAction.RESTART -> repository.restart()
            }
            val failure = UiMessage.ofFailure(result)
            if (failure != null) _messages.emit(failure) else _completions.emit(action)
            val state = repository.loadState()
            _uiState.update { it.copy(state = state, busyAction = null) }
        }
    }

    /** Runs the module self-test and shows whatever it printed. */
    fun runModuleTest() {
        if (_uiState.value.busyAction != null) return
        viewModelScope.launch {
            val result = repository.test()
            val failure = UiMessage.ofFailure(result)
            _messages.emit(
                failure ?: UiMessage.Raw(
                    result.getOrNull()?.out?.trim().orEmpty().ifBlank { "ok" }
                )
            )
        }
    }

    fun probe() {
        val ready = _uiState.value.state as? ModuleState.Ready ?: return
        if (_uiState.value.probing) return
        viewModelScope.launch {
            _uiState.update { it.copy(probing = true, probe = null) }
            val result = DnsProbe.probe(ready.status.port)
            _uiState.update { it.copy(probing = false, probe = result) }
        }
    }

    fun retryRoot() {
        viewModelScope.launch {
            _uiState.update { it.copy(state = ModuleState.Loading) }
            val state = repository.loadState()
            _uiState.update { it.copy(state = state) }
        }
    }

    override fun onCleared() {
        stopPolling()
    }
}
