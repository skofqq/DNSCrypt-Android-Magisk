package io.github.skofqq.dnscryptmanager.ui

import androidx.compose.foundation.layout.ime
import io.github.skofqq.dnscryptmanager.ui.status.ProxyAction
import io.github.skofqq.dnscryptmanager.ui.components.notify
import io.github.skofqq.dnscryptmanager.ui.components.NoticeKind
import io.github.skofqq.dnscryptmanager.ui.components.LocalSnackbarLift
import io.github.skofqq.dnscryptmanager.ui.components.BoxySnackbarHost
import io.github.skofqq.dnscryptmanager.ui.blocklists.BlocklistsScreen
import io.github.skofqq.dnscryptmanager.ui.blocklists.BlocklistsViewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
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
import io.github.skofqq.dnscryptmanager.ui.components.LocalBarPadding
import io.github.skofqq.dnscryptmanager.ui.components.PageHeader
import io.github.skofqq.dnscryptmanager.ui.components.TopFade
import io.github.skofqq.dnscryptmanager.ui.config.ConfigEvent
import io.github.skofqq.dnscryptmanager.ui.config.ConfigScreen
import io.github.skofqq.dnscryptmanager.ui.config.ConfigViewModel
import io.github.skofqq.dnscryptmanager.ui.logs.LogTab
import io.github.skofqq.dnscryptmanager.ui.logs.LogsScreen
import io.github.skofqq.dnscryptmanager.ui.logs.LogsViewModel
import io.github.skofqq.dnscryptmanager.ui.servers.ServersScreen
import io.github.skofqq.dnscryptmanager.ui.servers.ServersViewModel
import io.github.skofqq.dnscryptmanager.ui.settings.SettingsScreen
import io.github.skofqq.dnscryptmanager.ui.settings.SettingsViewModel
import io.github.skofqq.dnscryptmanager.ui.status.StatusScreen
import io.github.skofqq.dnscryptmanager.ui.status.StatusViewModel
import io.github.skofqq.dnscryptmanager.ui.theme.Boxy
import io.github.skofqq.dnscryptmanager.ui.update.ModuleUpdateSheet
import io.github.skofqq.dnscryptmanager.ui.update.ModuleUpdateViewModel
import kotlinx.coroutines.launch

/** The three tabs of the bottom bar, as in Boxy: home, settings, log. */
enum class Destination(val labelRes: Int, val iconRes: Int) {
    STATUS(R.string.nav_status, R.drawable.ic_shield),
    SETTINGS(R.string.nav_settings, R.drawable.ic_tune),
    LOGS(R.string.nav_logs, R.drawable.ic_terminal),
}

/** Pages opened from Settings, shown full screen with a back pill and no bottom bar. */
enum class SubPage { SERVERS, CONFIG, BLOCKLISTS }

/** Height of the floating bar plus its margins, without the system navigation bar. */
private val BarHeight = 68.dp + 6.dp + 10.dp

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val factory = remember(context) { AppViewModelFactory(context.appContainer) }
    val statusViewModel: StatusViewModel = viewModel(factory = factory)
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()

    var destination by rememberSaveable { mutableStateOf(Destination.STATUS) }
    var subPage by rememberSaveable { mutableStateOf<SubPage?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Raised by screens with their own bottom bar (Settings' unsaved changes) so notices clear it.
    val snackbarLift = remember { mutableStateOf(0.dp) }
    val scope = rememberCoroutineScope()

    val haptics = LocalHapticFeedback.current

    // Resource lookups belong in composable scope; effects only see the finished text.
    val timeoutText = stringResource(R.string.error_timeout)
    val noRootText = stringResource(R.string.error_no_root)
    val savedForNextStartText = stringResource(R.string.saved_for_next_start)
    val resolve: (UiMessage) -> String = remember(timeoutText, noRootText, savedForNextStartText) {
        { message ->
            when (message) {
                is UiMessage.Raw -> message.text
                is UiMessage.Timeout -> timeoutText
                is UiMessage.NoRoot -> noRootText
                is UiMessage.SavedForNextStart -> savedForNextStartText
            }
        }
    }

    // Every message from the view models goes through here: failures as errors, a change
    // saved while the proxy is stopped as information with Start, which only the user presses.
    val startLabel = stringResource(R.string.action_start)
    val notifyMessage: suspend (UiMessage) -> Unit = { message ->
        when (message) {
            is UiMessage.SavedForNextStart ->
                snackbarHostState.notify(savedForNextStartText, NoticeKind.INFO, startLabel) {
                    statusViewModel.run(ProxyAction.START)
                }
            is UiMessage.Raw -> snackbarHostState.notify(message.text, if (message.error) NoticeKind.ERROR else NoticeKind.INFO)
            else -> snackbarHostState.notify(resolve(message), NoticeKind.ERROR)
        }
    }

    LaunchedEffect(statusViewModel) {
        statusViewModel.messages.collect { notifyMessage(it) }
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

    // The Check button opens the DNS check sheet; the view model outlives the sheet so a
    // finished leak test is still there when it is opened again.
    val dnsCheckViewModel: io.github.skofqq.dnscryptmanager.ui.status.DnsCheckViewModel = viewModel(factory = factory)
    val dnsCheck by dnsCheckViewModel.state.collectAsStateWithLifecycle()
    if (dnsCheck.open && !gated) {
        io.github.skofqq.dnscryptmanager.ui.status.DnsCheckSheet(
            state = dnsCheck,
            onDismiss = dnsCheckViewModel::close,
            onLeakTest = dnsCheckViewModel::runLeakTest,
            onSelfTest = statusViewModel::runModuleTest,
        )
    }

    BackHandler(enabled = subPage != null) { subPage = null }

    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Content runs under the floating bar; this is how far its last item must clear it.
    val barPadding = if (subPage == null) BarHeight + navBar + 16.dp else navBar + 16.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(Boxy.colors.page),
    ) {
        if (gated) {
            GateScreen(
                state = statusState.state,
                onRetry = statusViewModel::retryRoot,
                onOpenReleases = { context.openUrl(ModuleContract.RELEASES_URL) },
                modifier = Modifier.statusBarsPadding(),
            )
            return@Box
        }

        CompositionLocalProvider(LocalBarPadding provides barPadding, LocalSnackbarLift provides snackbarLift) {
            AnimatedContent(
                targetState = subPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "subPage",
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
            ) { page ->
                when (page) {
                    SubPage.SERVERS -> {
                        val serversViewModel: ServersViewModel = viewModel(factory = factory)
                        val serversState by serversViewModel.uiState.collectAsStateWithLifecycle()
                        val appliedText = stringResource(R.string.servers_applied)

                        LaunchedEffect(serversViewModel) {
                            serversViewModel.messages.collect {
                                notifyMessage(it)
                            }
                        }
                        LaunchedEffect(serversViewModel) {
                            serversViewModel.applied.collect {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                snackbarHostState.notify(appliedText, NoticeKind.SUCCESS)
                                statusViewModel.refresh()
                            }
                        }

                        ServersScreen(
                            uiState = serversState,
                            supported = serversSupported,
                            onBack = { subPage = null },
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
                            onExclude = serversViewModel::excludeServer,
                            onInclude = serversViewModel::includeServer,
                            onSetCountries = serversViewModel::setCountryRules,
                            onRestartNow = serversViewModel::restartNow,
                            onDismissRestart = serversViewModel::dismissRestartOffer,
                            onToggleExcludedOnly = serversViewModel::toggleExcludedOnly,
                            onAssignCountry = serversViewModel::assignCountry,
                            onOpenReleases = { context.openUrl(ModuleContract.RELEASES_URL) },
                        )
                    }

                    SubPage.CONFIG -> {
                        val configViewModel: ConfigViewModel = viewModel(factory = factory)
                        val configState by configViewModel.uiState.collectAsStateWithLifecycle()
                        val savedText = stringResource(R.string.config_saved)
                        val emptyText = stringResource(R.string.config_invalid_empty)
                        val listenText = stringResource(R.string.config_invalid_listen)
                        val restoredText = stringResource(R.string.backup_restored)
                        val backupFormat = stringResource(R.string.backup_created, PLACEHOLDER)

                        LaunchedEffect(configViewModel) {
                            configViewModel.events.collect { event ->
                                val (message, kind) = when (event) {
                                    is ConfigEvent.Saved -> savedText to NoticeKind.SUCCESS
                                    is ConfigEvent.InvalidEmpty -> emptyText to NoticeKind.WARNING
                                    is ConfigEvent.InvalidListen -> listenText to NoticeKind.WARNING
                                    is ConfigEvent.Restored -> restoredText to NoticeKind.SUCCESS
                                    is ConfigEvent.BackupCreated -> backupFormat.format(event.name) to NoticeKind.SUCCESS
                                    is ConfigEvent.Failed -> resolve(event.message) to NoticeKind.ERROR
                                }
                                snackbarHostState.notify(message, kind)
                                statusViewModel.refresh()
                            }
                        }

                        ConfigScreen(
                            uiState = configState,
                            onBack = { subPage = null },
                            onTextChanged = configViewModel::onTextChanged,
                            onSave = configViewModel::saveAndRestart,
                            onBackup = configViewModel::createBackup,
                            onRestore = configViewModel::restore,
                            mirrorSupported = mirrorSupported,
                            onExport = configViewModel::exportConfig,
                            onImport = configViewModel::importConfig,
                        )
                    }

                    SubPage.BLOCKLISTS -> {
                        val blocklistsViewModel: BlocklistsViewModel = viewModel(factory = factory)
                        val blocklistsState by blocklistsViewModel.uiState.collectAsStateWithLifecycle()
                        LaunchedEffect(blocklistsViewModel) {
                            blocklistsViewModel.messages.collect { notifyMessage(it) }
                        }
                        BlocklistsScreen(
                            uiState = blocklistsState,
                            onBack = { subPage = null },
                            onToggle = blocklistsViewModel::setEnabled,
                            onAdd = blocklistsViewModel::add,
                            onRemove = blocklistsViewModel::remove,
                            onUpdateNow = blocklistsViewModel::updateNow,
                            onAutoUpdate = blocklistsViewModel::setAutoUpdate,
                            onUnmeteredOnly = blocklistsViewModel::setUnmeteredOnly,
                            onChargingOnly = blocklistsViewModel::setChargingOnly,
                            onRestartNow = {
                                blocklistsViewModel.restartNow()
                                statusViewModel.refresh()
                            },
                            onDismissRestart = blocklistsViewModel::dismissRestart,
                        )
                    }

                    null -> AnimatedContent(
                        targetState = destination,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "destination",
                        modifier = Modifier.fillMaxSize(),
                    ) { current ->
                        when (current) {
                            Destination.STATUS -> TabPage(
                                title = stringResource(R.string.page_status_title),
                                subtitle = stringResource(R.string.page_status_sub),
                                actions = {
                                    HeaderAction(
                                        icon = painterResource(R.drawable.ic_refresh),
                                        contentDescription = stringResource(R.string.action_refresh),
                                        onClick = { statusViewModel.refresh(showSpinner = true) },
                                    )
                                    HeaderAction(
                                        icon = painterResource(R.drawable.ic_system_update),
                                        contentDescription = stringResource(R.string.update_row_title),
                                        tint = if (updateState.updateAvailable) Boxy.colors.accent else Boxy.colors.text,
                                        dot = updateState.updateAvailable,
                                        onClick = { updateSheet = true },
                                    )
                                },
                            ) {
                                LifecycleResumeEffect(Unit) {
                                    statusViewModel.startPolling()
                                    onPauseOrDispose { statusViewModel.stopPolling() }
                                }
                                StatusScreen(
                                    uiState = statusState,
                                    onRefresh = { statusViewModel.refresh(showSpinner = true) },
                                    onAction = statusViewModel::run,
                                    onProbe = { readyStatus?.let(dnsCheckViewModel::open) },
                                    onModuleTest = statusViewModel::runModuleTest,
                                    onOpenLogs = { destination = Destination.LOGS },
                                    onOpenConflicts = {
                                        pendingLogTab = LogTab.CONFLICTS
                                        destination = Destination.LOGS
                                    },
                                    updateState = updateState,
                                    onOpenUpdate = { updateSheet = true },
                                    dnsCheck = dnsCheck,
                                    onOpenServers = { subPage = SubPage.SERVERS },
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
                                        notifyMessage(it)
                                    }
                                }

                                val chosen = readyStatus?.serverSelection.orEmpty()
                                val blocklistSettings by context.appContainer.blocklists.settings.collectAsStateWithLifecycle()
                                val blocklistsSummary = if (blocklistSettings.enabledCount == 0) {
                                    stringResource(R.string.blocklists_none_enabled)
                                } else {
                                    stringResource(
                                        R.string.blocklists_summary,
                                        blocklistSettings.enabledCount,
                                        java.text.NumberFormat.getIntegerInstance().format(blocklistSettings.mergedEntries ?: 0),
                                    )
                                }
                                TabPage(
                                    title = stringResource(R.string.nav_settings),
                                    subtitle = stringResource(R.string.page_settings_sub),
                                    actions = {
                                        HeaderAction(
                                            icon = painterResource(R.drawable.ic_system_update),
                                            contentDescription = stringResource(R.string.update_row_title),
                                            tint = if (updateState.updateAvailable) Boxy.colors.accent else Boxy.colors.text,
                                            dot = updateState.updateAvailable,
                                            onClick = { updateSheet = true },
                                        )
                                    },
                                ) {
                                    SettingsScreen(
                                        uiState = settingsState,
                                        appSettings = appSettings,
                                        serversSupported = serversSupported,
                                        serversSummary = if (chosen.isEmpty()) {
                                            stringResource(R.string.servers_mode_auto)
                                        } else {
                                            pluralStringResource(R.plurals.servers_chosen_count, chosen.size, chosen.size)
                                        },
                                        advancedSupported = conflictsSupported,
                                        runGidSupported = runGidSupported,
                                        mirrorSupported = mirrorSupported,
                                        resetSupported = resetSupported,
                                        autostartSupported = autostartSupported,
                                        onOpenServers = { subPage = SubPage.SERVERS },
                                        onOpenConfig = { subPage = SubPage.CONFIG },
                                        onOpenBlocklists = { subPage = SubPage.BLOCKLISTS },
                                        blocklistsSummary = blocklistsSummary,
                                        onReset = {
                                            settingsViewModel.resetToDefaults {
                                                scope.launch { snackbarHostState.notify(resetDoneText, NoticeKind.SUCCESS) }
                                                statusViewModel.refresh()
                                            }
                                        },
                                        onEdit = settingsViewModel::edit,
                                        onDiscard = settingsViewModel::discardEdits,
                                        onApply = {
                                            settingsViewModel.applyAndRestart {
                                                scope.launch { snackbarHostState.notify(appliedMessage, NoticeKind.SUCCESS) }
                                                statusViewModel.refresh()
                                            }
                                        },
                                        onThemeMode = settingsViewModel::setThemeMode,
                                        onDynamicColor = settingsViewModel::setDynamicColor,
                                        onPollInterval = settingsViewModel::setPollInterval,
                                        updateState = updateState,
                                        onOpenUpdate = { updateSheet = true },
                                        onShown = settingsViewModel::refreshGroup,
                                    )
                                }
                            }

                            Destination.LOGS -> {
                                val logsViewModel: LogsViewModel = viewModel(factory = factory)
                                val logsState by logsViewModel.uiState.collectAsStateWithLifecycle()
                                val copiedMessage = stringResource(R.string.logs_copied)
                                val savedFormat = stringResource(R.string.logs_saved, PLACEHOLDER)
                                val saveFailed = stringResource(R.string.logs_save_failed)
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
                                    onLevel = logsViewModel::setLevel,
                                    onLineCount = logsViewModel::setLineCount,
                                    onFollow = logsViewModel::setFollow,
                                    onWrap = logsViewModel::setWrap,
                                    onOnlyMatches = logsViewModel::setOnlyMatches,
                                    onRefresh = { logsViewModel.refresh() },
                                    onLiveRefresh = logsViewModel::liveRefresh,
                                    onAddName = logsViewModel::addName,
                                    onCopyLine = { text ->
                                        context.copyToClipboard(text)
                                        scope.launch { snackbarHostState.notify(copiedMessage, NoticeKind.SUCCESS) }
                                    },
                                    onRestartNow = {
                                        logsViewModel.restartNow()
                                        statusViewModel.refresh()
                                    },
                                    onDismissNameResult = logsViewModel::dismissNameResult,
                                    onCopy = {
                                        context.copyToClipboard(logsViewModel.currentText())
                                        scope.launch { snackbarHostState.notify(copiedMessage, NoticeKind.SUCCESS) }
                                    },
                                    onShare = { context.shareText(logsViewModel.currentText(), shareTitle) },
                                    onSave = {
                                        val name = logsViewModel.exportFileName()
                                        val ok = context.saveToDownloads(name, logsViewModel.currentText())
                                        scope.launch {
                                            if (ok) {
                                                snackbarHostState.notify(savedFormat.format(name), NoticeKind.SUCCESS)
                                            } else {
                                                snackbarHostState.notify(saveFailed, NoticeKind.ERROR)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (subPage == null) {
                val items = Destination.entries.map {
                    BarItem(stringResource(it.labelRes), painterResource(it.iconRes))
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = navBar + 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomBar(
                        items = items,
                        selected = destination.ordinal,
                        onSelect = { destination = Destination.entries[it] },
                        modifier = Modifier.fillMaxWidth(0.80f),
                    )
                }
            }
        }

        // Above the keyboard when it is open (the config editor), otherwise above the bars.
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        BoxySnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = maxOf(barPadding + snackbarLift.value, imeBottom)),
        )
    }
}

/** A tab: the large page title with round actions, then content that scrolls under a soft fade. */
@Composable
private fun TabPage(
    title: String,
    subtitle: String?,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(title = title, subtitle = subtitle, actions = actions)
        Box(Modifier.weight(1f)) {
            content()
            TopFade()
        }
    }
}

/** The %1s slot of a format string, kept out of the format call itself. */
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

/** Writes [text] to Downloads through MediaStore: no storage permission needed from Android 10 on. */
private fun Context.saveToDownloads(name: String, text: String): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
    }
    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: return false
    true
}.getOrDefault(false)
