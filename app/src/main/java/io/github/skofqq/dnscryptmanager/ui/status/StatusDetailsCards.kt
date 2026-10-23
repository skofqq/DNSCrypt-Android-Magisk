package io.github.skofqq.dnscryptmanager.ui.status

import io.github.skofqq.dnscryptmanager.ui.update.ModuleUpdateUiState
import io.github.skofqq.dnscryptmanager.ui.update.UpdatePhase
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.data.ModuleStatus
import io.github.skofqq.dnscryptmanager.ui.components.NoticeCard
import io.github.skofqq.dnscryptmanager.ui.theme.Tints

/**
 * The module version and where it stands against the latest release; opens the update
 * sheet. A badge marks an update to install or a reboot it is waiting for.
 */
@Composable
fun VersionCard(status: ModuleStatus, update: ModuleUpdateUiState, onClick: () -> Unit) {
    val rebootPending = update.phase == UpdatePhase.INSTALLED
    val (badge, tint) = when {
        rebootPending -> stringResource(R.string.update_badge_reboot) to Tints.amber
        update.updateAvailable -> stringResource(R.string.version_badge_update) to Tints.green
        else -> null to null
    }
    MetricCard(
        title = stringResource(R.string.version_card_title),
        badge = badge,
        tint = tint,
        value = status.versionOrNull ?: stringResource(R.string.common_dash),
        subtitle = when {
            rebootPending -> stringResource(R.string.update_reboot_hint)
            update.updateAvailable -> stringResource(R.string.version_update_available, update.release?.version.orEmpty())
            update.phase == UpdatePhase.CHECKING -> stringResource(R.string.version_checking)
            update.phase == UpdatePhase.UP_TO_DATE -> stringResource(R.string.version_up_to_date)
            update.error != null -> stringResource(R.string.version_check_failed)
            else -> stringResource(R.string.version_tap_to_check)
        },
        subtitleMaxLines = 1,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    )
}

/** The proxy runs but nothing sends DNS to it: the intercept rules are gone. */
@Composable
fun RulesMissingCard() {
    NoticeCard(
        title = stringResource(R.string.rules_missing_title),
        body = stringResource(R.string.rules_missing_body),
        tint = Tints.red,
        icon = painterResource(R.drawable.ic_warning),
    )
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
