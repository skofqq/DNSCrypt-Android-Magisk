package io.github.skofqq.dnscryptmanager.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.skofqq.dnscryptmanager.data.AppPreferences
import io.github.skofqq.dnscryptmanager.data.ActiveServer
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
import io.github.skofqq.dnscryptmanager.data.LiveServers
import io.github.skofqq.dnscryptmanager.AppStartGate
import kotlin.random.Random

enum class ProxyAction { START, STOP, RESTART }

data class StatusUiState(
    val state: ModuleState = ModuleState.Loading,
    val refreshing: Boolean = false,
    val busyAction: ProxyAction? = null,
    val probing: Boolean = false,
    val probe: ProbeResult? = null,
    /** null while unknown or when the module is too old to answer `conflicts`. */
    val boxDetected: Boolean? = null,
    /** The resolver dnscrypt-proxy settled on, from its log; null until it has said. */
    val activeServer: ActiveServer? = null,
    /** The servers the proxy balances across, for the server card. */
    val live: LiveServers? = null,
    /** Local resolution times of uncached names, newest last, for the sparkline. */
    val rttHistory: List<Int> = emptyList(),
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
            restartPending = false
            AppStartGate.markReady()
            checkConflicts(state)
            loadServers(state)
        }
    }

    /** One poll saw the supervisor without the proxy; a real restart lasts longer than that. */
    private var restartPending = false

    /**
     * A restart is a moment with the supervisor up and the proxy down. A single poll that
     * lands in it would flash "restarting" on a healthy proxy, so it has to be seen twice.
     */
    private fun applyPolled(state: ModuleState) {
        val restarting = (state as? ModuleState.Ready)?.status?.isRestarting == true
        val wasRunning = (_uiState.value.state as? ModuleState.Ready)?.status?.running == true
        if (restarting && wasRunning && !restartPending) {
            restartPending = true
            return
        }
        restartPending = false
        _uiState.update { it.copy(state = state) }
    }

    /** The active server and the live list; servers support arrived in 2.1.18-r2. */
    private suspend fun loadServers(state: ModuleState) {
        val ready = state as? ModuleState.Ready ?: return
        if (!ready.status.supportsServers || !ready.status.running) {
            _uiState.update { it.copy(activeServer = null, live = null, rttHistory = emptyList()) }
            return
        }
        val line = repository.serversActive().getOrNull()?.takeIf { it.isSuccess }?.out?.trim()
        val live = repository.serversLive().getOrNull()?.takeIf { it.isSuccess }?.out?.let(LiveServers::parseJson)
        _uiState.update { it.copy(activeServer = line?.let(ActiveServer::parse), live = live ?: it.live) }
    }

    private var lastRttSample = 0L

    /**
     * Times one lookup of a random name under example.com straight at the proxy's port, so it
     * cannot come from a cache and does not depend on interception. Only while the screen is
     * visible, and at most every [RTT_SAMPLE_MS].
     */
    private suspend fun sampleRtt(state: ModuleState) {
        val status = (state as? ModuleState.Ready)?.status ?: return
        if (!status.running) return
        val now = System.currentTimeMillis()
        if (now - lastRttSample < RTT_SAMPLE_MS) return
        lastRttSample = now
        val label = "rtt" + Random.nextLong(0, Long.MAX_VALUE).toString(36)
        val probe = DnsProbe.probe(status.port, host = "$label.${DnsProbe.DEFAULT_HOST}") as? ProbeResult.Answered ?: return
        _uiState.update { it.copy(rttHistory = (it.rttHistory + probe.rttMillis.toInt()).takeLast(RTT_SAMPLES)) }
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
            sampleRtt(_uiState.value.state)
            while (isActive) {
                delay(interval * 1000L)
                if (_uiState.value.busyAction == null) {
                    val state = repository.loadState()
                    applyPolled(state)
                    loadServers(_uiState.value.state)
                    sampleRtt(_uiState.value.state)
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

    private companion object {
        const val RTT_SAMPLE_MS = 10_000L
        const val RTT_SAMPLES = 20
    }
}
