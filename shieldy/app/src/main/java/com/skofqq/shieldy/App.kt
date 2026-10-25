package com.skofqq.shieldy

import com.skofqq.shieldy.work.BlocklistWorker
import android.app.Application
import android.content.Context
import com.skofqq.shieldy.data.AppPreferences
import com.skofqq.shieldy.data.BlocklistRepository
import com.skofqq.shieldy.data.ModuleRepository
import com.skofqq.shieldy.data.ModuleUpdater
import com.skofqq.shieldy.root.RootShell

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
        // Keeps the daily blocklist job in line with the settings, including ones that
        // arrived from DNSCrypt Manager.
        BlocklistWorker.schedule(this, container.blocklists.settings.value)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as App).container
