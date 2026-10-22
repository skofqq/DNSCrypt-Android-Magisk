package io.github.skofqq.dnscryptmanager.tile

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.appContainer
import io.github.skofqq.dnscryptmanager.data.ModuleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings toggle. The icon follows `running`, the subtitle shows the local port.
 */
class DnsCryptTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var toggleJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        scope = CoroutineScope(SupervisorJob())
        refresh()
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        toggleJob = null
        super.onStopListening()
    }

    override fun onClick() {
        val active = scope ?: return
        if (toggleJob?.isActive == true) return
        toggleJob = active.launch {
            val repository = applicationContext.appContainer.repository
            val state = repository.loadState()
            if (state !is ModuleState.Ready) {
                render(state)
                return@launch
            }
            if (state.status.running) repository.stop() else repository.start()
            render(repository.loadState())
        }
    }

    private fun refresh() {
        val active = scope ?: return
        active.launch { render(applicationContext.appContainer.repository.loadState()) }
    }

    private fun render(state: ModuleState) {
        val tile = qsTile ?: return
        when (state) {
            is ModuleState.Ready -> {
                val running = state.status.running
                tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.subtitle = state.status.port.toString()
                tile.icon = Icon.createWithResource(
                    this,
                    if (running) R.drawable.ic_qs_tile else R.drawable.ic_qs_tile_off,
                )
            }

            else -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = getString(
                    when (state) {
                        is ModuleState.NoRoot -> R.string.gate_no_root_title
                        is ModuleState.ModuleMissing -> R.string.gate_missing_title
                        is ModuleState.ModuleDisabled -> R.string.gate_disabled_title
                        is ModuleState.Outdated -> R.string.gate_outdated_title
                        else -> R.string.gate_loading
                    }
                )
                tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_tile_off)
            }
        }
        tile.label = getString(R.string.tile_label)
        tile.updateTile()
    }
}
