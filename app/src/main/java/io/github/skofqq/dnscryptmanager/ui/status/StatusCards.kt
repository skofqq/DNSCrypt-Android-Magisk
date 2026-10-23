package io.github.skofqq.dnscryptmanager.ui.status

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
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.data.DnsVerdict
import io.github.skofqq.dnscryptmanager.data.ModuleStatus
import io.github.skofqq.dnscryptmanager.ui.components.BoxyCard
import io.github.skofqq.dnscryptmanager.ui.components.CardTitle
import io.github.skofqq.dnscryptmanager.ui.servers.rttTint
import io.github.skofqq.dnscryptmanager.ui.theme.Boxy
import io.github.skofqq.dnscryptmanager.ui.theme.Tint
import io.github.skofqq.dnscryptmanager.ui.theme.Tints
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
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DnsCheckCard(dnsCheck, Modifier.weight(1f).fillMaxHeight(), onCheck)
        ServerCard(status, uiState, Modifier.weight(1f).fillMaxHeight(), onOpenServers)
    }
}

@Composable
private fun DnsCheckCard(state: DnsCheckState, modifier: Modifier, onClick: () -> Unit) {
    val checked = state.checkedAt != null && !state.running
    val (badge, tint, value) = when {
        !checked -> Triple(null, null, stringResource(R.string.common_dash))
        else -> when (state.verdict) {
            DnsVerdict.THROUGH_PROXY -> Triple(stringResource(R.string.dnscard_badge_dnscrypt), Tints.green, stringResource(R.string.dnscard_value_proxy))
            DnsVerdict.LEAK -> Triple(stringResource(R.string.dnscard_badge_leak), Tints.red, state.leak?.publicIp?.org?.ifBlank { null } ?: stringResource(R.string.dnscard_value_leak))
            DnsVerdict.PROXY_STOPPED -> if (state.boxDns) {
                Triple("Box", Tints.gray, stringResource(R.string.dnscard_value_box))
            } else {
                Triple(stringResource(R.string.dnscard_badge_system), Tints.gray, state.network?.servers?.firstOrNull() ?: stringResource(R.string.dnscard_value_system))
            }
            DnsVerdict.UNKNOWN -> Triple(stringResource(R.string.dnscard_badge_dnscrypt), Tints.blue, stringResource(R.string.dnscard_value_proxy))
        }
    }
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
        state.checkedAt == null -> stringResource(R.string.dnscard_tap_to_check)
        else -> {
            val minutes = ((now - state.checkedAt) / 60_000).toInt().coerceAtLeast(0)
            if (minutes == 0) stringResource(R.string.dnscard_checked_now)
            else pluralStringResource(R.plurals.dnscard_checked_ago, minutes, minutes)
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
    val protocol = live?.servers?.firstOrNull { it.name == active?.name }?.protocol
        ?: live?.servers?.firstOrNull()?.protocol
    val subtitle = when {
        !status.running -> stringResource(R.string.dnscard_server_stopped)
        live != null && live.servers.isNotEmpty() ->
            stringResource(R.string.dnscard_server_sub, live.inUseCount, live.liveCount, live.strategy)
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
        extra = {
            if (rtt != null || uiState.rttHistory.size > 1) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    rtt?.let {
                        Text(
                            stringResource(R.string.servers_rtt_short, it),
                            style = MaterialTheme.typography.labelLarge,
                            color = rttTint(it).fg,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Sparkline(uiState.rttHistory, Tints.teal.fg, Modifier.width(64.dp).height(26.dp))
                }
            }
        },
    )
}

/** Title with a pill, a big value, a grey subtitle with a chevron: Boxy's IpCard layout. */
@Composable
private fun MetricCard(
    title: String,
    badge: String?,
    tint: Tint?,
    value: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    BoxyCard(modifier = modifier, onClick = onClick) {
        CardTitle(title, badge = badge, tint = tint)
        Spacer(Modifier.height(14.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
            color = Boxy.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                subtitle,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = Boxy.colors.text2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
