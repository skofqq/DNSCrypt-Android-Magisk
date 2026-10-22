package io.github.skofqq.dnscryptmanager.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.skofqq.dnscryptmanager.data.ModuleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LogTab { MODULE, PROXY, RULES, CONFLICTS }

data class LogsUiState(
    val tab: LogTab = LogTab.MODULE,
    val lines: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val follow: Boolean = true,
    val query: String = "",
    val lineCount: Int = DEFAULT_LINES,
) {
    val visibleLines: List<String>
        get() = if (query.isBlank()) lines else lines.filter { it.contains(query, ignoreCase = true) }

    companion object {
        const val DEFAULT_LINES = 200
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
        _uiState.value = _uiState.value.copy(tab = tab, lines = emptyList(), error = null)
        refresh()
    }

    fun setFollow(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(follow = enabled)
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun refresh() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(loading = true, error = null)
            val result = when (state.tab) {
                LogTab.MODULE -> repository.moduleLog(state.lineCount)
                LogTab.PROXY -> repository.proxyLog(state.lineCount)
                LogTab.RULES -> repository.rules()
                LogTab.CONFLICTS -> repository.conflicts()
            }
            result.fold(
                onSuccess = { output ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        lines = if (output.isSuccess) output.stdout else emptyList(),
                        error = if (output.isSuccess) null else output.message,
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

    fun currentText(): String = _uiState.value.visibleLines.joinToString("\n")
}
