package io.github.skofqq.dnscryptmanager.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.skofqq.dnscryptmanager.data.ModuleContract
import io.github.skofqq.dnscryptmanager.data.ModuleRepository
import io.github.skofqq.dnscryptmanager.data.ResolverEntry
import io.github.skofqq.dnscryptmanager.data.ResolverParser
import io.github.skofqq.dnscryptmanager.data.ServerSelection
import io.github.skofqq.dnscryptmanager.data.StampProtocol
import io.github.skofqq.dnscryptmanager.data.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ServersUiState(
    val loading: Boolean = true,
    val applying: Boolean = false,
    /** True when the proxy has not downloaded public-resolvers.md yet. */
    val cacheMissing: Boolean = false,
    val catalog: List<ResolverEntry> = emptyList(),
    val cacheEpochSeconds: Long? = null,
    val savedAuto: Boolean = true,
    val savedSelection: List<String> = emptyList(),
    val draftAuto: Boolean = true,
    val draftSelection: List<String> = emptyList(),
    /** The chip the up/down buttons act on. */
    val focusedServer: String? = null,
    val query: String = "",
    val protocols: Set<StampProtocol> = emptySet(),
    val requireDnssec: Boolean = false,
    val requireNoLog: Boolean = false,
    val requireNoFilter: Boolean = false,
    val activeServer: String? = null,
    val error: String? = null,
) {
    private val catalogNames: Set<String> by lazy { catalog.mapTo(HashSet()) { it.name } }

    /** Legal: a name may come from somewhere other than the cached catalogue. */
    fun isKnown(name: String): Boolean = catalog.isEmpty() || name in catalogNames

    val unknownSelected: List<String>
        get() = if (catalog.isEmpty()) emptyList() else draftSelection.filterNot { it in catalogNames }

    val isStale: Boolean
        get() = cacheEpochSeconds?.let {
            System.currentTimeMillis() / 1000 - it > ModuleContract.CACHE_STALE_SECONDS
        } == true

    val changeCount: Int
        get() = when {
            draftAuto && savedAuto -> 0
            draftAuto != savedAuto -> 1
            else -> {
                val added = draftSelection.filterNot { it in savedSelection }
                val removed = savedSelection.filterNot { it in draftSelection }
                val reordered =
                    if (added.isEmpty() && removed.isEmpty() && draftSelection != savedSelection) 1 else 0
                added.size + removed.size + reordered
            }
        }

    /** An empty manual list means the same thing as auto, so warn before applying it. */
    val emptyManualSelection: Boolean
        get() = !draftAuto && draftSelection.isEmpty()
}

class ServersViewModel(
    private val repository: ModuleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServersUiState())
    val uiState: StateFlow<ServersUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    private val _applied = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val applied: SharedFlow<Unit> = _applied

    init {
        load(force = true)
    }

    /**
     * Reads the selection every time, but re-reads and re-parses the 300 KB catalogue
     * only when the cache file actually changed or the user asked for it.
     */
    fun load(force: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }

            val selection = repository.serversGet().getOrNull()
            val savedSelection = selection
                ?.takeIf { it.isSuccess }
                ?.let { ServerSelection.parseSelection(it.out) }
                ?: emptyList()
            val savedAuto = savedSelection.isEmpty()

            val active = repository.serversActive().getOrNull()
                ?.takeIf { it.isSuccess }
                ?.out?.trim()
                ?.takeIf { it.isNotEmpty() }

            val epoch = repository.resolverCacheTimestamp()
            val current = _uiState.value
            val catalogUsable = current.catalog.isNotEmpty() &&
                !force &&
                epoch != null &&
                epoch == current.cacheEpochSeconds

            if (catalogUsable) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        savedAuto = savedAuto,
                        savedSelection = savedSelection,
                        draftAuto = savedAuto,
                        draftSelection = savedSelection,
                        activeServer = active,
                        cacheEpochSeconds = epoch,
                    )
                }
                return@launch
            }

            val raw = repository.serversRaw().getOrNull()
            if (raw == null || !raw.isSuccess) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        cacheMissing = true,
                        catalog = emptyList(),
                        savedAuto = savedAuto,
                        savedSelection = savedSelection,
                        draftAuto = savedAuto,
                        draftSelection = savedSelection,
                        activeServer = active,
                        error = raw?.message?.takeIf { message -> message.isNotBlank() },
                    )
                }
                return@launch
            }

            // 774 blocks is enough work to keep off the main thread.
            val parsed = withContext(Dispatchers.Default) { ResolverParser.parse(raw.out) }

            _uiState.update {
                it.copy(
                    loading = false,
                    cacheMissing = parsed.isEmpty(),
                    catalog = parsed,
                    cacheEpochSeconds = epoch,
                    savedAuto = savedAuto,
                    savedSelection = savedSelection,
                    draftAuto = savedAuto,
                    draftSelection = savedSelection,
                    activeServer = active,
                )
            }
        }
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun setDraftAuto(auto: Boolean) = _uiState.update {
        it.copy(draftAuto = auto, focusedServer = if (auto) null else it.focusedServer)
    }

    fun toggleProtocol(protocol: StampProtocol) = _uiState.update {
        val protocols = if (protocol in it.protocols) it.protocols - protocol else it.protocols + protocol
        it.copy(protocols = protocols)
    }

    fun toggleDnssec() = _uiState.update { it.copy(requireDnssec = !it.requireDnssec) }

    fun toggleNoLog() = _uiState.update { it.copy(requireNoLog = !it.requireNoLog) }

    fun toggleNoFilter() = _uiState.update { it.copy(requireNoFilter = !it.requireNoFilter) }

    fun addServer(name: String) = _uiState.update {
        if (name in it.draftSelection) it
        else it.copy(draftSelection = it.draftSelection + name, draftAuto = false)
    }

    fun removeServer(name: String) = _uiState.update {
        it.copy(
            draftSelection = it.draftSelection - name,
            focusedServer = if (it.focusedServer == name) null else it.focusedServer,
        )
    }

    fun focusServer(name: String) = _uiState.update {
        it.copy(focusedServer = if (it.focusedServer == name) null else name)
    }

    /** Order matters: the proxy tries the servers in the order it is given them. */
    fun moveFocused(up: Boolean) = _uiState.update { state ->
        val name = state.focusedServer ?: return@update state
        val index = state.draftSelection.indexOf(name)
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in state.draftSelection.indices) return@update state
        val reordered = state.draftSelection.toMutableList()
        reordered[index] = reordered[target]
        reordered[target] = name
        state.copy(draftSelection = reordered)
    }

    fun discard() = _uiState.update {
        it.copy(
            draftAuto = it.savedAuto,
            draftSelection = it.savedSelection,
            focusedServer = null,
        )
    }

    /** One `servers set` (or `servers auto`), then one restart. */
    fun apply() {
        val state = _uiState.value
        if (state.applying || state.changeCount == 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true) }

            val toAuto = state.draftAuto || state.draftSelection.isEmpty()
            val write = if (toAuto) {
                repository.serversAuto()
            } else {
                repository.serversSet(state.draftSelection)
            }
            val writeFailure = UiMessage.ofFailure(write)
            if (writeFailure != null) {
                _messages.emit(writeFailure)
                _uiState.update { it.copy(applying = false) }
                return@launch
            }

            val restartFailure = UiMessage.ofFailure(repository.restart())
            _uiState.update { it.copy(applying = false) }
            if (restartFailure != null) {
                _messages.emit(restartFailure)
            } else {
                _applied.emit(Unit)
            }
            load()
        }
    }
}
