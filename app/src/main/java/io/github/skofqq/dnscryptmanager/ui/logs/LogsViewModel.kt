package io.github.skofqq.dnscryptmanager.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.skofqq.dnscryptmanager.data.LevelFilter
import io.github.skofqq.dnscryptmanager.data.LogLine
import io.github.skofqq.dnscryptmanager.data.ModuleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogTab { MODULE, PROXY, RULES, CONFLICTS }

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

    /** [silent] skips the progress bar: live updates should not flicker every few seconds. */
    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            val state = _uiState.value
            if (!silent) _uiState.update { it.copy(loading = true, error = null) }
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
                    _uiState.update {
                        it.copy(
                            loading = false,
                            lines = if (output.isSuccess) output.stdout.map(LogLine::parse) else emptyList(),
                            error = if (output.isSuccess) null else output.message,
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
