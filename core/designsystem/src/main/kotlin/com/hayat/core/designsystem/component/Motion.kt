package com.hayat.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import com.hayat.core.designsystem.theme.LocalReduceMotion

/** Subtle spring scale-down while pressed. Pass the same [interaction] to the clickable. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !reduce) pressedScale else 1f,
        tween(durationMillis = if (pressed) 120 else 180, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * One-time entrance: fades and slides content up, staggered by [index]. Skipped when the
 * user prefers reduced motion. Runs once per composition key (lists keep it per item).
 */
fun Modifier.animateEnter(index: Int = 0): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) progress.animateTo(1f, tween(durationMillis = 220, delayMillis = (index.coerceAtMost(6)) * 30, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 16f
    }
}

/** Number that counts to its new value. [format] lets callers apply Bengali digits. */
@Composable
fun AnimatedCount(value: Int, style: TextStyle, format: (Int) -> String = { it.toString() }, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val shown by animateIntAsState(value, tween(if (reduce) 0 else 500, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "count")
    Text(format(shown), style = style, modifier = modifier)
}
