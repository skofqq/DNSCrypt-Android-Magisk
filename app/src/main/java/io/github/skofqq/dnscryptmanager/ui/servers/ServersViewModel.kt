package io.github.skofqq.dnscryptmanager.ui.servers

import android.content.Context
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
    /** disabled_server_names: skipped by the proxy even in automatic mode. */
    val excluded: Set<String> = emptySet(),
    /** Server name → two-letter country, or [ServerCountries.UNKNOWN]; null until worked out. */
    val countries: Map<String, String>? = null,
    /** Countries excluded as a whole (app-side rule; the module only keeps names). */
    val countryRules: Set<String> = emptySet(),
    val exclusionsBusy: Boolean = false,
    /** Exclusions changed while the proxy runs: offer a restart instead of doing it. */
    val restartOffer: Boolean = false,
    val showExcludedOnly: Boolean = false,
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
    private val context: Context,
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
        }.invokeOnCompletion { viewModelScope.launch { refreshExclusions(); computeCountries() } }
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

            val restart = repository.restartIfRunning()
            val restartFailure = restart?.let(UiMessage::ofFailure)
            _uiState.update { it.copy(applying = false) }
            when {
                restartFailure != null -> _messages.emit(restartFailure)
                restart == null -> _messages.emit(UiMessage.SavedForNextStart)
                else -> _applied.emit(Unit)
            }
            load()
        }
    }

    // ---- Exclusions (disabled_server_names) ----

    /** Which names the country rules put on the list, so lifting a rule spares manual exclusions. */
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private suspend fun refreshExclusions() {
        val out = repository.serversExcluded().getOrNull()?.takeIf { it.isSuccess } ?: return
        val names = out.out.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        _uiState.update {
            it.copy(excluded = names, countryRules = prefs.getStringSet(KEY_COUNTRIES, emptySet()).orEmpty())
        }
    }

    /** Country per server, worked out once per resolver list (keyed by its timestamp). */
    private suspend fun computeCountries() {
        val state = _uiState.value
        if (state.catalog.isEmpty() || state.countries != null) return
        // v2: location hints from names and descriptions now come before GeoIP.
        val cacheFile = java.io.File(context.cacheDir, "countries-v2-${state.cacheEpochSeconds ?: 0}.txt")
        val cached = runCatching {
            cacheFile.readLines().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        val countries = cached ?: run {
            val geo = withContext(Dispatchers.IO) { io.github.skofqq.dnscryptmanager.data.GeoIp.load(context) }
            io.github.skofqq.dnscryptmanager.data.ServerCountries.map(state.catalog, geo).also { map ->
                runCatching {
                    context.cacheDir.listFiles { f -> f.name.startsWith("countries-") }?.forEach { it.delete() }
                    cacheFile.writeText(map.entries.joinToString("\n") { "${it.key}=${it.value}" })
                }
            }
        }
        _uiState.update { it.copy(countries = countries + assignedCountries()) }
        syncCountryRules()
    }

    /**
     * An excluded country also covers servers that land in it later: a new resolver list,
     * or a better guess at where a server is. Servers the user let back in stay in.
     */
    private fun syncCountryRules() {
        val state = _uiState.value
        val countries = state.countries ?: return
        val kept = prefs.getStringSet(KEY_KEPT, emptySet()).orEmpty()
        val missing = countries.filter { (name, code) ->
            code in state.countryRules && name !in state.excluded && name !in kept
        }.keys
        if (missing.isEmpty()) return
        editExclusions {
            val result = repository.serversExclude(missing)
            if (result.getOrNull()?.isSuccess == true) {
                val owned = prefs.getStringSet(KEY_COUNTRY_NAMES, emptySet()).orEmpty() + missing
                prefs.edit().putStringSet(KEY_COUNTRY_NAMES, owned).apply()
            }
            result
        }
    }

    /** Countries the user set by hand for servers the app could not place. */
    private fun assignedCountries(): Map<String, String> =
        prefs.getStringSet(KEY_ASSIGNED, emptySet()).orEmpty()
            .mapNotNull { entry -> entry.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()

    /**
     * Puts a server into [code]'s group. If that country is excluded, the server is
     * excluded with it, and lifting the country later gives it back.
     */
    fun assignCountry(name: String, code: String) {
        val assigned = assignedCountries() + (name to code)
        prefs.edit().putStringSet(KEY_ASSIGNED, assigned.map { "${it.key}=${it.value}" }.toSet()).apply()
        _uiState.update { it.copy(countries = it.countries?.plus(name to code)) }
        val state = _uiState.value
        if (code in state.countryRules && name !in state.excluded) {
            editExclusions {
                val result = repository.serversExclude(listOf(name))
                if (result.getOrNull()?.isSuccess == true) {
                    val owned = prefs.getStringSet(KEY_COUNTRY_NAMES, emptySet()).orEmpty() + name
                    prefs.edit().putStringSet(KEY_COUNTRY_NAMES, owned).apply()
                }
                result
            }
        }
    }

    fun excludeServer(name: String) = editExclusions { repository.serversExclude(listOf(name)) }

    fun includeServer(name: String) = editExclusions {
        // A manual include also takes the name out of the country bookkeeping, and if
        // its country is excluded, keeps it from being swept back in.
        val fromCountries = prefs.getStringSet(KEY_COUNTRY_NAMES, emptySet()).orEmpty() - name
        val state = _uiState.value
        val kept = prefs.getStringSet(KEY_KEPT, emptySet()).orEmpty().let {
            if (state.countries?.get(name) in state.countryRules) it + name else it
        }
        prefs.edit().putStringSet(KEY_COUNTRY_NAMES, fromCountries).putStringSet(KEY_KEPT, kept).apply()
        repository.serversInclude(listOf(name))
    }

    /**
     * Applies a new set of excluded countries. Newly chosen countries add their servers
     * (only those not excluded already); dropped countries give back exactly the names
     * they added, so a server excluded by hand stays excluded.
     */
    fun setCountryRules(codes: Set<String>) {
        val state = _uiState.value
        val countries = state.countries ?: return
        val before = prefs.getStringSet(KEY_COUNTRIES, emptySet()).orEmpty()
        val added = codes - before
        val removed = before - codes
        val owned = prefs.getStringSet(KEY_COUNTRY_NAMES, emptySet()).orEmpty().toMutableSet()

        val toExclude = countries.filter { it.value in added && it.key !in state.excluded }.keys
        val toInclude = owned.filter { countries[it] in removed }.toSet()

        editExclusions {
            val include = repository.serversInclude(toInclude)
            if (include.getOrNull()?.isSuccess == false || include.isFailure) return@editExclusions include
            val exclude = repository.serversExclude(toExclude)
            if (exclude.getOrNull()?.isSuccess == true) {
                owned.removeAll(toInclude)
                owned.addAll(toExclude)
                // Letting a country back in ends the exceptions made inside it.
                val kept = prefs.getStringSet(KEY_KEPT, emptySet()).orEmpty().filterNot { countries[it] in removed }.toSet()
                prefs.edit()
                    .putStringSet(KEY_COUNTRIES, codes)
                    .putStringSet(KEY_COUNTRY_NAMES, owned)
                    .putStringSet(KEY_KEPT, kept)
                    .apply()
            }
            exclude
        }
    }

    private fun editExclusions(edit: suspend () -> Result<io.github.skofqq.dnscryptmanager.root.ShellOutput>) {
        if (_uiState.value.exclusionsBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(exclusionsBusy = true) }
            val failure = UiMessage.ofFailure(edit())
            refreshExclusions()
            // Never start a stopped proxy for this: only offer a restart when it runs.
            val running = (repository.loadState() as? io.github.skofqq.dnscryptmanager.data.ModuleState.Ready)?.status?.running == true
            _uiState.update { it.copy(exclusionsBusy = false, restartOffer = failure == null && running) }
            when {
                failure != null -> _messages.emit(failure)
                !running -> _messages.emit(UiMessage.SavedForNextStart)
            }
        }
    }

    fun restartNow() {
        _uiState.update { it.copy(restartOffer = false) }
        viewModelScope.launch {
            val restart = repository.restartIfRunning() ?: return@launch
            val failure = UiMessage.ofFailure(restart)
            if (failure != null) _messages.emit(failure) else _applied.emit(Unit)
            load()
        }
    }

    fun dismissRestartOffer() = _uiState.update { it.copy(restartOffer = false) }

    fun toggleExcludedOnly() = _uiState.update { it.copy(showExcludedOnly = !it.showExcludedOnly) }

    private companion object {
        const val PREFS = "exclusions"
        const val KEY_COUNTRIES = "countries"
        const val KEY_COUNTRY_NAMES = "country_names"
        const val KEY_ASSIGNED = "assigned"
        const val KEY_KEPT = "kept"
    }
}
