package io.github.skofqq.dnscryptmanager.ui.components

import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.ui.theme.Boxy
import io.github.skofqq.dnscryptmanager.ui.theme.Tint

/*
 * One grid for every screen, measured off Boxy: cards 16 dp from the screen edge,
 * content 16 dp inside a card (32 dp from the edge), loose text 20 dp from the edge
 * so it lines up with the page title.
 */
val PageInset: Dp = 16.dp
val CardInset: Dp = 16.dp
val TextInset: Dp = 20.dp

/**
 * Space the floating bottom bar takes over the content. Scrolling screens end with this
 * much padding, so their last item can be scrolled clear of the bar.
 */
val LocalBarPadding = compositionLocalOf { 0.dp }

/** Text on an accent fill: white in light mode, near-black on the light dark-mode accent (WCAG AA). */
@Composable
fun onAccent(): Color = if (Boxy.colors.isDark) Color(0xFF0A0C0F) else Color.White

/** Boxy's settings group: title and summary inside the card, rows below. */
@Composable
fun SectionCard(
    title: String?,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Boxy.colors.card)
            .padding(vertical = 14.dp),
    ) {
        if (title != null) {
            Column(Modifier.padding(horizontal = CardInset)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Boxy.colors.text)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Boxy.colors.text2)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

@Composable
private fun RowTexts(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    modifier: Modifier,
    titleBadge: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Boxy.colors.text.copy(alpha = if (enabled) 1f else 0.45f),
                modifier = Modifier.weight(1f, fill = false),
            )
            titleBadge?.invoke()
        }
        if (!subtitle.isNullOrEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Boxy.colors.text2.copy(alpha = if (enabled) 1f else 0.45f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Hairline between rows, starting under the row text as in Boxy. */
@Composable
fun RowDivider() {
    HorizontalDivider(
        Modifier.padding(start = CardInset + 44.dp + 14.dp, end = CardInset),
        color = Boxy.colors.outline,
    )
}

/** Navigation row: icon tile, title, value line, then a badge or a chevron. */
@Composable
fun NavRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    showDivider: Boolean = true,
    iconTint: Color = Boxy.colors.text,
    enabled: Boolean = true,
    titleBadge: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = CardInset, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, tint = iconTint.copy(alpha = if (enabled) 1f else 0.4f))
            Spacer(Modifier.width(14.dp))
            RowTexts(title, subtitle, enabled, Modifier.weight(1f), titleBadge)
            Spacer(Modifier.width(8.dp))
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    null,
                    Modifier.size(20.dp),
                    tint = Boxy.colors.text2.copy(alpha = 0.75f),
                )
            }
        }
        if (showDivider) RowDivider()
    }
}

/** Switch row with an icon tile, as in Boxy's settings. */
@Composable
fun SwitchRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = CardInset, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, tint = Boxy.colors.text.copy(alpha = if (enabled) 1f else 0.4f))
            Spacer(Modifier.width(14.dp))
            RowTexts(title, subtitle, enabled, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Boxy.colors.accent,
                    checkedThumbColor = onAccent(),
                ),
            )
        }
        if (showDivider) RowDivider()
    }
}

/** Back pill shown above a sub-page title. */
@Composable
fun BackPill(onBack: () -> Unit) {
    Row(
        Modifier
            .padding(start = PageInset, top = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Boxy.colors.card)
            .clickable(onClick = onBack)
            .padding(start = 12.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_arrow_back), null, Modifier.size(20.dp), tint = Boxy.colors.text)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.action_back), style = MaterialTheme.typography.labelLarge, color = Boxy.colors.text)
    }
}

/** Sub-page header: back pill, then the usual large title with round actions. */
@Composable
fun SubPageHeader(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        BackPill(onBack)
        PageHeader(title, subtitle, actions = actions)
    }
}

/** Pill chip: neutral card colour when off, tinted with [color] when [active]. */
@Composable
fun BoxyChip(
    text: String,
    active: Boolean,
    color: Color = Boxy.colors.accent,
    trailing: Painter? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) color.copy(alpha = 0.16f) else Boxy.colors.card)
            .clickable(onClick = onClick)
            // Fixed height: chips with and without the trailing arrow line up in one row.
            .height(40.dp)
            .padding(start = 14.dp, end = if (trailing != null) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = if (active) color else Boxy.colors.text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
        if (trailing != null) {
            Icon(trailing, null, Modifier.padding(start = 2.dp).size(20.dp), tint = if (active) color else Boxy.colors.text2)
        }
    }
}

/** Soft fade under the pinned page title, so content does not end in a hard cut. */
@Composable
fun TopFade(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(16.dp)
            .background(Brush.verticalGradient(listOf(Boxy.colors.page, Boxy.colors.page.copy(alpha = 0f)))),
    )
}

data class Choice<T>(val value: T, val title: String, val subtitle: String? = null)

/** Boxy's single-choice dialog: one rounded tile per option, the chosen one tinted. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    titleBadge: (@Composable () -> Unit)? = null,
    text: String? = null,
) {
    BoxyDialog(
        title = title,
        onDismiss = onDismiss,
        text = text,
        dismissText = stringResource(R.string.action_cancel),
        titleBadge = titleBadge,
    ) {
        Column(
            Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            choices.forEach { choice ->
                val isSelected = choice.value == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Boxy.colors.accent.copy(alpha = 0.14f) else Boxy.colors.surface2)
                        .clickable {
                            onSelect(choice.value)
                            onDismiss()
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(choice.title, style = MaterialTheme.typography.titleMedium, color = Boxy.colors.text)
                        if (choice.subtitle != null) {
                            Text(choice.subtitle, style = MaterialTheme.typography.bodySmall, color = Boxy.colors.text2)
                        }
                    }
                    if (isSelected) Icon(painterResource(R.drawable.ic_check), null, tint = Boxy.colors.accent)
                }
            }
        }
    }
}

/**
 * Boxy-style dialog: semibold title, body, then pills. [confirmText] is optional; a
 * [confirmTint] of red marks a destructive action.
 */
@Composable
fun BoxyDialog(
    title: String,
    onDismiss: () -> Unit,
    text: String? = null,
    dismissText: String? = null,
    confirmText: String? = null,
    confirmTint: Tint? = null,
    onConfirm: () -> Unit = {},
    titleBadge: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Boxy.colors.card)
                .padding(22.dp),
        ) {
            if (titleBadge == null) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Boxy.colors.text)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Boxy.colors.text,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    titleBadge()
                }
            }
            if (text != null) {
                Spacer(Modifier.height(10.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = Boxy.colors.text2)
            }
            if (content != null) {
                Spacer(Modifier.height(14.dp))
                content()
            }
            if (dismissText != null || confirmText != null) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (dismissText != null) {
                        PrimaryPill(
                            dismissText,
                            Modifier.weight(1f),
                            color = Boxy.colors.surface2,
                            contentColor = Boxy.colors.text,
                            onClick = onDismiss,
                        )
                    }
                    if (confirmText != null) {
                        if (confirmTint != null) {
                            ActionPill(confirmText, confirmTint, enabled = true, modifier = Modifier.weight(1f)) {
                                onConfirm()
                            }
                        } else {
                            PrimaryPill(confirmText, Modifier.weight(1f), onClick = onConfirm)
                        }
                    }
                }
            }
        }
    }
}

/** Pill search field: icon, hint, an optional counter on the right and a clear button. */
@Composable
fun SearchField(
    query: String,
    hint: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(Boxy.colors.card)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), null, Modifier.size(20.dp), tint = Boxy.colors.text2)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(hint, style = MaterialTheme.typography.bodyLarge, color = Boxy.colors.text2, maxLines = 1)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Boxy.colors.text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Boxy.colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailingText != null) {
            Text(
                trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = Boxy.colors.text2,
                modifier = Modifier.padding(horizontal = 6.dp),
                maxLines = 1,
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable { onQuery("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    stringResource(R.string.action_clear),
                    Modifier.size(18.dp),
                    tint = Boxy.colors.text2,
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Floating pair of pills (discard / apply) used for pending edits, above the bottom bar. */
@Composable
fun PendingBar(
    label: String,
    applying: Boolean,
    applyText: String,
    onDiscard: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PageInset, vertical = 8.dp)
            // Floats over cards of the same colour: the shadow and hairline keep it apart.
            .shadow(16.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(Boxy.colors.card)
            .border(1.dp, Boxy.colors.outline, RoundedCornerShape(26.dp))
            .padding(10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = Boxy.colors.text2,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        // Progress stays inside the card: drawn outside it, it crossed the rows behind.
        if (applying) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryPill(
                stringResource(R.string.action_cancel),
                Modifier.weight(1f),
                enabled = !applying,
                color = Boxy.colors.surface2,
                contentColor = Boxy.colors.text,
                onClick = onDiscard,
            )
            PrimaryPill(applyText, Modifier.weight(1f), enabled = !applying, onClick = onApply)
        }
    }
}
