package com.skofqq.shieldy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skofqq.shieldy.R
import com.skofqq.shieldy.ui.theme.Boxy
import com.skofqq.shieldy.ui.theme.Tint
import com.skofqq.shieldy.ui.theme.Tints
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Extra bottom room a screen asks notices to keep, e.g. for its unsaved-changes bar. */
val LocalSnackbarLift = androidx.compose.runtime.staticCompositionLocalOf { mutableStateOf(0.dp) }

enum class NoticeKind { SUCCESS, INFO, WARNING, ERROR }

/** What a notice shows; the host reads [kind] for the icon and colour. */
data class Notice(
    override val message: String,
    val kind: NoticeKind,
    override val actionLabel: String? = null,
) : SnackbarVisuals {
    override val withDismissAction: Boolean get() = false
    override val duration: SnackbarDuration
        get() = if (actionLabel != null || kind == NoticeKind.ERROR) SnackbarDuration.Long else SnackbarDuration.Short
}

/**
 * Shows a notice and runs [onAction] if its button was pressed. The action is the
 * user's choice: nothing here starts or restarts anything on its own.
 */
suspend fun SnackbarHostState.notify(
    message: String,
    kind: NoticeKind,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    // One at a time, newest first: a stale notice makes way instead of queueing.
    currentSnackbarData?.dismiss()
    if (showSnackbar(Notice(message, kind, actionLabel)) == SnackbarResult.ActionPerformed) onAction()
}

/**
 * The app's only snackbar host: a floating card that springs in above the bottom bar
 * (and the unsaved-changes bar, through [lift]), swipes away sideways, and buzzes for
 * errors.
 */
@Composable
fun BoxySnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val data = state.currentSnackbarData
    var shown by remember { mutableStateOf<SnackbarData?>(null) }
    if (data != null) shown = data
    val haptics = LocalHapticFeedback.current
    val accessibility = LocalAccessibilityManager.current

    LaunchedEffect(data) {
        if (data == null) return@LaunchedEffect
        val visuals = data.visuals
        if ((visuals as? Notice)?.kind == NoticeKind.ERROR) haptics.performHapticFeedback(HapticFeedbackType.Reject)
        val base = when (visuals.duration) {
            SnackbarDuration.Short -> 4_000L
            SnackbarDuration.Long -> 8_000L
            SnackbarDuration.Indefinite -> return@LaunchedEffect
        }
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(
            base,
            containsIcons = true,
            containsText = true,
            containsControls = visuals.actionLabel != null,
        ) ?: base
        delay(timeout)
        data.dismiss()
    }

    AnimatedVisibility(
        visible = data != null,
        modifier = modifier,
        enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { it } +
            scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), initialScale = 0.85f) +
            fadeIn(),
        exit = slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { it / 2 } +
            scaleOut(targetScale = 0.9f) +
            fadeOut(),
    ) {
        shown?.let { BoxySnackbar(it) }
    }
}

@Composable
private fun BoxySnackbar(data: SnackbarData) {
    val notice = data.visuals as? Notice
    val kind = notice?.kind ?: NoticeKind.INFO
    val tint = kind.tint()
    val offset = remember(data) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Row(
        Modifier
            .padding(horizontal = PageInset, vertical = 8.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / (size.width.takeIf { it > 0f } ?: 1f)).coerceIn(0f, 1f)
            }
            .pointerInput(data) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offset.value) > size.width / 3f) {
                                offset.animateTo(if (offset.value > 0) size.width.toFloat() else -size.width.toFloat())
                                data.dismiss()
                            } else {
                                offset.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                            }
                        }
                    },
                    onHorizontalDrag = { _, amount -> scope.launch { offset.snapTo(offset.value + amount) } },
                )
            }
            .shadow(14.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Boxy.colors.surface2)
            .border(1.dp, Boxy.colors.outline, RoundedCornerShape(20.dp))
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(tint.bg), contentAlignment = Alignment.Center) {
            Icon(painterResource(kind.icon()), null, Modifier.size(20.dp), tint = tint.fg)
        }
        Text(
            data.visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            color = Boxy.colors.text,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
        )
        data.visuals.actionLabel?.let { label ->
            TextButton(onClick = { data.performAction() }) {
                Text(label, color = tint.fg, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun NoticeKind.tint(): Tint = when (this) {
    NoticeKind.SUCCESS -> Tints.green
    NoticeKind.INFO -> Tints.blue
    NoticeKind.WARNING -> Tints.amber
    NoticeKind.ERROR -> Tints.red
}

private fun NoticeKind.icon(): Int = when (this) {
    NoticeKind.SUCCESS -> R.drawable.ic_check
    NoticeKind.INFO -> R.drawable.ic_info
    NoticeKind.WARNING -> R.drawable.ic_warning
    NoticeKind.ERROR -> R.drawable.ic_close
}
