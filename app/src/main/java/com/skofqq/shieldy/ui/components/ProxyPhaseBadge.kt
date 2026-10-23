package com.skofqq.shieldy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.skofqq.shieldy.R
import com.skofqq.shieldy.ui.theme.Tint
import com.skofqq.shieldy.ui.theme.Tints

/** The three states the proxy can be in, as far as the user is concerned. */
enum class ProxyPhase { RUNNING, STOPPED, RESTARTING }

/**
 * Morphs between three Material shapes as the proxy changes state. Colour alone would
 * not survive colour blindness, so shape and icon carry the same message.
 */
@Composable
fun ProxyPhaseBadge(
    phase: ProxyPhase,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 96.dp,
) {
    val target = remember(phase) { polygonFor(phase) }
    var previous by remember { mutableStateOf(target) }
    val morph = remember(phase) { Morph(previous, target) }
    val progress = remember(phase) { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(phase) {
        progress.animateTo(1f, spec)
        previous = target
    }

    val rotation = if (phase == ProxyPhase.RESTARTING) {
        val transition = rememberInfiniteTransition(label = "restart-spin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "restart-angle",
        )
        angle
    } else {
        0f
    }

    val container = phaseContainerColor(phase)
    val content = phaseContentColor(phase)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotation)
                .clip(MorphShape(morph, progress.value))
                .background(container)
        )
        when (phase) {
            ProxyPhase.RUNNING -> Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = stringResource(R.string.status_running),
                tint = content,
                modifier = Modifier.size(size / 2.4f),
            )

            ProxyPhase.STOPPED -> Icon(
                painter = painterResource(R.drawable.ic_stop),
                contentDescription = stringResource(R.string.status_stopped),
                tint = content,
                modifier = Modifier.size(size / 2.4f),
            )

            ProxyPhase.RESTARTING -> Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = stringResource(R.string.status_restarting),
                tint = content,
                modifier = Modifier.size(size / 2.4f),
            )
        }
    }
}

@Composable
fun phaseColor(phase: ProxyPhase): Color = phaseTint(phase).fg

/** Boxy's status colours: green running, red stopped, amber in between. */
@Composable
fun phaseTint(phase: ProxyPhase): Tint = when (phase) {
    ProxyPhase.RUNNING -> Tints.green
    ProxyPhase.STOPPED -> Tints.red
    ProxyPhase.RESTARTING -> Tints.amber
}

@Composable
fun phaseContainerColor(phase: ProxyPhase): Color = phaseTint(phase).bg

@Composable
fun phaseContentColor(phase: ProxyPhase): Color = phaseTint(phase).fg

private fun polygonFor(phase: ProxyPhase): RoundedPolygon = when (phase) {
    ProxyPhase.RUNNING -> androidx.compose.material3.MaterialShapes.Cookie9Sided
    ProxyPhase.STOPPED -> androidx.compose.material3.MaterialShapes.Square
    ProxyPhase.RESTARTING -> androidx.compose.material3.MaterialShapes.Clover4Leaf
}

/** Draws a morph between two polygons, scaled to whatever size it is given. */
private class MorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toPath(progress)
        val bounds = path.getBounds()
        if (bounds.width <= 0f || bounds.height <= 0f) return Outline.Generic(path)

        val scale = Matrix().apply { scale(size.width / bounds.width, size.height / bounds.height) }
        path.transform(scale)
        val scaled = path.getBounds()
        val move = Matrix().apply { translate(-scaled.left, -scaled.top) }
        path.transform(move)
        return Outline.Generic(path)
    }
}
