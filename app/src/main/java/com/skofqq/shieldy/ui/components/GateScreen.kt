package com.skofqq.shieldy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skofqq.shieldy.R
import com.skofqq.shieldy.data.ModuleContract
import com.skofqq.shieldy.data.ModuleState

/**
 * Everything that stands between the app and a usable module: no root, no module,
 * module switched off, module too old.
 */
@Composable
fun GateScreen(
    state: ModuleState,
    onRetry: () -> Unit,
    onOpenReleases: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            is ModuleState.Loading -> {
                LoadingIndicator()
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.gate_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is ModuleState.NoRoot -> GateBody(
                title = stringResource(R.string.gate_no_root_title),
                body = stringResource(R.string.gate_no_root_body),
                primaryLabel = stringResource(R.string.action_retry),
                onPrimary = onRetry,
            )

            is ModuleState.ModuleMissing -> GateBody(
                title = stringResource(R.string.gate_missing_title),
                body = stringResource(R.string.gate_missing_body),
                primaryLabel = stringResource(R.string.action_open_releases),
                onPrimary = onOpenReleases,
                secondaryLabel = stringResource(R.string.action_retry),
                onSecondary = onRetry,
            )

            is ModuleState.ModuleDisabled -> GateBody(
                title = stringResource(R.string.gate_disabled_title),
                body = stringResource(R.string.gate_disabled_body),
                primaryLabel = stringResource(R.string.action_retry),
                onPrimary = onRetry,
            )

            is ModuleState.Outdated -> GateBody(
                title = stringResource(R.string.gate_outdated_title),
                body = buildString {
                    append(stringResource(R.string.gate_outdated_body, ModuleContract.MIN_VERSION))
                    append("\n\n")
                    append(
                        state.version?.let { stringResource(R.string.gate_outdated_installed, it) }
                            ?: stringResource(R.string.gate_outdated_unknown)
                    )
                },
                primaryLabel = stringResource(R.string.action_open_releases),
                onPrimary = onOpenReleases,
                secondaryLabel = stringResource(R.string.action_retry),
                onSecondary = onRetry,
            )

            is ModuleState.Ready -> Unit
        }
    }
}

@Composable
private fun GateBody(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Icon(
        painter = painterResource(R.drawable.ic_shield),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(56.dp),
    )
    Spacer(Modifier.height(20.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
    Button(onClick = onPrimary) { Text(primaryLabel) }
    if (secondaryLabel != null && onSecondary != null) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
    }
}
