package io.github.skofqq.dnscryptmanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.skofqq.dnscryptmanager.ui.theme.Boxy
import io.github.skofqq.dnscryptmanager.ui.theme.Tint

/** Label on the left, value on the right: the densest readable form for status data. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Boxy.colors.text2,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Boxy.colors.text2,
        modifier = modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** Title plus supporting line, used by every settings entry that needs an explanation. */
@Composable
fun SettingLabel(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = Boxy.colors.text)
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Boxy.colors.text2,
            )
        }
    }
}

/** Large page title with an optional summary and trailing actions on the same row, as in Boxy. */
@Composable
fun PageHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Boxy.colors.text, maxLines = 1)
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Boxy.colors.text2, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Round 44 dp button for page headers. */
@Composable
fun HeaderAction(
    icon: Painter,
    contentDescription: String?,
    tint: Color = Boxy.colors.text,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .background(Boxy.colors.card)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(22.dp), tint = tint.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

val BoxyCardShape = RoundedCornerShape(26.dp)

/** Flat rounded card on the page colour: the building block of every Boxy screen. */
@Composable
fun BoxyCard(
    modifier: Modifier = Modifier,
    color: Color = Boxy.colors.card,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(BoxyCardShape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Card title with an optional pill badge on the right. */
@Composable
fun CardTitle(title: String, badge: String? = null, tint: Tint? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
            color = Boxy.colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (badge != null && tint != null) {
            Spacer(Modifier.width(6.dp))
            Badge(badge, tint)
        }
    }
}

/** Small tinted pill with bold text. */
@Composable
fun Badge(text: String, tint: Tint, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(tint.bg).padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint.fg, maxLines = 1)
    }
}

/** Soft tinted action button (Start / Stop / Restart in Boxy's hero card). */
@Composable
fun ActionPill(
    text: String,
    tint: Tint,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(tint.bg.copy(alpha = if (enabled) tint.bg.alpha else tint.bg.alpha * 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = tint.fg.copy(alpha = if (enabled) 1f else 0.45f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Solid accent button, the primary action of a sheet or card. */
@Composable
fun PrimaryPill(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Boxy.colors.accent,
    contentColor: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = contentColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 44 dp rounded tile holding a row's icon. */
@Composable
fun IconTile(icon: Painter, tint: Color = Boxy.colors.text, background: Color = Boxy.colors.surface2) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
    }
}

/** Tinted note: icon tile, coloured title and body on a soft background, as in Boxy's notices. */
@Composable
fun NoticeCard(
    title: String,
    body: String,
    tint: Tint,
    icon: Painter,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(tint.bg).padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint = tint.fg, background = tint.bg)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = tint.fg)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Boxy.colors.text)
            }
        }
        if (action != null) {
            Spacer(Modifier.height(10.dp))
            action()
        }
    }
}

data class BarItem(val label: String, val icon: Painter)

/** Floating pill navigation bar with a sliding highlight, Boxy's bottom bar without the glass. */
@Composable
fun BottomBar(items: List<BarItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Boxy.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth(0.94f)
            .height(68.dp)
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.outline, shape),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val itemWidth = maxWidth / items.size
            val position by animateFloatAsState(
                selected.toFloat(),
                spring(dampingRatio = 0.8f, stiffness = 500f),
                label = "indicator",
            )
            Box(
                Modifier
                    .offset(x = itemWidth * position)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(colors.surface2),
            )
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { index, item ->
                    val tint = if (index == selected) colors.accent else colors.text
                    Column(
                        Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clip(shape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(index) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(item.icon, null, Modifier.size(22.dp), tint = tint)
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
