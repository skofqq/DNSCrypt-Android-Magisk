package com.skofqq.shieldy.ui.status

import com.skofqq.shieldy.ui.update.ModuleUpdateUiState
import com.skofqq.shieldy.ui.update.UpdatePhase
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
import com.skofqq.shieldy.R
import com.skofqq.shieldy.data.ModuleStatus
import com.skofqq.shieldy.ui.components.NoticeCard
import com.skofqq.shieldy.ui.theme.Tints

/**
 * Only while there is something to do about the module: a newer release to install, or
 * an installed one waiting for a reboot. Opens the update sheet; the version itself
 * lives in Settings.
 */
@Composable
fun UpdateBanner(update: ModuleUpdateUiState, onClick: () -> Unit) {
    val rebootPending = update.phase == UpdatePhase.INSTALLED
    if (!rebootPending && !update.updateAvailable) return
    NoticeCard(
        title = if (rebootPending) {
            stringResource(R.string.version_banner_reboot_title)
        } else {
            stringResource(R.string.version_update_available, update.release?.version.orEmpty())
        },
        body = stringResource(if (rebootPending) R.string.update_reboot_hint else R.string.version_banner_update_body),
        tint = if (rebootPending) Tints.amber else Tints.green,
        icon = painterResource(R.drawable.ic_system_update),
        modifier = Modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
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
