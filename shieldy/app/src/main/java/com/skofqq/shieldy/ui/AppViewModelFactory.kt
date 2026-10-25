package com.skofqq.shieldy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.skofqq.shieldy.AppContainer
import com.skofqq.shieldy.ui.config.ConfigViewModel
import com.skofqq.shieldy.ui.logs.LogsViewModel
import com.skofqq.shieldy.ui.servers.ServersViewModel
import com.skofqq.shieldy.ui.settings.SettingsViewModel
import com.skofqq.shieldy.ui.status.StatusViewModel
import com.skofqq.shieldy.ui.update.ModuleUpdateViewModel

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

            modelClass.isAssignableFrom(com.skofqq.shieldy.ui.status.DnsCheckViewModel::class.java) ->
                com.skofqq.shieldy.ui.status.DnsCheckViewModel(container.repository, container.appContext)

            modelClass.isAssignableFrom(com.skofqq.shieldy.ui.blocklists.BlocklistsViewModel::class.java) ->
                com.skofqq.shieldy.ui.blocklists.BlocklistsViewModel(container.blocklists, container.repository, container.appContext)

            modelClass.isAssignableFrom(ModuleUpdateViewModel::class.java) ->
                ModuleUpdateViewModel(container.updater)

            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return viewModel as T
    }
}
