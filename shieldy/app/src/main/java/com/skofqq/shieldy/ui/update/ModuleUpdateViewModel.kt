package com.skofqq.shieldy.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skofqq.shieldy.data.InstalledModule
import com.skofqq.shieldy.data.ModuleRelease
import com.skofqq.shieldy.data.ModuleUpdater
import com.skofqq.shieldy.data.UpdateError
import com.skofqq.shieldy.data.UpdateException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UpdatePhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    CHECK_FAILED,
    DOWNLOADING,
    INSTALLING,

    /** Installed, waiting for a reboot to take effect. */
    INSTALLED,
    INSTALL_FAILED,
}

data class ModuleUpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val installed: InstalledModule? = null,
    val release: ModuleRelease? = null,
    /** Changelog sections newer than the installed version. */
    val changelog: String? = null,
    /** 0..1, or -1 while the size is unknown. */
    val progress: Float = -1f,
    /** Installer output, line by line. */
    val log: List<String> = emptyList(),
    val error: UpdateError? = null,
    val rebooting: Boolean = false,
) {
    val updateAvailable: Boolean
        get() = release != null && installed != null && release.versionCode > installed.versionCode

    val busy: Boolean get() = phase == UpdatePhase.DOWNLOADING || phase == UpdatePhase.INSTALLING
}

class ModuleUpdateViewModel(private val updater: ModuleUpdater) : ViewModel() {

    private val _uiState = MutableStateFlow(ModuleUpdateUiState())
    val uiState: StateFlow<ModuleUpdateUiState> = _uiState.asStateFlow()

    init {
        check()
    }

    fun check() {
        if (_uiState.value.busy || _uiState.value.phase == UpdatePhase.CHECKING) return
        // A finished install stays on screen: checking again would offer the same zip.
        if (_uiState.value.phase == UpdatePhase.INSTALLED) return
        viewModelScope.launch {
            _uiState.update { it.copy(phase = UpdatePhase.CHECKING, error = null) }
            val installed = updater.installed().getOrElse { fail(UpdatePhase.CHECK_FAILED, it); return@launch }
            _uiState.update { it.copy(installed = installed) }
            val release = updater.latest(installed.updateJson)
                .getOrElse { fail(UpdatePhase.CHECK_FAILED, it); return@launch }
            if (release.versionCode <= installed.versionCode) {
                _uiState.update { it.copy(phase = UpdatePhase.UP_TO_DATE, release = release, changelog = null) }
                return@launch
            }
            val changelog = updater.changelog(release.changelogUrl, installed.version)
            _uiState.update {
                it.copy(phase = UpdatePhase.AVAILABLE, release = release, changelog = changelog)
            }
        }
    }

    fun install() {
        val state = _uiState.value
        val release = state.release ?: return
        if (state.busy || !state.updateAvailable) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(phase = UpdatePhase.DOWNLOADING, progress = -1f, log = emptyList(), error = null)
            }
            val zip = updater.download(release) { progress ->
                _uiState.update { it.copy(progress = progress) }
            }.getOrElse { fail(UpdatePhase.INSTALL_FAILED, it); return@launch }

            updater.verify(zip).getOrElse { fail(UpdatePhase.INSTALL_FAILED, it); return@launch }

            _uiState.update { it.copy(phase = UpdatePhase.INSTALLING) }
            updater.install(zip) { line ->
                _uiState.update { it.copy(log = it.log + line) }
            }.getOrElse { fail(UpdatePhase.INSTALL_FAILED, it); return@launch }

            _uiState.update { it.copy(phase = UpdatePhase.INSTALLED) }
        }
    }

    fun reboot() {
        if (_uiState.value.rebooting) return
        viewModelScope.launch {
            _uiState.update { it.copy(rebooting = true) }
            updater.reboot().onFailure { error ->
                _uiState.update { it.copy(rebooting = false, error = (error as? UpdateException)?.error) }
            }
        }
    }

    private fun fail(phase: UpdatePhase, error: Throwable) {
        val reason = (error as? UpdateException)?.error
            ?: UpdateError.Other(error.message ?: error.javaClass.simpleName)
        _uiState.update { it.copy(phase = phase, error = reason) }
    }
}
