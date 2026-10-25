package com.skofqq.shieldy.ui.logs

import com.skofqq.shieldy.ui.components.BoxyDialog
import com.skofqq.shieldy.data.LogDomains
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.skofqq.shieldy.R
import com.skofqq.shieldy.data.LevelFilter
import com.skofqq.shieldy.data.LogLevel
import com.skofqq.shieldy.data.LogLine
import com.skofqq.shieldy.ui.components.ActionPill
import com.skofqq.shieldy.ui.components.BoxyChip
import com.skofqq.shieldy.ui.components.HeaderAction
import com.skofqq.shieldy.ui.components.LocalBarPadding
import com.skofqq.shieldy.ui.components.NoticeCard
import com.skofqq.shieldy.ui.components.PageHeader
import com.skofqq.shieldy.ui.components.PageInset
import com.skofqq.shieldy.ui.components.PrimaryPill
import com.skofqq.shieldy.ui.components.SearchField
import com.skofqq.shieldy.ui.theme.Boxy
import com.skofqq.shieldy.ui.theme.MonospaceTextStyle
import com.skofqq.shieldy.ui.theme.Tints
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often a live log is re-read while the screen is open. */
private const val LIVE_INTERVAL_MS = 3_000L

/**
 * Logs in Boxy's layout: source and filter chips under the title, the log in one card
 * with coloured levels, search with a match counter, and a "latest" pill when scrolled up.
 */
@Composable
fun LogsScreen(
    uiState: LogsUiState,
    conflictsSupported: Boolean,
    onSelectTab: (LogTab) -> Unit,
    onQuery: (String) -> Unit,
    onLevel: (LevelFilter) -> Unit,
    onLineCount: (Int) -> Unit,
    onFollow: (Boolean) -> Unit,
    onWrap: (Boolean) -> Unit,
    onOnlyMatches: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onLiveRefresh: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onAddName: (allowed: Boolean, name: String) -> Unit = { _, _ -> },
    onCopyLine: (String) -> Unit = {},
    onRestartNow: () -> Unit = {},
    onDismissNameResult: () -> Unit = {},
) {
    var domainLine by remember { mutableStateOf<LogLine?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lines = uiState.visibleLines
    val matches = uiState.matchIndexes
    var searchOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var matchCursor by remember(uiState.query) { mutableIntStateOf(0) }
    val barPadding = LocalBarPadding.current

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 1
        }
    }

    // Live: keep reading while the tab is open; stop as soon as it is not.
    LaunchedEffect(uiState.follow, uiState.tab) {
        if (!uiState.follow || !uiState.isLog) return@LaunchedEffect
        while (true) {
            delay(LIVE_INTERVAL_MS)
            onLiveRefresh()
        }
    }
    LaunchedEffect(lines.size, uiState.follow) {
        if (uiState.follow && lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    val subtitle = buildString {
        append(stringResource(uiState.tab.labelRes()))
        append(" · ")
        append(
            if (uiState.follow && uiState.isLog) {
                stringResource(R.string.logs_live)
            } else {
                pluralStringResource(R.plurals.logs_lines, lines.size, lines.size)
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        PageHeader(title = stringResource(R.string.nav_logs), subtitle = subtitle) {
            HeaderAction(
                icon = painterResource(R.drawable.ic_search),
                contentDescription = stringResource(R.string.action_search),
                tint = if (searchOpen) Boxy.colors.accent else Boxy.colors.text,
                onClick = {
                    searchOpen = !searchOpen
                    if (!searchOpen) onQuery("")
                },
            )
            HeaderAction(
                icon = painterResource(R.drawable.ic_share),
                contentDescription = stringResource(R.string.action_share),
                enabled = lines.isNotEmpty(),
                onClick = onShare,
            )
            Box {
                HeaderAction(
                    icon = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.action_more),
                    onClick = { menuOpen = true },
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = Boxy.colors.card,
                ) {
                    MenuItem(R.drawable.ic_copy, stringResource(R.string.logs_copy_all), enabled = lines.isNotEmpty()) {
                        menuOpen = false
                        onCopy()
                    }
                    MenuItem(R.drawable.ic_download, stringResource(R.string.logs_save), enabled = lines.isNotEmpty()) {
                        menuOpen = false
                        onSave()
                    }
                    MenuItem(R.drawable.ic_refresh, stringResource(R.string.action_refresh)) {
                        menuOpen = false
                        onRefresh()
                    }
                    MenuItem(R.drawable.ic_wrap, stringResource(R.string.logs_wrap), checked = uiState.wrap) {
                        onWrap(!uiState.wrap)
                    }
                    MenuItem(
                        R.drawable.ic_filter,
                        stringResource(R.string.logs_only_matches),
                        checked = uiState.onlyMatches,
                        enabled = uiState.query.isNotBlank(),
                    ) { onOnlyMatches(!uiState.onlyMatches) }
                }
            }
        }

        // The conflicts tab only exists from module 2.1.18-r3 on.
        val tabs = remember(conflictsSupported) {
            LogTab.entries.filter { it != LogTab.CONFLICTS || conflictsSupported }
        }
        ChipRow {
            tabs.forEach { tab ->
                item { BoxyChip(stringResource(tab.chipRes()), uiState.tab == tab) { onSelectTab(tab) } }
            }
        }
        if (uiState.isLog) {
            Spacer(Modifier.height(8.dp))
            ChipRow {
                item { LevelChip(uiState.level, onLevel) }
                item { LinesChip(uiState.lineCount, onLineCount) }
                item {
                    BoxyChip(
                        text = stringResource(R.string.logs_live),
                        active = uiState.follow,
                        color = Tints.green.fg,
                        onClick = { onFollow(!uiState.follow) },
                    )
                }
            }
        }

        if (searchOpen) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PageInset).padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    query = uiState.query,
                    hint = stringResource(R.string.action_search),
                    onQuery = onQuery,
                    modifier = Modifier.weight(1f),
                    trailingText = when {
                        uiState.query.isBlank() -> null
                        matches.isEmpty() -> stringResource(R.string.config_no_matches)
                        else -> stringResource(R.string.config_match_count, matchCursor + 1, matches.size)
                    },
                )
                if (uiState.query.isNotBlank()) {
                    JumpButton(R.drawable.ic_arrow_up, R.string.action_previous_match, matches.isNotEmpty()) {
                        matchCursor = (matchCursor + matches.size - 1) % matches.size
                        onFollow(false)
                        scope.launch { listState.animateScrollToItem(matches[matchCursor]) }
                    }
                    JumpButton(R.drawable.ic_arrow_down, R.string.action_next_match, matches.isNotEmpty()) {
                        matchCursor = (matchCursor + 1) % matches.size
                        onFollow(false)
                        scope.launch { listState.animateScrollToItem(matches[matchCursor]) }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        if (uiState.loading) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = PageInset + 8.dp, vertical = 4.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.error != null -> NoticeCard(
                    title = stringResource(R.string.logs_error_title),
                    body = uiState.error,
                    tint = Tints.red,
                    icon = painterResource(R.drawable.ic_warning),
                    modifier = Modifier.padding(horizontal = PageInset),
                ) {
                    ActionPill(stringResource(R.string.action_refresh), Tints.red, true, Modifier.fillMaxWidth(), onRefresh)
                }

                lines.isEmpty() && !uiState.loading -> NoticeCard(
                    title = stringResource(R.string.logs_empty_title),
                    body = stringResource(
                        if (uiState.lines.isEmpty()) uiState.tab.emptyRes() else R.string.logs_filtered_out
                    ),
                    tint = Tints.gray,
                    icon = painterResource(R.drawable.ic_article),
                    modifier = Modifier.padding(horizontal = PageInset),
                ) {
                    ActionPill(stringResource(R.string.action_refresh), Tints.blue, true, Modifier.fillMaxWidth(), onRefresh)
                }

                else -> PullToRefreshBox(
                    isRefreshing = uiState.loading && !uiState.follow,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LogCard(
                        lines = lines,
                        isLog = uiState.isLog,
                        query = uiState.query,
                        wrap = uiState.wrap,
                        listState = listState,
                        bottomPadding = barPadding,
                        onLongPress = if (uiState.isLog) { line -> domainLine = line } else null,
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !atBottom && lines.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = PageInset + 8.dp, bottom = barPadding),
            ) {
                PrimaryPill(
                    text = stringResource(R.string.logs_latest),
                    modifier = Modifier.width(140.dp),
                    onClick = {
                        scope.launch { listState.animateScrollToItem(lines.lastIndex) }
                        if (uiState.isLog) onFollow(true)
                    },
                )
            }
        }
    }

    domainLine?.let { line ->
        DomainDialog(line, onAddName, onCopyLine, onDismiss = { domainLine = null })
    }
    uiState.nameResult?.let { result ->
        NameResultDialog(result, onRestartNow, onDismissNameResult)
    }
}

@Composable
private fun ChipRow(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = PageInset),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun LevelChip(level: LevelFilter, onLevel: (LevelFilter) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BoxyChip(
            text = stringResource(level.labelRes()),
            active = level != LevelFilter.ALL,
            color = Tints.amber.fg,
            trailing = painterResource(R.drawable.ic_expand),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp), containerColor = Boxy.colors.card) {
            LevelFilter.entries.forEach { option ->
                MenuItem(null, stringResource(option.labelRes()), checked = option == level) {
                    open = false
                    onLevel(option)
                }
            }
        }
    }
}

@Composable
private fun LinesChip(count: Int, onLineCount: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BoxyChip(
            text = stringResource(R.string.logs_last_n, count),
            active = false,
            trailing = painterResource(R.drawable.ic_expand),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp), containerColor = Boxy.colors.card) {
            LogsUiState.LINE_CHOICES.forEach { option ->
                MenuItem(null, stringResource(R.string.logs_last_n, option), checked = option == count) {
                    open = false
                    onLineCount(option)
                }
            }
        }
    }
}

/** The log itself: one card, monospace, coloured by level, wrapped or scrolled sideways. */
@Composable
private fun LogCard(
    lines: List<LogLine>,
    isLog: Boolean,
    query: String,
    wrap: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onLongPress: ((LogLine) -> Unit)? = null,
) {
    val palette = logPalette()
    val haptics = LocalHapticFeedback.current
    val horizontal = rememberScrollState()
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = PageInset)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Boxy.colors.card)
            .then(if (wrap) Modifier else Modifier.horizontalScroll(horizontal)),
    ) {
        // Log lines answer a long press with their domains, which text selection would
        // swallow; command output keeps selection. Copy stays in the menu for both.
        val list: @Composable () -> Unit = {
            LazyColumn(
                state = listState,
                modifier = if (wrap) {
                    Modifier.fillMaxSize()
                } else {
                    // A lazy list needs a bounded width inside a horizontal scroll.
                    Modifier.width(1600.dp).fillMaxHeight()
                },
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        text = if (isLog) colourLog(line, query, palette) else colourCommand(line.raw, query, palette),
                        style = MonospaceTextStyle,
                        softWrap = wrap,
                        modifier = if (onLongPress == null) {
                            Modifier
                        } else {
                            Modifier.fillMaxWidth().pointerInput(line) {
                                detectTapGestures(onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLongPress(line)
                                })
                            }
                        },
                    )
                }
            }
        }
        if (onLongPress == null) SelectionContainer { list() } else list()
    }
}

/** Domains of one log line, each with Block and Allow; plus copying the line itself. */
@Composable
private fun DomainDialog(
    line: LogLine,
    onAddName: (allowed: Boolean, name: String) -> Unit,
    onCopyLine: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val domains = remember(line) { LogDomains.extract(line.raw) }
    BoxyDialog(
        title = stringResource(R.string.logs_domain_title),
        text = if (domains.isEmpty()) stringResource(R.string.logs_no_domain) else null,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.action_cancel),
        confirmText = stringResource(R.string.logs_copy_line),
        onConfirm = {
            onCopyLine(line.raw)
            onDismiss()
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            domains.take(MAX_DOMAINS).forEach { domain ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Boxy.colors.surface2).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(domain, style = MonospaceTextStyle, color = Boxy.colors.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionPill(stringResource(R.string.logs_block), Tints.red, true, Modifier.weight(1f)) {
                            onAddName(false, domain)
                            onDismiss()
                        }
                        ActionPill(stringResource(R.string.logs_allow), Tints.green, true, Modifier.weight(1f)) {
                            onAddName(true, domain)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

/** What adding a name did, and the restart offer when the proxy runs. */
@Composable
private fun NameResultDialog(result: NameResult, onRestartNow: () -> Unit, onDismiss: () -> Unit) {
    val title = when {
        !result.added -> stringResource(R.string.logs_already, result.name)
        result.allowed -> stringResource(R.string.logs_added_allowed, result.name)
        else -> stringResource(R.string.logs_added_blocked, result.name)
    }
    if (result.added && result.running) {
        BoxyDialog(
            title = title,
            text = stringResource(R.string.blocklists_restart_body),
            onDismiss = onDismiss,
            dismissText = stringResource(R.string.update_later),
            confirmText = stringResource(R.string.exclusions_restart_now),
            onConfirm = {
                onRestartNow()
                onDismiss()
            },
        )
    } else {
        BoxyDialog(
            title = title,
            text = if (result.added) stringResource(R.string.saved_for_next_start) else null,
            onDismiss = onDismiss,
            confirmText = stringResource(R.string.action_ok),
            onConfirm = onDismiss,
        )
    }
}

private const val MAX_DOMAINS = 4


private data class LogPalette(
    val text: Color,
    val dim: Color,
    val info: Color,
    val notice: Color,
    val warning: Color,
    val error: Color,
    val accent: Color,
    val highlight: Color,
)

@Composable
private fun logPalette() = LogPalette(
    text = Boxy.colors.text,
    dim = Boxy.colors.text2,
    info = Tints.blue.fg,
    notice = Tints.teal.fg,
    warning = Tints.amber.fg,
    error = Tints.red.fg,
    accent = Tints.purple.fg,
    highlight = Tints.amber.bg,
)

private fun colourLog(line: LogLine, query: String, p: LogPalette): AnnotatedString = buildAnnotatedString {
    line.timestamp?.let { withStyle(SpanStyle(color = p.dim)) { append(it); append(' ') } }
    line.levelText?.let {
        val colour = when (line.level) {
            LogLevel.DEBUG -> p.dim
            LogLevel.INFO -> p.info
            LogLevel.NOTICE -> p.notice
            LogLevel.WARNING -> p.warning
            LogLevel.ERROR, null -> p.error
        }
        withStyle(SpanStyle(color = colour, fontWeight = FontWeight.SemiBold)) { append(it.uppercase()) }
        append(' ')
    }
    val messageColour = when (line.level) {
        LogLevel.ERROR -> p.error
        LogLevel.WARNING -> p.warning
        else -> p.text
    }
    appendHighlighted(line.message, query, messageColour, p.highlight)
}

/** Rules and conflicts: headings bold, our chain in accent, box_for_magisk in amber. */
private fun colourCommand(raw: String, query: String, p: LogPalette): AnnotatedString = buildAnnotatedString {
    val trimmed = raw.trim()
    val colour = when {
        trimmed.startsWith("---") -> p.text
        raw.contains("DNSCRYPT") -> p.accent
        raw.contains("box", ignoreCase = true) || raw.contains("clash") || raw.contains("sing-box") -> p.warning
        else -> p.text
    }
    if (trimmed.startsWith("---")) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colour)) { append(trimmed.trim('-', ' ')) }
    } else {
        appendHighlighted(raw, query, colour, p.highlight)
    }
}

private fun AnnotatedString.Builder.appendHighlighted(text: String, query: String, colour: Color, highlight: Color) {
    if (query.isBlank()) {
        withStyle(SpanStyle(color = colour)) { append(text) }
        return
    }
    var start = 0
    while (true) {
        val hit = text.indexOf(query, start, ignoreCase = true)
        if (hit < 0) break
        withStyle(SpanStyle(color = colour)) { append(text.substring(start, hit)) }
        withStyle(SpanStyle(color = colour, background = highlight, fontWeight = FontWeight.Bold)) {
            append(text.substring(hit, hit + query.length))
        }
        start = hit + query.length
    }
    withStyle(SpanStyle(color = colour)) { append(text.substring(start)) }
}

@Composable
private fun MenuItem(icon: Int?, text: String, checked: Boolean? = null, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = Boxy.colors.text.copy(alpha = if (enabled) 1f else 0.4f)) },
        leadingIcon = icon?.let { { Icon(painterResource(it), null, tint = Boxy.colors.text) } },
        trailingIcon = if (checked == true) {
            { Icon(painterResource(R.drawable.ic_check), null, tint = Boxy.colors.accent) }
        } else {
            null
        },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun JumpButton(icon: Int, description: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = stringResource(description), tint = Boxy.colors.text)
    }
}

private fun LogTab.labelRes() = when (this) {
    LogTab.MODULE -> R.string.logs_tab_module
    LogTab.PROXY -> R.string.logs_tab_proxy
    LogTab.RULES -> R.string.logs_tab_rules
    LogTab.CONFLICTS -> R.string.logs_tab_conflicts
}

/** Short chip labels; the file name goes into the subtitle. */
private fun LogTab.chipRes() = when (this) {
    LogTab.MODULE -> R.string.logs_chip_module
    LogTab.PROXY -> R.string.logs_chip_proxy
    LogTab.RULES -> R.string.logs_tab_rules
    LogTab.CONFLICTS -> R.string.logs_tab_conflicts
}

private fun LogTab.emptyRes() = when (this) {
    LogTab.MODULE -> R.string.logs_empty_module
    LogTab.PROXY -> R.string.logs_empty_proxy
    LogTab.RULES, LogTab.CONFLICTS -> R.string.logs_empty
}

private fun LevelFilter.labelRes() = when (this) {
    LevelFilter.ALL -> R.string.logs_level_all
    LevelFilter.INFO -> R.string.logs_level_info
    LevelFilter.WARNING -> R.string.logs_level_warning
    LevelFilter.ERROR -> R.string.logs_level_error
}
