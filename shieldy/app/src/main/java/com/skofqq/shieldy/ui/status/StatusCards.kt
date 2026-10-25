package com.skofqq.shieldy.ui.status

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skofqq.shieldy.R
import com.skofqq.shieldy.data.DnsVerdict
import com.skofqq.shieldy.data.ModuleStatus
import com.skofqq.shieldy.dns.ProbeResult
import com.skofqq.shieldy.ui.components.BoxyCard
import com.skofqq.shieldy.ui.components.CardTitle
import com.skofqq.shieldy.ui.servers.rttTint
import com.skofqq.shieldy.ui.theme.Boxy
import com.skofqq.shieldy.ui.theme.Tint
import com.skofqq.shieldy.ui.theme.Tints
import kotlinx.coroutines.delay

/** Boxy's paired metric cards (IP / Speed there): DNS check on the left, the server on the right. */
@Composable
fun DnsCardsRow(
    status: ModuleStatus,
    uiState: StatusUiState,
    dnsCheck: DnsCheckState,
    onCheck: () -> Unit,
    onOpenServers: () -> Unit,
) {
    // One under the other at full width, like the Version card: the titles keep their
    // badges on one line and long server names fit.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DnsCheckCard(dnsCheck, status, Modifier.fillMaxWidth(), onCheck)
        ServerCard(status, uiState, Modifier.fillMaxWidth(), onOpenServers)
    }
}

@Composable
private fun DnsCheckCard(state: DnsCheckState, status: ModuleStatus, modifier: Modifier, onClick: () -> Unit) {
    // A verdict from before a start or stop no longer describes where queries go.
    val proxyActiveNow = status.running && status.rulesV4 && status.boxDns != true
    val checked = state.checkedAt != null && !state.running && state.proxyActive == proxyActiveNow
    // The value is the verdict and the badge its colour; who answered goes in the subtitle.
    val (badge, tint, value) = when {
        !checked -> Triple(null, null, stringResource(R.string.dnscard_value_unchecked))
        else -> when (state.verdict) {
            DnsVerdict.THROUGH_PROXY -> Triple(stringResource(R.string.dnscard_badge_ok), Tints.green, stringResource(R.string.dnscard_value_shieldy))
            DnsVerdict.LEAK -> Triple(stringResource(R.string.dnscard_badge_leak), Tints.red, stringResource(R.string.dnscard_value_bypass))
            DnsVerdict.PROXY_STOPPED -> if (state.boxDns) {
                Triple("Box", Tints.gray, stringResource(R.string.dnscard_value_box))
            } else {
                Triple(stringResource(R.string.dnscard_badge_system), Tints.gray, stringResource(R.string.dnscard_value_system))
            }
            // No leak test yet: the local probe alone says the proxy answers, but not who
            // the internet saw, so no OK badge until the leak test runs.
            DnsVerdict.UNKNOWN -> Triple(
                null,
                null,
                stringResource(
                    if ((state.probe as? ProbeResult.Answered)?.isResolved == true) R.string.dnscard_value_shieldy
                    else R.string.dnscard_value_unknown
                ),
            )
        }
    }
    // The resolver the internet saw (bash.ws), else the network's own DNS when the proxy is off.
    val resolver = state.leak?.resolvers?.firstOrNull()?.let { r ->
        listOf(r.flag, r.org.ifBlank { r.ip }).filter { it.isNotBlank() }.joinToString(" ")
    } ?: state.network?.servers?.firstOrNull()?.takeIf { state.verdict == DnsVerdict.PROXY_STOPPED }
    // Re-render the "N min ago" line once a minute.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.checkedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000)
        }
    }
    val subtitle = when {
        state.running -> stringResource(R.string.dnscheck_running)
        !checked || state.checkedAt == null -> stringResource(R.string.dnscard_tap_to_check)
        else -> {
            val minutes = ((now - state.checkedAt) / 60_000).toInt().coerceAtLeast(0)
            val ago = if (minutes == 0) stringResource(R.string.dnscard_checked_now)
            else pluralStringResource(R.plurals.dnscard_checked_ago, minutes, minutes)
            if (resolver != null) "$resolver · $ago" else ago
        }
    }
    MetricCard(
        title = stringResource(R.string.dns_check_title),
        badge = badge,
        tint = tint,
        value = value,
        subtitle = subtitle,
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
private fun ServerCard(status: ModuleStatus, uiState: StatusUiState, modifier: Modifier, onClick: () -> Unit) {
    val active = uiState.activeServer.takeIf { status.running }
    val live = uiState.live.takeIf { status.running }
    // The protocol only tells servers apart when the set mixes DNSCrypt with DoH / ODoH.
    val mixed = live?.servers?.map { it.protocol }?.distinct()?.size?.let { it > 1 } == true
    val protocol = if (!mixed) null else live?.servers?.firstOrNull { it.name == active?.name }?.protocol
        ?: live?.servers?.firstOrNull()?.protocol
    val subtitle = when {
        !status.running -> stringResource(R.string.dnscard_server_stopped)
        // The strategy behind "in use" is spelled out in the DNS check sheet.
        live != null && live.servers.isNotEmpty() ->
            // Right after a start the proxy has not logged its "live servers" count yet.
            stringResource(R.string.dnscard_server_in_use, live.inUseCount, maxOf(live.liveCount, live.servers.size))
        else -> stringResource(R.string.servers_mode_auto)
    }
    val rtt = active?.rttMs
    MetricCard(
        title = stringResource(R.string.hero_server),
        badge = protocol,
        tint = if (protocol != null) Tints.blue else null,
        value = active?.name ?: stringResource(R.string.common_dash),
        subtitle = subtitle,
        modifier = modifier,
        onClick = onClick,
        subtitleMaxLines = 1,
        trailing = if (rtt != null || uiState.rttHistory.size > 1) {
            {
                Column(horizontalAlignment = Alignment.End) {
                    rtt?.let {
                        Text(
                            stringResource(R.string.servers_rtt_short, it),
                            style = MaterialTheme.typography.labelLarge,
                            color = rttTint(it).fg,
                        )
                    }
                    Sparkline(uiState.rttHistory, Tints.teal.fg, Modifier.width(64.dp).height(22.dp))
                }
            }
        } else {
            null
        },
    )
}

/** Title with a pill, a big value, a grey subtitle with a chevron: Boxy's IpCard layout. */
@Composable
internal fun MetricCard(
    title: String,
    badge: String?,
    tint: Tint?,
    value: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit,
    extra: @Composable () -> Unit = {},
    /** A state dot before the value; unlike a badge it keeps the card's rows in line. */
    dot: Color? = null,
    subtitleMaxLines: Int = 2,
    /** Shown right of the value and subtitle, before the chevron (the server's RTT). */
    trailing: (@Composable () -> Unit)? = null,
) {
    BoxyCard(modifier = modifier, onClick = onClick) {
        CardTitle(title, badge = badge, tint = tint)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(dot))
                Spacer(Modifier.width(8.dp))
            }
            // Shrinks before it cuts: "Running 2 h 14 min" has to fit half a 360 dp screen
            // at large font sizes. The step range keeps it a headline even at its smallest.
            BasicText(
                value,
                style = MaterialTheme.typography.titleLarge.copy(color = Boxy.colors.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 21.sp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Boxy.colors.text2,
            maxLines = subtitleMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
        }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                null,
                Modifier.size(24.dp).clip(RoundedCornerShape(50)),
                tint = Boxy.colors.text2,
            )
        }
        extra()
        Spacer(Modifier.weight(1f, fill = false))
    }
}

/** Boxy's sparkline: a thin line scaled to the largest sample. */
@Composable
private fun Sparkline(values: List<Int>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(color.copy(alpha = 0.6f), Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
            return@Canvas
        }
        val max = values.max().coerceAtLeast(1).toFloat()
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v / max) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
