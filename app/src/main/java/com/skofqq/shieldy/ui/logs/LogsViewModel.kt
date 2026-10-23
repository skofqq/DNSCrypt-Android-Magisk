package com.skofqq.shieldy.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skofqq.shieldy.data.LevelFilter
import com.skofqq.shieldy.data.LogLine
import com.skofqq.shieldy.data.ModuleRepository
import com.skofqq.shieldy.data.ModuleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogTab { MODULE, PROXY, RULES, CONFLICTS }

/** A name added from a log line: whether it was new, and whether the proxy runs to restart it. */
data class NameResult(val name: String, val allowed: Boolean, val added: Boolean, val running: Boolean)

data class LogsUiState(
    val tab: LogTab = LogTab.MODULE,
    val lines: List<LogLine> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Live: re-read the file every few seconds and stay at the bottom. */
    val follow: Boolean = true,
    val query: String = "",
    /** With a query: hide the lines that do not match instead of only highlighting. */
    val onlyMatches: Boolean = false,
    val level: LevelFilter = LevelFilter.ALL,
    val lineCount: Int = DEFAULT_LINES,
    val wrap: Boolean = true,
    val nameResult: NameResult? = null,
) {
    /** Rules and conflicts are command output, not logs: no levels, no tail length. */
    val isLog: Boolean get() = tab == LogTab.MODULE || tab == LogTab.PROXY

    val visibleLines: List<LogLine>
        get() = lines.filter { line ->
            line.passes(level) &&
                (!onlyMatches || query.isBlank() || line.raw.contains(query, ignoreCase = true))
        }

    /** Indexes into [visibleLines] that contain the query, for the counter and jumps. */
    val matchIndexes: List<Int>
        get() = if (query.isBlank()) {
            emptyList()
        } else {
            visibleLines.mapIndexedNotNull { index, line -> index.takeIf { line.raw.contains(query, ignoreCase = true) } }
        }

    companion object {
        const val DEFAULT_LINES = 200
        val LINE_CHOICES = listOf(200, 500, 2000)
    }
}

class LogsViewModel(
    private val repository: ModuleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(tab: LogTab) {
        if (_uiState.value.tab == tab) return
        _uiState.update { it.copy(tab = tab, lines = emptyList(), error = null) }
        refresh()
    }

    fun setFollow(enabled: Boolean) {
        _uiState.update { it.copy(follow = enabled) }
        if (enabled) refresh(silent = true)
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun setOnlyMatches(enabled: Boolean) = _uiState.update { it.copy(onlyMatches = enabled) }

    fun setLevel(level: LevelFilter) = _uiState.update { it.copy(level = level) }

    fun setWrap(enabled: Boolean) = _uiState.update { it.copy(wrap = enabled) }

    fun setLineCount(count: Int) {
        if (_uiState.value.lineCount == count) return
        _uiState.update { it.copy(lineCount = count) }
        refresh()
    }

    /** Byte offset each log was read up to, so Live only fetches what was appended. */
    private val offsets = mutableMapOf<LogTab, Long>()

    /**
     * Live update of a log tab: reads from the stored offset and appends, keeping the
     * last [LogsUiState.lineCount] lines. Without an offset it is a full [refresh].
     */
    fun liveRefresh() {
        val state = _uiState.value
        val from = offsets[state.tab]
        if (!state.isLog || from == null) {
            refresh(silent = true)
            return
        }
        viewModelScope.launch {
            val chunk = repository.logChunk(state.tab == LogTab.PROXY, from, state.lineCount).getOrNull()
                ?: return@launch refresh(silent = true)
            if (_uiState.value.tab != state.tab) return@launch
            offsets[state.tab] = chunk.size
            if (!chunk.reset && chunk.lines.isEmpty()) return@launch
            val added = chunk.lines.map(LogLine::parse)
            _uiState.update {
                val lines = if (chunk.reset) added else (it.lines + added).takeLast(it.lineCount)
                it.copy(lines = lines, error = null)
            }
        }
    }

    /** [silent] skips the progress bar: live updates should not flicker every few seconds. */
    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            val state = _uiState.value
            if (!silent) _uiState.update { it.copy(loading = true, error = null) }
            if (state.isLog) {
                // Straight from the file, so the size it ended at is known for Live.
                val chunk = repository.logChunk(state.tab == LogTab.PROXY, null, state.lineCount).getOrNull()
                if (chunk != null) {
                    if (_uiState.value.tab != state.tab) return@launch
                    offsets[state.tab] = chunk.size
                    _uiState.update {
                        it.copy(loading = false, lines = chunk.lines.map(LogLine::parse), error = null)
                    }
                    return@launch
                }
                offsets.remove(state.tab)
            }
            val result = when (state.tab) {
                LogTab.MODULE -> repository.moduleLog(state.lineCount)
                LogTab.PROXY -> repository.proxyLog(state.lineCount)
                LogTab.RULES -> repository.rules()
                LogTab.CONFLICTS -> repository.conflicts()
            }
            // The tab may have changed while the shell ran; drop the stale answer.
            if (_uiState.value.tab != state.tab) return@launch
            result.fold(
                onSuccess = { output ->
                    // `rules` exits non-zero when one table is missing (no ip6tables nat)
                    // yet prints the rest: command output is shown whenever there is some.
                    val usable = output.isSuccess || (!state.isLog && output.stdout.isNotEmpty())
                    _uiState.update {
                        it.copy(
                            loading = false,
                            lines = if (usable) output.stdout.map(LogLine::parse) else emptyList(),
                            error = if (usable) null else output.message,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(loading = false, error = error.message ?: error.javaClass.simpleName)
                    }
                },
            )
        }
    }

    /** Adds a domain from a log line to blocked- or allowed-names.txt. Never starts the proxy. */
    fun addName(allowed: Boolean, name: String) {
        viewModelScope.launch {
            repository.addName(allowed, name).fold(
                onSuccess = { added ->
                    val running = (repository.loadState() as? ModuleState.Ready)?.status?.running == true
                    _uiState.update { it.copy(nameResult = NameResult(name, allowed, added, running)) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(error = error.message ?: error.javaClass.simpleName) }
                },
            )
        }
    }

    fun dismissNameResult() = _uiState.update { it.copy(nameResult = null) }

    fun restartNow() {
        viewModelScope.launch { repository.restartIfRunning() }
    }

    fun currentText(): String = _uiState.value.visibleLines.joinToString("\n") { it.raw }

    fun exportFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val base = when (_uiState.value.tab) {
            LogTab.MODULE -> "module"
            LogTab.PROXY -> "dnscrypt-proxy"
            LogTab.RULES -> "rules"
            LogTab.CONFLICTS -> "conflicts"
        }
        return "$base-$stamp.txt"
    }
}
