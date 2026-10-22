package io.github.skofqq.dnscryptmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import io.github.skofqq.dnscryptmanager.ui.components.BarItem
import io.github.skofqq.dnscryptmanager.ui.components.BottomBar
import io.github.skofqq.dnscryptmanager.ui.components.GateScreen
import io.github.skofqq.dnscryptmanager.ui.components.HeaderAction
import io.github.skofqq.dnscryptmanager.ui.components.PageHeader
import io.github.skofqq.dnscryptmanager.ui.theme.Boxy
import io.github.skofqq.dnscryptmanager.ui.update.ModuleUpdateSheet
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

enum class Destination(val labelRes: Int, val iconRes: Int, val titleRes: Int, val subtitleRes: Int) {
    STATUS(R.string.nav_status, R.drawable.ic_shield, R.string.page_status_title, R.string.page_status_sub),
    SERVERS(R.string.nav_servers, R.drawable.ic_dns, R.string.nav_servers, R.string.page_servers_sub),
    SETTINGS(R.string.nav_settings, R.drawable.ic_tune, R.string.nav_settings, R.string.page_settings_sub),
    CONFIG(R.string.nav_config, R.drawable.ic_article, R.string.nav_config, R.string.page_config_sub),
    LOGS(R.string.nav_logs, R.drawable.ic_terminal, R.string.nav_logs, R.string.page_logs_sub),
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

    // Activity-scoped, so the Status card and the Settings row share one check.
    val updateViewModel: ModuleUpdateViewModel = viewModel(factory = factory)
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    var updateSheet by rememberSaveable { mutableStateOf(false) }

    if (updateSheet && !gated) {
        ModuleUpdateSheet(
            state = updateState,
            onDismiss = { updateSheet = false },
            onCheck = updateViewModel::check,
            onInstall = updateViewModel::install,
            onReboot = updateViewModel::reboot,
            onOpenUrl = { context.openUrl(it) },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Boxy.colors.page,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!gated) {
                val items = Destination.entries.map {
                    BarItem(stringResource(it.labelRes), painterResource(it.iconRes))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = 6.dp, bottom = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomBar(
                        items = items,
                        selected = destination.ordinal,
                        onSelect = { destination = Destination.entries[it] },
                    )
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

        Column(Modifier.padding(padding)) {
        PageHeader(
            title = stringResource(destination.titleRes),
            subtitle = stringResource(destination.subtitleRes),
        ) {
            if (destination == Destination.STATUS) {
                HeaderAction(
                    icon = painterResource(R.drawable.ic_refresh),
                    contentDescription = stringResource(R.string.action_refresh),
                    onClick = { statusViewModel.refresh(showSpinner = true) },
                )
            }
            if (destination == Destination.STATUS || destination == Destination.SETTINGS) {
                HeaderAction(
                    icon = painterResource(R.drawable.ic_system_update),
                    contentDescription = stringResource(R.string.update_row_title),
                    tint = if (updateState.updateAvailable) Boxy.colors.accent else Boxy.colors.text,
                    onClick = { updateSheet = true },
                )
            }
        }
        AnimatedContent(
            targetState = destination,
            label = "destination",
            modifier = Modifier.weight(1f),
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
                        updateState = updateState,
                        onOpenUpdate = { updateSheet = true },
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
                        onOpenUpdate = { updateSheet = true },
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
