package io.github.skofqq.dnscryptmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.skofqq.dnscryptmanager.AppContainer
import io.github.skofqq.dnscryptmanager.ui.config.ConfigViewModel
import io.github.skofqq.dnscryptmanager.ui.logs.LogsViewModel
import io.github.skofqq.dnscryptmanager.ui.servers.ServersViewModel
import io.github.skofqq.dnscryptmanager.ui.settings.SettingsViewModel
import io.github.skofqq.dnscryptmanager.ui.status.StatusViewModel
import io.github.skofqq.dnscryptmanager.ui.update.ModuleUpdateViewModel

/** Wires view models by hand, since the app has no DI framework. */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(StatusViewModel::class.java) ->
                StatusViewModel(container.repository, container.preferences)

            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(container.repository, container.preferences)

            modelClass.isAssignableFrom(ConfigViewModel::class.java) ->
                ConfigViewModel(container.repository)

            modelClass.isAssignableFrom(LogsViewModel::class.java) ->
                LogsViewModel(container.repository)

            modelClass.isAssignableFrom(ServersViewModel::class.java) ->
                ServersViewModel(container.repository, container.appContext)

            modelClass.isAssignableFrom(io.github.skofqq.dnscryptmanager.ui.status.DnsCheckViewModel::class.java) ->
                io.github.skofqq.dnscryptmanager.ui.status.DnsCheckViewModel(container.repository, container.appContext)

            modelClass.isAssignableFrom(ModuleUpdateViewModel::class.java) ->
                ModuleUpdateViewModel(container.updater)

            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return viewModel as T
    }
}
