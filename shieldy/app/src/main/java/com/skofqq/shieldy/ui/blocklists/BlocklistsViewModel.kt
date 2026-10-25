package com.skofqq.shieldy.ui.blocklists

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skofqq.shieldy.data.BlocklistRepository
import com.skofqq.shieldy.data.BlocklistSettings
import com.skofqq.shieldy.data.ModuleRepository
import com.skofqq.shieldy.data.ModuleState
import com.skofqq.shieldy.data.UiMessage
import com.skofqq.shieldy.work.BlocklistWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlocklistsUiState(
    val settings: BlocklistSettings = BlocklistSettings(),
    val updating: Boolean = false,
    /** Lists were merged while the proxy runs: it needs a restart to load them. */
    val restartOffer: Boolean = false,
)

class BlocklistsViewModel(
    private val blocklists: BlocklistRepository,
    private val repository: ModuleRepository,
    private val context: Context,
) : ViewModel() {

    private val local = MutableStateFlow(BlocklistsUiState())

    val uiState: StateFlow<BlocklistsUiState> = combine(local, blocklists.settings) { state, settings ->
        state.copy(settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlocklistsUiState(blocklists.settings.value))

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    fun setEnabled(id: String, enabled: Boolean) = blocklists.setEnabled(id, enabled)

    fun add(name: String, url: String) = blocklists.add(name, url)

    fun remove(id: String) = blocklists.remove(id)

    fun setAutoUpdate(enabled: Boolean) = reschedule { blocklists.setAutoUpdate(enabled) }

    fun setUnmeteredOnly(enabled: Boolean) = reschedule { blocklists.setUnmeteredOnly(enabled) }

    fun setChargingOnly(enabled: Boolean) = reschedule { blocklists.setChargingOnly(enabled) }

    private fun reschedule(change: () -> Unit) {
        change()
        BlocklistWorker.schedule(context, blocklists.settings.value)
    }

    /** Downloads and merges now; a running proxy is offered a restart, never started. */
    fun updateNow() {
        if (local.value.updating) return
        viewModelScope.launch {
            local.update { it.copy(updating = true, restartOffer = false) }
            val result = blocklists.updateAll(restart = false)
            val running = (repository.loadState() as? ModuleState.Ready)?.status?.running == true
            local.update { it.copy(updating = false, restartOffer = result.isSuccess && running) }
            result.exceptionOrNull()?.let { _messages.emit(UiMessage.of(it)) }
            if (result.isSuccess && !running) _messages.emit(UiMessage.SavedForNextStart)
        }
    }

    fun restartNow() {
        local.update { it.copy(restartOffer = false) }
        viewModelScope.launch {
            repository.restartIfRunning()?.let { result -> UiMessage.ofFailure(result)?.let { _messages.emit(it) } }
        }
    }

    fun dismissRestart() = local.update { it.copy(restartOffer = false) }
}
