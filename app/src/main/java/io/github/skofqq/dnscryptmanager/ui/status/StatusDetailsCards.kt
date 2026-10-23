package io.github.skofqq.dnscryptmanager.ui.status

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.data.Ipv6Dns
import io.github.skofqq.dnscryptmanager.data.ModuleStatus
import io.github.skofqq.dnscryptmanager.ui.components.NoticeCard
import io.github.skofqq.dnscryptmanager.ui.theme.Tints

/**
 * Two half-width cards under DNS check / Server: whether DNS is intercepted (and for
 * how long), and on which port. Everything else is in the sheet behind them.
 */
@Composable
fun DetailsCards(status: ModuleStatus, uptimeSeconds: Long?, onOpenAll: () -> Unit) {
    val running = status.running
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // Red only for a running proxy without its rules; stopped is simply off.
        val dot = when {
            !running -> Tints.gray
            status.rulesV4 -> Tints.green
            else -> Tints.red
        }
        MetricCard(
            title = stringResource(R.string.details_interception),
            badge = null,
            tint = null,
            dot = dot.fg,
            value = if (running) {
                stringResource(R.string.details_running_for, uptimeSeconds?.let { formatUptime(it) } ?: stringResource(R.string.common_dash))
            } else {
                stringResource(R.string.details_off)
            },
            subtitle = if (running) {
                stringResource(R.string.details_rules, if (status.rulesV4) "✓" else "✗", ipv6State(status))
            } else {
                stringResource(R.string.details_stopped)
            },
            subtitleMaxLines = 1,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onOpenAll,
        )
        MetricCard(
            title = stringResource(R.string.label_port),
            badge = null,
            tint = null,
            value = status.port.toString(),
            subtitle = stringResource(R.string.details_ipv6_short, stringResource(ipv6Label(status.redirectIpv6)).lowercase()),
            subtitleMaxLines = 1,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onOpenAll,
        )
    }
}

/**
 * Shown only while the proxy runs with Private DNS on: Android then sends DNS over TLS
 * straight to its provider, around the port 53 interception.
 */
@Composable
fun PrivateDnsWarning(status: ModuleStatus) {
    val mode = status.privateDns?.trim()?.lowercase()
    if (!status.running || (mode != "hostname" && mode != "opportunistic")) return
    val context = LocalContext.current
    NoticeCard(
        title = stringResource(R.string.private_dns_warning_title),
        body = stringResource(R.string.private_dns_warning_body),
        tint = Tints.amber,
        icon = painterResource(R.drawable.ic_warning),
        modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable {
            // Private DNS sits under Network & internet; there is no public action for it.
            runCatching {
                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
    )
}

/** What the IPv6 side does right now, in words. */
@Composable
private fun ipv6State(status: ModuleStatus): String = status.ipv6Dns?.let { ipv6DnsLabel(it) }
    ?: stringResource(ipv6Label(status.redirectIpv6)).lowercase()

@Composable
private fun ipv6DnsLabel(dns: Ipv6Dns): String = stringResource(
    when (dns) {
        Ipv6Dns.REDIRECTED -> R.string.ipv6_short_redirected
        Ipv6Dns.BLOCKED -> R.string.ipv6_short_blocked
        Ipv6Dns.BYPASS -> R.string.ipv6_short_bypass
    }
)

/** Android's private_dns_mode in words; "null" (never set) means the system default. */
@Composable
fun privateDnsLabel(raw: String?): String = stringResource(
    when (raw?.trim()?.lowercase()) {
        "off" -> R.string.private_dns_off
        "opportunistic" -> R.string.private_dns_auto
        "hostname" -> R.string.private_dns_hostname
        else -> R.string.private_dns_default
    }
)
