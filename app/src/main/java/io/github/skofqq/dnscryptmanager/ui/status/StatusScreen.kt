package io.github.skofqq.dnscryptmanager.ui.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.data.Ipv6Dns
import io.github.skofqq.dnscryptmanager.data.ModuleState
import io.github.skofqq.dnscryptmanager.data.ModuleStatus
import io.github.skofqq.dnscryptmanager.dns.DnsProbe
import io.github.skofqq.dnscryptmanager.dns.ProbeResult
import io.github.skofqq.dnscryptmanager.ui.components.InfoRow
import io.github.skofqq.dnscryptmanager.ui.components.ProxyPhase
import io.github.skofqq.dnscryptmanager.ui.components.ProxyPhaseBadge
import io.github.skofqq.dnscryptmanager.ui.components.phaseColor

@Composable
fun StatusScreen(
    uiState: StatusUiState,
    onRefresh: () -> Unit,
    onAction: (ProxyAction) -> Unit,
    onProbe: () -> Unit,
    onModuleTest: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenConflicts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = uiState.state as? ModuleState.Ready ?: return
    val status = ready.status
    val phase = phaseOf(status)
    var confirmStop by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = uiState.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProxyPhaseBadge(phase = phase)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(phase.titleRes()),
                        style = MaterialTheme.typography.displaySmall,
                        color = phaseColor(phase),
                    )
                    if (!status.ipv6Bypass) {
                        Text(
                            text = stringResource(phase.hintRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (phase == ProxyPhase.RESTARTING) {
                        TextButton(onClick = onOpenLogs) {
                            Text(stringResource(R.string.status_open_logs))
                        }
                    }
                    // Intercept handed to box on purpose: this is a working setup, not a fault.
                    if (uiState.boxDetected == true && !status.rulesV4) {
                        Text(
                            text = stringResource(R.string.status_box_handover),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    ProxyActions(
                        phase = phase,
                        busyAction = uiState.busyAction,
                        onStart = { onAction(ProxyAction.START) },
                        onStop = { confirmStop = true },
                        onRestart = { onAction(ProxyAction.RESTART) },
                    )

                    AnimatedVisibility(visible = uiState.busyAction != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(16.dp))
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.working),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (status.ipv6Bypass) {
                Ipv6BypassCard()
            }

            if (uiState.boxDetected == true && status.rulesV4) {
                BoxConflictCard(onOpenConflicts = onOpenConflicts)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    StatusDetails(status = status, uptimeSeconds = ready.uptimeSeconds)
                }
            }

            DnsCheckCard(
                port = status.port,
                probing = uiState.probing,
                probe = uiState.probe,
                onProbe = onProbe,
                onModuleTest = onModuleTest,
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.confirm_stop_title)) },
            text = { Text(stringResource(R.string.confirm_stop_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    onAction(ProxyAction.STOP)
                }) { Text(stringResource(R.string.action_stop)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProxyActions(
    phase: ProxyPhase,
    busyAction: ProxyAction?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
) {
    val busy = busyAction != null
    val running = phase != ProxyPhase.STOPPED
    // The ButtonGroup content lambda is not composable, so labels are resolved up here.
    val startLabel = stringResource(R.string.action_start)
    val stopLabel = stringResource(R.string.action_stop)
    val restartLabel = stringResource(R.string.action_restart)

    ButtonGroup(
        overflowIndicator = { menuState ->
            IconButton(onClick = { menuState.show() }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.action_more),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        clickableItem(
            onClick = onStart,
            label = startLabel,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            enabled = !busy && !running,
        )
        clickableItem(
            onClick = onStop,
            label = stopLabel,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_stop),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            enabled = !busy && running,
        )
        clickableItem(
            onClick = onRestart,
            label = restartLabel,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            enabled = !busy,
        )
    }
}

/**
 * The proxy runs, but IPv6 queries go around it. Shown instead of the reassuring
 * "going through the proxy" line, which would be false here.
 */
@Composable
private fun Ipv6BypassCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_warning),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.status_ipv6_bypass_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.status_ipv6_bypass_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/** Our rules are installed but another module took port 53 first, so they sit idle. */
@Composable
private fun BoxConflictCard(onOpenConflicts: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_warning),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.status_conflict_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.status_conflict_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenConflicts) {
                Text(stringResource(R.string.status_open_conflicts))
            }
        }
    }
}

@Composable
private fun StatusDetails(status: ModuleStatus, uptimeSeconds: Long?) {
    val unknown = stringResource(R.string.value_unknown)
    InfoRow(
        label = stringResource(R.string.label_uptime),
        value = uptimeSeconds?.let { formatUptime(it) } ?: unknown,
    )
    InfoRow(
        label = stringResource(R.string.label_version),
        value = status.versionOrNull ?: unknown,
    )
    InfoRow(label = stringResource(R.string.label_port), value = status.port.toString())
    InfoRow(
        label = stringResource(R.string.label_port6),
        value = status.port6?.toString() ?: unknown,
    )
    InfoRow(
        label = stringResource(R.string.label_rules),
        value = stringResource(
            if (status.rulesV4) R.string.value_present else R.string.value_absent
        ),
        valueColor = if (status.rulesV4) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.error
        },
    )
    InfoRow(
        label = stringResource(R.string.label_ipv6),
        value = stringResource(ipv6Label(status.redirectIpv6)),
    )
    // Only modules from 2.4.2 on report where IPv6 queries actually go.
    status.ipv6Dns?.let { dns ->
        InfoRow(
            label = stringResource(R.string.label_ipv6_dns),
            value = stringResource(
                when (dns) {
                    Ipv6Dns.REDIRECTED -> R.string.ipv6_dns_redirected
                    Ipv6Dns.BLOCKED -> R.string.ipv6_dns_blocked
                    Ipv6Dns.BYPASS -> R.string.ipv6_dns_bypass
                }
            ),
            valueColor = if (dns == Ipv6Dns.BYPASS) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
    InfoRow(
        label = stringResource(R.string.label_tether),
        value = stringResource(
            if (status.tetherRedirect) R.string.value_on else R.string.value_off
        ),
    )
    InfoRow(
        label = stringResource(R.string.label_private_dns),
        value = status.privateDns ?: unknown,
    )
    InfoRow(label = stringResource(R.string.label_pid), value = status.pid?.toString() ?: unknown)
    InfoRow(
        label = stringResource(R.string.label_supervisor),
        value = status.supervisorPid?.toString() ?: unknown,
    )
    InfoRow(
        label = stringResource(R.string.label_config),
        value = status.config.substringAfterLast('/').ifBlank { unknown },
    )
}

@Composable
private fun DnsCheckCard(
    port: Int,
    probing: Boolean,
    probe: ProbeResult?,
    onProbe: () -> Unit,
    onModuleTest: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.dns_check_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.dns_check_subtitle, DnsProbe.DEFAULT_HOST, port),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onProbe, enabled = !probing) {
                    Text(stringResource(R.string.dns_check_run))
                }
                Spacer(Modifier.size(16.dp))
                if (probing) {
                    androidx.compose.material3.LoadingIndicator()
                } else if (probe != null) {
                    ProbeText(probe)
                }
            }
            TextButton(onClick = onModuleTest) {
                Text(stringResource(R.string.dns_check_module_test))
            }
        }
    }
}

@Composable
private fun ProbeText(probe: ProbeResult) {
    when (probe) {
        is ProbeResult.Answered -> Column {
            Text(
                text = if (probe.isResolved) {
                    stringResource(R.string.dns_check_answered, probe.rttMillis)
                } else {
                    stringResource(R.string.dns_check_empty, probe.rttMillis)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (probe.isResolved) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )
            if (probe.rcode != 0) {
                Text(
                    text = stringResource(R.string.dns_check_rcode, probe.rcode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is ProbeResult.TimedOut -> Text(
            text = stringResource(R.string.dns_check_timeout),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )

        is ProbeResult.Failed -> Text(
            text = stringResource(R.string.dns_check_failed, probe.reason),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

fun phaseOf(status: ModuleStatus): ProxyPhase = when {
    status.running -> ProxyPhase.RUNNING
    status.isRestarting -> ProxyPhase.RESTARTING
    else -> ProxyPhase.STOPPED
}

private fun ProxyPhase.titleRes() = when (this) {
    ProxyPhase.RUNNING -> R.string.status_running
    ProxyPhase.STOPPED -> R.string.status_stopped
    ProxyPhase.RESTARTING -> R.string.status_restarting
}

private fun ProxyPhase.hintRes() = when (this) {
    ProxyPhase.RUNNING -> R.string.status_running_hint
    ProxyPhase.STOPPED -> R.string.status_stopped_hint
    ProxyPhase.RESTARTING -> R.string.status_restarting_hint
}

fun ipv6Label(mode: String) = when (mode) {
    "redirect" -> R.string.ipv6_redirect
    "off" -> R.string.ipv6_off
    else -> R.string.ipv6_auto
}

@Composable
private fun formatUptime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        stringResource(R.string.uptime_format, hours, minutes)
    } else {
        stringResource(R.string.uptime_format_short, minutes, seconds % 60)
    }
}
