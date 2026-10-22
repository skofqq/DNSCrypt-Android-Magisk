package io.github.skofqq.dnscryptmanager.ui.logs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.ui.theme.MonospaceTextStyle

@Composable
fun LogsScreen(
    uiState: LogsUiState,
    conflictsSupported: Boolean,
    onSelectTab: (LogTab) -> Unit,
    onQuery: (String) -> Unit,
    onFollow: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val lines = uiState.visibleLines

    LaunchedEffect(lines.size, uiState.follow) {
        if (uiState.follow && lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // The conflicts tab only exists from module 2.1.18-r3 on.
        val tabs = remember(conflictsSupported) {
            LogTab.entries.filter { it != LogTab.CONFLICTS || conflictsSupported }
        }
        PrimaryTabRow(selectedTabIndex = tabs.indexOf(uiState.tab).coerceAtLeast(0)) {
            tabs.forEach { tab ->
                Tab(
                    selected = uiState.tab == tab,
                    onClick = { onSelectTab(tab) },
                    text = {
                        Text(
                            text = stringResource(tab.labelRes()),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQuery,
                singleLine = true,
                label = { Text(stringResource(R.string.action_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.action_clear),
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    painterResource(R.drawable.ic_refresh),
                    contentDescription = stringResource(R.string.action_refresh),
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    painter = painterResource(R.drawable.ic_article),
                    contentDescription = stringResource(R.string.action_copy),
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    painterResource(R.drawable.ic_share),
                    contentDescription = stringResource(R.string.action_share),
                )
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = uiState.follow,
                onClick = { onFollow(!uiState.follow) },
                label = { Text(stringResource(R.string.logs_autoscroll)) },
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (uiState.query.isBlank()) {
                    pluralStringResource(R.plurals.logs_lines, lines.size, lines.size)
                } else {
                    pluralStringResource(R.plurals.logs_matches, lines.size, lines.size)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (uiState.loading) {
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        if (uiState.error != null) {
            Text(
                text = stringResource(R.string.error_prefix, uiState.error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (lines.isEmpty() && !uiState.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.logs_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    itemsIndexed(lines) { _, line ->
                        Text(
                            text = line,
                            style = MonospaceTextStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = true,
                        )
                    }
                }
            }
        }
    }
}

private fun LogTab.labelRes() = when (this) {
    LogTab.MODULE -> R.string.logs_tab_module
    LogTab.PROXY -> R.string.logs_tab_proxy
    LogTab.RULES -> R.string.logs_tab_rules
    LogTab.CONFLICTS -> R.string.logs_tab_conflicts
}
