package io.github.skofqq.dnscryptmanager

import android.app.Application
import android.content.Context
import io.github.skofqq.dnscryptmanager.data.AppPreferences
import io.github.skofqq.dnscryptmanager.data.BlocklistRepository
import io.github.skofqq.dnscryptmanager.data.ModuleRepository
import io.github.skofqq.dnscryptmanager.data.ModuleUpdater
import io.github.skofqq.dnscryptmanager.root.RootShell

/**
 * Hand-rolled dependency container: the app is small enough that a DI framework
 * would cost more than it saves.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val preferences: AppPreferences = AppPreferences(context)
    val rootShell: RootShell = RootShell()
    val repository: ModuleRepository = ModuleRepository(rootShell, context.cacheDir)
    val updater: ModuleUpdater = ModuleUpdater(rootShell, context.cacheDir)
    val blocklists: BlocklistRepository = BlocklistRepository(context, repository)
}

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as App).container
