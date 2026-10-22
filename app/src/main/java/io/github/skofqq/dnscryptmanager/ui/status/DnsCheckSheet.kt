package io.github.skofqq.dnscryptmanager.ui.status

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.data.DnsVerdict
import io.github.skofqq.dnscryptmanager.data.LeakEntry
import io.github.skofqq.dnscryptmanager.data.LiveServer
import io.github.skofqq.dnscryptmanager.dns.ProbeResult
import io.github.skofqq.dnscryptmanager.ui.components.ActionPill
import io.github.skofqq.dnscryptmanager.ui.components.Badge
import io.github.skofqq.dnscryptmanager.ui.components.NoticeCard
import io.github.skofqq.dnscryptmanager.ui.components.PrimaryPill
import io.github.skofqq.dnscryptmanager.ui.servers.rttTint
import io.github.skofqq.dnscryptmanager.ui.theme.Boxy
import io.github.skofqq.dnscryptmanager.ui.theme.Tint
import io.github.skofqq.dnscryptmanager.ui.theme.Tints

/**
 * The result of the Check button, in a Boxy sheet: the verdict first, then the servers
 * the proxy is using, the network's own DNS, and the optional bash.ws leak test.
 */
@Composable
fun DnsCheckSheet(state: DnsCheckState, onDismiss: () -> Unit, onLeakTest: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = Boxy.colors.page,
        contentColor = Boxy.colors.text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.dnscheck_title), style = MaterialTheme.typography.titleLarge, color = Boxy.colors.text)

            if (state.running) {
                Group {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.dnscheck_running), color = Boxy.colors.text2)
                    }
                }
                return@Column
            }

            Verdict(state)
            Servers(state)
            Network(state)
            Leak(state, onLeakTest)

            PrimaryPill(
                stringResource(R.string.dnscheck_close),
                Modifier.fillMaxWidth(),
                color = Boxy.colors.card,
                contentColor = Boxy.colors.text,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Boxy.colors.card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
private fun GroupTitle(text: String, badge: String? = null, tint: Tint? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Boxy.colors.text, modifier = Modifier.weight(1f))
        if (badge != null && tint != null) Badge(badge, tint)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Verdict(state: DnsCheckState) {
    val names = state.live?.inUse?.joinToString(", ") { it.name }.orEmpty()
    val networkDns = state.network?.servers?.joinToString(", ").orEmpty().ifEmpty { "—" }
    val (title, body, tint) = when (state.verdict) {
        DnsVerdict.THROUGH_PROXY -> Triple(
            stringResource(R.string.dnscheck_verdict_proxy_title),
            stringResource(R.string.dnscheck_verdict_proxy_body, names.ifEmpty { "—" }),
            Tints.green,
        )
        DnsVerdict.LEAK -> Triple(
            stringResource(R.string.dnscheck_verdict_leak_title),
            stringResource(R.string.dnscheck_verdict_leak_body, state.leak?.publicIp?.org.orEmpty().ifEmpty { networkDns }),
            Tints.red,
        )
        DnsVerdict.PROXY_STOPPED -> Triple(
            stringResource(R.string.dnscheck_verdict_stopped_title),
            if (state.boxDns) stringResource(R.string.dnscheck_verdict_box_body)
            else stringResource(R.string.dnscheck_verdict_stopped_body, networkDns),
            Tints.gray,
        )
        DnsVerdict.UNKNOWN -> Triple(
            stringResource(R.string.dnscheck_verdict_active_title),
            stringResource(R.string.dnscheck_verdict_active_body),
            Tints.blue,
        )
    }
    NoticeCard(title = title, body = body, tint = tint, icon = painterResource(R.drawable.ic_shield))
}

@Composable
private fun Servers(state: DnsCheckState) {
    val live = state.live
    Group {
        val probe = state.probe
        GroupTitle(
            stringResource(R.string.dnscheck_servers_title),
            badge = (probe as? ProbeResult.Answered)?.let { stringResource(R.string.servers_rtt_short, it.rttMillis.toInt()) },
            tint = (probe as? ProbeResult.Answered)?.let { rttTint(it.rttMillis.toInt()) },
        )
        if (live == null || live.servers.isEmpty()) {
            Text(stringResource(R.string.dnscheck_no_servers), style = MaterialTheme.typography.bodySmall, color = Boxy.colors.text2)
            return@Group
        }
        Text(
            if (live.strategy == "random") {
                stringResource(R.string.dnscheck_strategy_random, live.liveCount)
            } else {
                stringResource(R.string.dnscheck_strategy, live.strategy, live.inUseCount, live.liveCount)
            },
            style = MaterialTheme.typography.bodySmall,
            color = Boxy.colors.text2,
        )
        Spacer(Modifier.height(8.dp))
        var showAll by remember { mutableStateOf(false) }
        val shown = if (showAll) live.servers.take(DnsCheckViewModel.SHOWN_SERVERS) else live.inUse.take(DnsCheckViewModel.SHOWN_SERVERS)
        shown.forEachIndexed { index, server ->
            ServerRow(server, inUse = index < live.inUseCount, entry = state.catalog[server.name])
        }
        if (live.servers.size > live.inUseCount) {
            Spacer(Modifier.height(6.dp))
            ActionPill(
                stringResource(if (showAll) R.string.status_less_details else R.string.dnscheck_show_fastest),
                Tints.gray,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { showAll = !showAll },
            )
        }
    }
}

@Composable
private fun ServerRow(server: LiveServer, inUse: Boolean, entry: io.github.skofqq.dnscryptmanager.data.ResolverEntry?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                server.name,
                style = MaterialTheme.typography.titleSmall,
                color = Boxy.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.servers_rtt_short, server.rttMs),
                style = MaterialTheme.typography.labelLarge,
                color = rttTint(server.rttMs).fg,
            )
            Spacer(Modifier.weight(1f))
            if (inUse) Badge(stringResource(R.string.dnscheck_in_use), Tints.green)
        }
        entry?.summary?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Boxy.colors.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Tag(server.protocol, Tints.blue)
            if (entry?.noLog == true) Tag(stringResource(R.string.servers_flag_nolog), Tints.green)
            if (entry?.dnssec == true) Tag(stringResource(R.string.servers_flag_dnssec), Tints.teal)
        }
    }
}

@Composable
private fun Tag(text: String, tint: Tint) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(tint.bg).padding(horizontal = 9.dp, vertical = 2.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = tint.fg,
    )
}

@Composable
private fun Network(state: DnsCheckState) {
    val network = state.network
    Group {
        GroupTitle(stringResource(R.string.dnscheck_network_title))
        if (network == null || network.servers.isEmpty()) {
            Text(stringResource(R.string.dnscheck_network_none), style = MaterialTheme.typography.bodySmall, color = Boxy.colors.text2)
        } else {
            network.servers.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Boxy.colors.text)
            }
        }
        Spacer(Modifier.height(4.dp))
        // The setting and whether it is in effect are two things: unset means "automatic",
        // which only encrypts when the network's DNS happens to speak DoT.
        val mode = state.privateDnsMode
        val setting = when {
            mode == "off" -> stringResource(R.string.dnscheck_private_dns_off)
            mode.isNullOrBlank() || mode == "opportunistic" -> stringResource(R.string.dnscheck_private_dns_auto)
            else -> stringResource(R.string.dnscheck_private_dns_host, network?.privateDnsHost ?: mode)
        }
        val active = network?.privateDnsActive == true
        Text(
            if (mode == "off") setting
            else setting + stringResource(if (active) R.string.dnscheck_private_dns_active else R.string.dnscheck_private_dns_inactive),
            style = MaterialTheme.typography.bodySmall,
            color = if (active) Tints.amber.fg else Boxy.colors.text2,
        )
    }
}

@Composable
private fun Leak(state: DnsCheckState, onLeakTest: () -> Unit) {
    Group {
        GroupTitle(stringResource(R.string.dnscheck_leak_title))
        val report = state.leak
        when {
            state.leakRunning -> {
                Text(stringResource(R.string.dnscheck_leak_running), style = MaterialTheme.typography.bodySmall, color = Boxy.colors.text2)
                Spacer(Modifier.height(8.dp))
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
            report != null -> {
                report.publicIp?.let { LeakRow(stringResource(R.string.dnscheck_leak_public), it) }
                if (report.resolvers.isEmpty()) {
                    Text(stringResource(R.string.dnscheck_leak_none), style = MaterialTheme.typography.bodySmall, color = Boxy.colors.text2)
                }
                report.resolvers.forEach { LeakRow(null, it) }
            }
            else -> Text(stringResource(R.string.dnscheck_leak_note), style = MaterialTheme.typography.bodySmall, color = Boxy.colors.text2)
        }
        state.leakError?.let {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.dnscheck_leak_error, it), style = MaterialTheme.typography.bodySmall, color = Tints.red.fg)
        }
        if (!state.leakRunning) {
            Spacer(Modifier.height(10.dp))
            ActionPill(
                stringResource(if (report == null) R.string.dnscheck_leak_run else R.string.update_check_again),
                Tints.blue,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onLeakTest,
            )
        }
    }
}

@Composable
private fun LeakRow(label: String?, entry: LeakEntry) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(entry.flag.ifEmpty { "🌐" }, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(label, entry.ip).joinToString(": "),
                style = MaterialTheme.typography.titleSmall,
                color = Boxy.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(entry.org, entry.countryName).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Boxy.colors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
