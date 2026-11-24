package com.hayat.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hayat.core.designsystem.theme.HayatThemeExt
import com.hayat.core.designsystem.theme.LocalReduceMotion
import kotlin.math.roundToInt

/**
 * Deep "ink" surface — the premium, high-contrast card used for hero content. Content colour
 * defaults to [HayatExtendedColors.onInk]. A hairline edge keeps it distinct in dark mode.
 */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val ext = HayatThemeExt.colors
    val shape = RoundedCornerShape(28.dp)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(interaction, 0.98f) else Modifier)
            .clip(shape).background(ext.ink).border(1.dp, ext.hairline, shape)
            .then(if (onClick != null) Modifier.clickable(interaction, ripple(color = ext.onInk), role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
    ) { CompositionLocalProvider(LocalContentColor provides ext.onInk) { content() } }
}

/** Small pill label on ink (e.g. a date or streak tag). */
@Composable
fun InkTag(text: String, modifier: Modifier = Modifier) {
    val ext = HayatThemeExt.colors
    Text(
        text, modifier.clip(RoundedCornerShape(8.dp)).background(ext.inkAccent).padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall, color = ext.ink, maxLines = 1,
    )
}

/**
 * Bar-code style progress: [segments] rounded bars, filled from the left. The fill sweeps in on
 * first display (skipped with reduced motion). Announced as [description].
 */
@Composable
fun SegmentedProgress(
    progress: Float,
    description: String,
    modifier: Modifier = Modifier,
    segments: Int = 22,
    height: Dp = 30.dp,
    filled: Color = HayatThemeExt.colors.inkAccent,
    track: Color = HayatThemeExt.colors.onInk.copy(alpha = 0.12f),
) {
    val reduce = LocalReduceMotion.current
    val anim = remember { Animatable(if (reduce) progress else 0f) }
    LaunchedEffect(progress) { if (reduce) anim.snapTo(progress) else anim.animateTo(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing)) }
    val lit = anim.value * segments
    Row(modifier.height(height).semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(segments) { i ->
            // Partially lit edge segment blends between track and fill for a smooth sweep.
            val amount = (lit - i).coerceIn(0f, 1f)
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(lerp(track, filled, amount)))
        }
    }
}

/** Label + supporting line + value, as used in ink stat panels. */
@Composable
fun InkStat(label: String, value: String, modifier: Modifier = Modifier, supporting: String? = null) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        supporting?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.6f), maxLines = 1) }
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), modifier = Modifier.padding(top = 6.dp), maxLines = 1)
    }
}

/** Rounds a 0..1 fraction to a whole percent for display. */
fun percentOf(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 100).roundToInt()
