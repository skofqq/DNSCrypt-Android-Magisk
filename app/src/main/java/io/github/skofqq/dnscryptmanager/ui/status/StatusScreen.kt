package io.github.skofqq.dnscryptmanager.ui.status

import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.skofqq.dnscryptmanager.R
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.skofqq.dnscryptmanager.data.ActiveServer
import io.github.skofqq.dnscryptmanager.ui.components.BoxyDialog
import io.github.skofqq.dnscryptmanager.ui.components.LocalBarPadding
import io.github.skofqq.dnscryptmanager.ui.components.PageInset
import io.github.skofqq.dnscryptmanager.ui.servers.rttTint
import io.github.skofqq.dnscryptmanager.ui.components.ActionPill
import io.github.skofqq.dnscryptmanager.ui.components.Badge
import io.github.skofqq.dnscryptmanager.ui.components.BoxyCard
import io.github.skofqq.dnscryptmanager.ui.components.CardTitle
import io.github.skofqq.dnscryptmanager.ui.components.NoticeCard
import io.github.skofqq.dnscryptmanager.ui.theme.Boxy
import io.github.skofqq.dnscryptmanager.ui.theme.Tints
import io.github.skofqq.dnscryptmanager.ui.update.ModuleUpdateUiState
import io.github.skofqq.dnscryptmanager.ui.update.UpdatePhase
import io.github.skofqq.dnscryptmanager.data.Ipv6Dns
import io.github.skofqq.dnscryptmanager.data.ModuleState
import io.github.skofqq.dnscryptmanager.data.ModuleStatus
import io.github.skofqq.dnscryptmanager.dns.DnsProbe
import io.github.skofqq.dnscryptmanager.dns.ProbeResult
import io.github.skofqq.dnscryptmanager.ui.components.InfoRow
import io.github.skofqq.dnscryptmanager.ui.components.ProxyPhase
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
    updateState: ModuleUpdateUiState,
    onOpenUpdate: () -> Unit,
    dnsCheck: DnsCheckState,
    onOpenServers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = uiState.state as? ModuleState.Ready ?: return
    val status = ready.status
    val phase = phaseOf(status)
    // The module says whether box really holds port 53 (2.4.1+); older ones only say it is there.
    val boxCapturesDns = status.boxDns ?: (uiState.boxDetected == true)
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
                .padding(horizontal = PageInset),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(0.dp))
            HeroCard(
                status = status,
                phase = phase,
                uptimeSeconds = ready.uptimeSeconds,
                busyAction = uiState.busyAction,
                boxHandover = boxCapturesDns && !status.rulesV4,
                onStart = { onAction(ProxyAction.START) },
                onStop = { confirmStop = true },
                onRestart = { onAction(ProxyAction.RESTART) },
                onOpenLogs = onOpenLogs,
            )

            if (status.ipv6Bypass) {
                Ipv6BypassCard()
            }

            if (boxCapturesDns && status.rulesV4) {
                BoxConflictCard(onOpenConflicts = onOpenConflicts)
            }

            if (updateState.updateAvailable || updateState.phase == UpdatePhase.INSTALLED) {
                ModuleUpdateCard(updateState, onOpenUpdate)
            }

            DnsCardsRow(
                status = status,
                uiState = uiState,
                dnsCheck = dnsCheck,
                onCheck = onProbe,
                onOpenServers = onOpenServers,
            )

            var allDetails by rememberSaveable { mutableStateOf(false) }
            PrivateDnsWarning(status)
            DetailsCards(status = status, uptimeSeconds = ready.uptimeSeconds, onOpenAll = { allDetails = true })
            if (allDetails) {
                BoxyDialog(
                    title = stringResource(R.string.status_details),
                    onDismiss = { allDetails = false },
                    confirmText = stringResource(R.string.action_ok),
                    onConfirm = { allDetails = false },
                ) {
                    // Scrolls with large font scaling instead of pushing the button off screen.
                    Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                        StatusDetails(status = status, uptimeSeconds = ready.uptimeSeconds)
                    }
                }
            }

            Spacer(Modifier.height(LocalBarPadding.current))
        }
    }

    if (confirmStop) {
        BoxyDialog(
            title = stringResource(R.string.confirm_stop_title),
            text = stringResource(R.string.confirm_stop_message),
            onDismiss = { confirmStop = false },
            dismissText = stringResource(R.string.action_cancel),
            confirmText = stringResource(R.string.action_stop),
            confirmTint = Tints.red,
            onConfirm = {
                confirmStop = false
                onAction(ProxyAction.STOP)
            },
        )
    }
}

/**
 * Boxy's hero card: big coloured state on the left, the key facts in a side panel,
 * soft Start / Stop / Restart pills underneath.
 */
@Composable
private fun HeroCard(
    status: ModuleStatus,
    phase: ProxyPhase,
    uptimeSeconds: Long?,
    busyAction: ProxyAction?,
    boxHandover: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val colors = Boxy.colors
    val busy = busyAction != null
    val running = phase != ProxyPhase.STOPPED
    BoxyCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    stringResource(R.string.status_service),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(phase.titleRes()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = phaseColor(phase),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                val chip = when {
                    phase == ProxyPhase.RUNNING && uptimeSeconds != null ->
                        stringResource(R.string.hero_uptime, formatUptime(uptimeSeconds))
                    phase == ProxyPhase.STOPPED -> stringResource(R.string.hero_tap_start)
                    else -> stringResource(R.string.label_pid) + " " + (status.pid?.toString() ?: "—")
                }
                Text(
                    chip,
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface2)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text,
                    maxLines = 1,
                )
            }
            Column(
                Modifier
                    .width(150.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surface2)
                    .padding(vertical = 8.dp),
            ) {
                MiniRow(
                    stringResource(R.string.hero_ipv4),
                    stringResource(if (status.rulesV4) R.string.hero_on else R.string.hero_off),
                    if (status.rulesV4) Tints.green.fg else colors.text,
                )
                val ipv6 = status.ipv6Dns
                MiniRow(
                    stringResource(R.string.hero_ipv6),
                    when (ipv6) {
                        Ipv6Dns.REDIRECTED -> stringResource(R.string.hero_ipv6_redirected)
                        Ipv6Dns.BLOCKED -> stringResource(R.string.hero_ipv6_blocked)
                        Ipv6Dns.BYPASS -> stringResource(R.string.hero_ipv6_bypass)
                        null -> stringResource(ipv6Label(status.redirectIpv6))
                    },
                    if (ipv6 == Ipv6Dns.BYPASS && status.running) Tints.red.fg else colors.text,
                )
                MiniRow(stringResource(R.string.hero_port), status.port.toString())
                MiniRow(stringResource(R.string.hero_module), status.versionOrNull ?: "—")
            }
        }

        if (!status.ipv6Bypass) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(if (phase == ProxyPhase.STOPPED && status.manualStop) R.string.status_manual_stop_hint else phase.hintRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text2,
            )
        }
        if (phase == ProxyPhase.RESTARTING) {
            TextButton(onClick = onOpenLogs) { Text(stringResource(R.string.status_open_logs)) }
        }
        // Intercept handed to box on purpose: this is a working setup, not a fault.
        if (boxHandover) {
            Spacer(Modifier.height(8.dp))
            Badge(stringResource(R.string.status_box_handover), Tints.blue)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surface2.copy(alpha = 0.6f))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionPill(stringResource(R.string.action_start), Tints.green, !busy && !running, Modifier.weight(1f), onStart)
            ActionPill(stringResource(R.string.action_stop), Tints.red, !busy && running, Modifier.weight(1f), onStop)
            ActionPill(stringResource(R.string.action_restart), Tints.amber, !busy, Modifier.weight(1f), onRestart)
        }

        AnimatedVisibility(visible = busy) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.working),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text2,
                )
            }
        }
    }
}

@Composable
private fun MiniRow(label: String, value: String, color: Color = Boxy.colors.text) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Boxy.colors.text2, maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shown on the home screen while update.json offers something newer, or a reboot is pending. */
@Composable
private fun ModuleUpdateCard(state: ModuleUpdateUiState, onClick: () -> Unit) {
    val installed = state.phase == UpdatePhase.INSTALLED
    BoxyCard(onClick = onClick) {
        CardTitle(
            stringResource(R.string.update_card_title),
            badge = stringResource(if (installed) R.string.update_badge_reboot else R.string.update_badge_new),
            tint = if (installed) Tints.amber else Tints.green,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "${state.installed?.version.orEmpty()} → ${state.release?.version.orEmpty()}",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
            color = Boxy.colors.text,
        )
        Text(
            stringResource(if (installed) R.string.update_reboot_hint else R.string.update_card_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Boxy.colors.text2,
        )
    }
}

/**
 * The proxy runs, but IPv6 queries go around it. Shown instead of the reassuring
 * "going through the proxy" line, which would be false here.
 */
@Composable
private fun Ipv6BypassCard() {
    NoticeCard(
        title = stringResource(R.string.status_ipv6_bypass_title),
        body = stringResource(R.string.status_ipv6_bypass_body),
        tint = Tints.red,
        icon = painterResource(R.drawable.ic_warning),
    )
}

/** Our rules are installed but another module took port 53 first, so they sit idle. */
@Composable
private fun BoxConflictCard(onOpenConflicts: () -> Unit) {
    NoticeCard(
        title = stringResource(R.string.status_conflict_title),
        body = stringResource(R.string.status_conflict_body),
        tint = Tints.amber,
        icon = painterResource(R.drawable.ic_warning),
    ) {
        ActionPill(
            stringResource(R.string.status_open_conflicts),
            Tints.amber,
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenConflicts,
        )
    }
}

/** Every fact the module reports, for the dialog behind the detail cards. */
@Composable
private fun StatusDetails(status: ModuleStatus, uptimeSeconds: Long?) {
    val unknown = stringResource(R.string.value_unknown)
    val error = Tints.red.fg
    InfoRow(
        label = stringResource(R.string.label_uptime),
        value = uptimeSeconds?.takeIf { status.running }?.let { formatUptime(it) } ?: unknown,
    )
    InfoRow(label = stringResource(R.string.label_version), value = status.versionOrNull ?: unknown)
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
            valueColor = if (dns == Ipv6Dns.BYPASS) error else Boxy.colors.text,
        )
    }
    InfoRow(label = stringResource(R.string.label_private_dns), value = privateDnsLabel(status.privateDns))

    InfoRow(label = stringResource(R.string.label_port), value = status.port.toString())
    InfoRow(label = stringResource(R.string.label_port6), value = status.port6?.toString() ?: unknown)
    InfoRow(
        label = stringResource(R.string.label_rules),
        value = stringResource(
            when {
                status.rulesV4 -> R.string.value_present
                status.running -> R.string.value_absent
                else -> R.string.value_off
            }
        ),
        // Missing rules are only a problem while the proxy runs.
        valueColor = if (status.rulesV4 || !status.running) Boxy.colors.text else error,
    )
    InfoRow(label = stringResource(R.string.label_ipv6), value = stringResource(ipv6Label(status.redirectIpv6)))
    InfoRow(
        label = stringResource(R.string.label_tether),
        value = stringResource(if (status.tetherRedirect) R.string.value_on else R.string.value_off),
    )
    InfoRow(label = stringResource(R.string.label_pid), value = status.pid?.toString() ?: unknown)
    InfoRow(label = stringResource(R.string.label_supervisor), value = status.supervisorPid?.toString() ?: unknown)
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
    BoxyCard {
        CardTitle(stringResource(R.string.dns_check_title))
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.dns_check_subtitle, DnsProbe.DEFAULT_HOST, port),
            style = MaterialTheme.typography.bodySmall,
            color = Boxy.colors.text2,
        )
        Spacer(Modifier.height(14.dp))
        // Same tray as the hero's Start / Stop / Restart, so both cards read alike.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Boxy.colors.surface2.copy(alpha = 0.6f))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionPill(stringResource(R.string.dns_check_run), Tints.blue, !probing, Modifier.weight(1f), onProbe)
            ActionPill(stringResource(R.string.dns_check_self_test), Tints.teal, true, Modifier.weight(1f), onModuleTest)
        }
        if (probing || probe != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (probing) {
                    androidx.compose.material3.LoadingIndicator(Modifier.size(32.dp))
                } else if (probe != null) {
                    ProbeText(probe)
                }
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
internal fun formatUptime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        stringResource(R.string.uptime_format, hours, minutes)
    } else {
        stringResource(R.string.uptime_format_short, minutes, seconds % 60)
    }
}
