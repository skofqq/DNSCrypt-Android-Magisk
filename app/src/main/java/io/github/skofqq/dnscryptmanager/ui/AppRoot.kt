package io.github.skofqq.dnscryptmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.appContainer
import io.github.skofqq.dnscryptmanager.data.ModuleContract
import io.github.skofqq.dnscryptmanager.data.ModuleState
import io.github.skofqq.dnscryptmanager.data.UiMessage
import io.github.skofqq.dnscryptmanager.ui.components.GateScreen
import io.github.skofqq.dnscryptmanager.ui.config.ConfigEvent
import io.github.skofqq.dnscryptmanager.ui.config.ConfigScreen
import io.github.skofqq.dnscryptmanager.ui.config.ConfigViewModel
import io.github.skofqq.dnscryptmanager.ui.logs.LogsScreen
import io.github.skofqq.dnscryptmanager.ui.logs.LogTab
import io.github.skofqq.dnscryptmanager.ui.logs.LogsViewModel
import io.github.skofqq.dnscryptmanager.ui.servers.ServersScreen
import io.github.skofqq.dnscryptmanager.ui.servers.ServersViewModel
import io.github.skofqq.dnscryptmanager.ui.settings.SettingsScreen
import io.github.skofqq.dnscryptmanager.ui.settings.SettingsViewModel
import io.github.skofqq.dnscryptmanager.ui.status.StatusScreen
import io.github.skofqq.dnscryptmanager.ui.status.StatusViewModel
import io.github.skofqq.dnscryptmanager.ui.update.ModuleUpdateViewModel
import kotlinx.coroutines.launch

enum class Destination(val labelRes: Int, val iconRes: Int) {
    STATUS(R.string.nav_status, R.drawable.ic_shield),
    SERVERS(R.string.nav_servers, R.drawable.ic_dns),
    SETTINGS(R.string.nav_settings, R.drawable.ic_tune),
    CONFIG(R.string.nav_config, R.drawable.ic_article),
    LOGS(R.string.nav_logs, R.drawable.ic_terminal),
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val factory = remember(context) { AppViewModelFactory(context.appContainer) }
    val statusViewModel: StatusViewModel = viewModel(factory = factory)
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()

    var destination by rememberSaveable { mutableStateOf(Destination.STATUS) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val haptics = LocalHapticFeedback.current

    // Resource lookups belong in composable scope; effects only see the finished text.
    val timeoutText = stringResource(R.string.error_timeout)
    val noRootText = stringResource(R.string.error_no_root)
    val resolve: (UiMessage) -> String = remember(timeoutText, noRootText) {
        { message ->
            when (message) {
                is UiMessage.Raw -> message.text
                is UiMessage.Timeout -> timeoutText
                is UiMessage.NoRoot -> noRootText
            }
        }
    }

    LaunchedEffect(statusViewModel) {
        statusViewModel.messages.collect { snackbarHostState.showSnackbar(resolve(it)) }
    }

    // Buzz when the proxy actually changed state, not when the button was pressed.
    LaunchedEffect(statusViewModel) {
        statusViewModel.completions.collect {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }

    val gated = statusState.state !is ModuleState.Ready
    val readyStatus = (statusState.state as? ModuleState.Ready)?.status
    // Feature gates by module version: servers from r2, conflicts and run_gid from r3.
    val serversSupported = readyStatus?.supportsServers == true
    val conflictsSupported = readyStatus?.supportsConflicts == true
    val runGidSupported = readyStatus?.supportsRunGid == true
    val mirrorSupported = readyStatus?.supportsMirror == true
    val resetSupported = readyStatus?.supportsReset == true
    val autostartSupported = readyStatus?.supportsAutostart == true
    var pendingLogTab by remember { mutableStateOf<LogTab?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!gated) {
                NavigationBar {
                    Destination.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = destination == entry,
                            onClick = { destination = entry },
                            icon = {
                                Icon(
                                    painter = painterResource(entry.iconRes),
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(entry.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (gated) {
            GateScreen(
                state = statusState.state,
                onRetry = statusViewModel::retryRoot,
                onOpenReleases = { context.openUrl(ModuleContract.RELEASES_URL) },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        AnimatedContent(
            targetState = destination,
            label = "destination",
            modifier = Modifier.padding(padding),
        ) { current ->
            when (current) {
                Destination.STATUS -> {
                    LifecycleResumeEffect(Unit) {
                        statusViewModel.startPolling()
                        onPauseOrDispose { statusViewModel.stopPolling() }
                    }
                    StatusScreen(
                        uiState = statusState,
                        onRefresh = { statusViewModel.refresh(showSpinner = true) },
                        onAction = statusViewModel::run,
                        onProbe = statusViewModel::probe,
                        onModuleTest = statusViewModel::runModuleTest,
                        onOpenLogs = { destination = Destination.LOGS },
                        onOpenConflicts = {
                            pendingLogTab = LogTab.CONFLICTS
                            destination = Destination.LOGS
                        },
                    )
                }

                Destination.SERVERS -> {
                    val serversViewModel: ServersViewModel = viewModel(factory = factory)
                    val serversState by serversViewModel.uiState.collectAsStateWithLifecycle()
                    val appliedText = stringResource(R.string.servers_applied)

                    LaunchedEffect(serversViewModel) {
                        serversViewModel.messages.collect {
                            snackbarHostState.showSnackbar(resolve(it))
                        }
                    }
                    LaunchedEffect(serversViewModel) {
                        serversViewModel.applied.collect {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            snackbarHostState.showSnackbar(appliedText)
                            statusViewModel.refresh()
                        }
                    }

                    ServersScreen(
                        uiState = serversState,
                        supported = serversSupported,
                        onQuery = serversViewModel::setQuery,
                        onSetAuto = serversViewModel::setDraftAuto,
                        onToggleProtocol = serversViewModel::toggleProtocol,
                        onToggleDnssec = serversViewModel::toggleDnssec,
                        onToggleNoLog = serversViewModel::toggleNoLog,
                        onToggleNoFilter = serversViewModel::toggleNoFilter,
                        onAdd = serversViewModel::addServer,
                        onRemove = serversViewModel::removeServer,
                        onFocus = serversViewModel::focusServer,
                        onMove = serversViewModel::moveFocused,
                        onDiscard = serversViewModel::discard,
                        onApply = serversViewModel::apply,
                        onRefresh = { serversViewModel.load(force = true) },
                        onOpenReleases = { context.openUrl(ModuleContract.RELEASES_URL) },
                    )
                }

                Destination.SETTINGS -> {
                    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
                    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                    val appSettings by settingsViewModel.appSettings.collectAsStateWithLifecycle()
                    val updateViewModel: ModuleUpdateViewModel = viewModel(factory = factory)
                    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
                    val appliedMessage = stringResource(R.string.settings_applied)
                    val resetDoneText = stringResource(R.string.settings_reset_done)

                    LaunchedEffect(settingsViewModel) {
                        settingsViewModel.messages.collect {
                            snackbarHostState.showSnackbar(resolve(it))
                        }
                    }

                    SettingsScreen(
                        uiState = settingsState,
                        appSettings = appSettings,
                        advancedSupported = conflictsSupported,
                        runGidSupported = runGidSupported,
                        mirrorSupported = mirrorSupported,
                        resetSupported = resetSupported,
                        autostartSupported = autostartSupported,
                        onReset = {
                            settingsViewModel.resetToDefaults {
                                scope.launch { snackbarHostState.showSnackbar(resetDoneText) }
                                statusViewModel.refresh()
                            }
                        },
                        onEdit = settingsViewModel::edit,
                        onDiscard = settingsViewModel::discardEdits,
                        onApply = {
                            settingsViewModel.applyAndRestart {
                                scope.launch { snackbarHostState.showSnackbar(appliedMessage) }
                                statusViewModel.refresh()
                            }
                        },
                        onThemeMode = settingsViewModel::setThemeMode,
                        onDynamicColor = settingsViewModel::setDynamicColor,
                        onPollInterval = settingsViewModel::setPollInterval,
                        updateState = updateState,
                        onCheckUpdate = updateViewModel::check,
                        onInstallUpdate = updateViewModel::install,
                        onReboot = updateViewModel::reboot,
                        onOpenUrl = { context.openUrl(it) },
                    )
                }

                Destination.CONFIG -> {
                    val configViewModel: ConfigViewModel = viewModel(factory = factory)
                    val configState by configViewModel.uiState.collectAsStateWithLifecycle()
                    val savedText = stringResource(R.string.config_saved)
                    val emptyText = stringResource(R.string.config_invalid_empty)
                    val listenText = stringResource(R.string.config_invalid_listen)
                    val restoredText = stringResource(R.string.backup_restored)
                    val backupFormat = stringResource(R.string.backup_created, PLACEHOLDER)

                    LaunchedEffect(configViewModel) {
                        configViewModel.events.collect { event ->
                            val message = when (event) {
                                is ConfigEvent.Saved -> savedText
                                is ConfigEvent.InvalidEmpty -> emptyText
                                is ConfigEvent.InvalidListen -> listenText
                                is ConfigEvent.Restored -> restoredText
                                is ConfigEvent.BackupCreated -> backupFormat.format(event.name)
                                is ConfigEvent.Failed -> resolve(event.message)
                            }
                            snackbarHostState.showSnackbar(message)
                            statusViewModel.refresh()
                        }
                    }

                    ConfigScreen(
                        uiState = configState,
                        onTextChanged = configViewModel::onTextChanged,
                        onSave = configViewModel::saveAndRestart,
                        onBackup = configViewModel::createBackup,
                        onRestore = configViewModel::restore,
                        mirrorSupported = mirrorSupported,
                        onExport = configViewModel::exportConfig,
                        onImport = configViewModel::importConfig,
                    )
                }

                Destination.LOGS -> {
                    val logsViewModel: LogsViewModel = viewModel(factory = factory)
                    val logsState by logsViewModel.uiState.collectAsStateWithLifecycle()
                    val copiedMessage = stringResource(R.string.logs_copied)
                    val shareTitle = stringResource(R.string.logs_share_title)

                    LaunchedEffect(pendingLogTab) {
                        pendingLogTab?.let {
                            logsViewModel.selectTab(it)
                            pendingLogTab = null
                        }
                    }

                    LogsScreen(
                        uiState = logsState,
                        conflictsSupported = conflictsSupported,
                        onSelectTab = logsViewModel::selectTab,
                        onQuery = logsViewModel::setQuery,
                        onFollow = logsViewModel::setFollow,
                        onRefresh = logsViewModel::refresh,
                        onCopy = {
                            context.copyToClipboard(logsViewModel.currentText())
                            scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                        },
                        onShare = { context.shareText(logsViewModel.currentText(), shareTitle) },
                    )
                }
            }
        }
    }
}

/** The %1s slot of backup_created, kept out of the format call itself. */
private const val PLACEHOLDER = "%1" + "$" + "s"

private fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}

private fun Context.copyToClipboard(text: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("dnscrypt-log", text))
}

private fun Context.shareText(text: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
