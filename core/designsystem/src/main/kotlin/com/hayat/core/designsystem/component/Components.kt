package com.hayat.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.composed
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.ripple
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hayat.core.designsystem.theme.HayatThemeExt
import com.hayat.core.designsystem.theme.LocalReduceMotion

@Composable
fun HayatCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val ext = com.hayat.core.designsystem.theme.HayatThemeExt.colors
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // Dark theme: frosted "glass" card — translucent fill and a light-catching edge over the backdrop glow.
    val glass = ext.isDark && containerColor == MaterialTheme.colorScheme.surface
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .then(if (glass) Modifier else Modifier.softCard(shape))
            .clip(shape)
            .then(
                if (glass) Modifier.background(GlassFill).border(1.dp, GlassEdge, shape)
                else Modifier.background(containerColor),
            )
            .then(if (onClick != null) Modifier.clickable(interaction, ripple(), role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Translucent glass fill (top slightly brighter) and edge used for dark-theme cards and bars. */
val GlassFill = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x0AFFFFFF)))
val GlassEdge = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x0AFFFFFF)))

/** Light theme: soft shadow (y 4, blur 16, ~6 %). Dark theme: 1 px border. */
fun Modifier.softCard(shape: androidx.compose.ui.graphics.Shape): Modifier = composed {
    val ext = com.hayat.core.designsystem.theme.HayatThemeExt.colors
    if (ext.isDark) border(1.dp, ext.hairline, shape)
    else shadow(6.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.08f))
}

/**
 * App backdrop drawn once behind every screen: solid base plus soft emerald and champagne glows.
 * Gives the glass cards depth without any runtime blur (cheap to draw, no overdraw per card).
 */
fun Modifier.hayatBackdrop(): Modifier = composed {
    val ext = com.hayat.core.designsystem.theme.HayatThemeExt.colors
    val base = MaterialTheme.colorScheme.background
    val emerald = ext.gradientStart
    val gold = ext.gold
    val glowA = if (ext.isDark) 0.20f else 0.06f
    val glowB = if (ext.isDark) 0.09f else 0.06f
    drawBehind {
        drawRect(base)
        val w = size.width
        drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(emerald.copy(alpha = glowA), Color.Transparent), center = Offset(w * 0.05f, -w * 0.15f), radius = w * 1.05f))
        drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(gold.copy(alpha = glowB), Color.Transparent), center = Offset(w * 1.05f, w * 0.05f), radius = w * 0.8f))
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

/** Circular progress with an accessible percentage description. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    stroke: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    description: String,
    center: @Composable () -> Unit = {},
) {
    val reduce = LocalReduceMotion.current
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = tween(if (reduce) 0 else 500, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "ring")
    Box(modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(trackColor, -90f, 360f, false, tl, arc, style = Stroke(s))
            if (animated > 0f) {
                // Round caps extend the arc by half the stroke at each end; inset them so the visible
                // arc covers exactly `animated` of the circle (42 % looks like 42 %).
                val a = ringArc(animated, s, this.size.minDimension)
                drawArc(color, a.start, a.sweep, false, tl, arc, style = Stroke(s, cap = if (a.roundCaps) StrokeCap.Round else StrokeCap.Butt))
            }
        }
        center()
    }
}

/** Horizontal bar whose filled width is exactly [progress] of the track (rounded ends stay inside). */
@Composable
fun LinearMeter(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, description: String, height: Dp = 8.dp,
                trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    val reduce = LocalReduceMotion.current
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (reduce) 0 else 500, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "meter")
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(trackColor, cornerRadius = r)
        if (p > 0f) drawRoundRect(color, size = Size(size.width * p, size.height), cornerRadius = r)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, supporting: String? = null, icon: ImageVector? = null) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp).semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let { Icon(it, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)) }
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.let { Spacer(Modifier.height(16.dp)); it() }
    }
}

@Composable
fun ErrorState(title: String, message: String, retryLabel: String?, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (retryLabel != null && onRetry != null) TextButton(onClick = onRetry) { Text(retryLabel) }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

enum class StatusTone { NEUTRAL, SUCCESS, WARNING, ERROR, INFO }

/** Status is conveyed by icon + text + color, never color alone. */
@Composable
fun StatusChip(label: String, tone: StatusTone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val ext = HayatThemeExt.colors
    val (bg, fg) = when (tone) {
        StatusTone.SUCCESS -> ext.successContainer to ext.onSuccessContainer
        StatusTone.WARNING -> ext.warningContainer to ext.onWarningContainer
        StatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusTone.INFO -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, Modifier.size(14.dp), tint = fg); Spacer(Modifier.width(4.dp)) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(16.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.let { Spacer(Modifier.width(12.dp)); it() }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, icon: ImageVector? = null, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(16.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, tone: StatusTone = StatusTone.INFO, action: (@Composable () -> Unit)? = null) {
    val ext = HayatThemeExt.colors
    val (bg, fg) = when (tone) {
        StatusTone.WARNING -> ext.warningContainer to ext.onWarningContainer
        StatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(modifier.fillMaxWidth(), color = bg, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(if (tone == StatusTone.INFO) Icons.Outlined.Info else Icons.Outlined.ErrorOutline, null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.weight(1f))
            }
            action?.let { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { it() } }
        }
    }
}

/** Accessible weekday toggles: each day is a 48dp toggle with state description. */
@Composable
fun <T> DayToggleRow(days: List<T>, isSelected: (T) -> Boolean, label: (T) -> String, fullLabel: (T) -> String, onToggle: (T) -> Unit, modifier: Modifier = Modifier, selectedText: String, notSelectedText: String) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { d ->
            val selected = isSelected(d)
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .toggleable(selected, role = Role.Checkbox) { onToggle(d) }
                    .semantics { contentDescription = fullLabel(d); stateDescription = if (selected) selectedText else notSelectedText },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(d), style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun ColorSwatchPicker(colors: List<Long>, selected: Long, onSelect: (Long) -> Unit, describe: (Int) -> String, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEachIndexed { i, c ->
            val isSel = c == selected
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                    .then(if (isSel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable(role = Role.RadioButton) { onSelect(c) }
                    .semantics { contentDescription = describe(i); stateDescription = if (isSel) "✓" else "" },
                contentAlignment = Alignment.Center,
            ) { if (isSel) Icon(Icons.Outlined.Check, null, tint = Color.White) }
        }
    }
}

@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

/** Simple accessible bar chart. [summary] is read by screen readers instead of individual bars. */
@Composable
fun BarChart(values: List<Float>, labels: List<String>, summary: String, modifier: Modifier = Modifier, goal: Float? = null, height: Dp = 140.dp) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val goalColor = HayatThemeExt.colors.warning
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(goal ?: 0f).coerceAtLeast(1f)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = summary }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barW = slot * 0.56f
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 3))
                val h = size.height * (v / max)
                if (h > 0f) drawRoundRect(primary, Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 3))
            }
            goal?.let { g ->
                val y = size.height - size.height * (g / max)
                drawLine(goalColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}

/** Contribution-style heatmap cells; [levels] in 0..1. */
@Composable
fun HeatmapRow(levels: List<Float>, summary: String, modifier: Modifier = Modifier, cell: Dp = 14.dp) {
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    FlowRow(modifier.semantics { contentDescription = summary }, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        levels.forEach { l ->
            Box(Modifier.size(cell).clip(RoundedCornerShape(3.dp)).background(if (l <= 0f) empty else base.copy(alpha = 0.25f + 0.75f * l.coerceIn(0f, 1f))))
        }
    }
}

/** Large tap target for counters (zikr, water). */
@Composable
fun CounterButton(count: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 220.dp, contentDescription: String) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(count, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 40.dp), textAlign = TextAlign.End)
    }
}

/** Arc geometry for [ProgressRing]. */
data class RingArc(val start: Float, val sweep: Float, val roundCaps: Boolean) {
    /** Angle actually covered on screen, including the round caps. */
    fun visibleDegrees(strokePx: Float, sizePx: Float): Float {
        if (!roundCaps) return sweep
        val radius = (sizePx - strokePx) / 2f
        return sweep + 2 * Math.toDegrees((strokePx / 2f / radius).toDouble()).toFloat()
    }
}

/**
 * Round caps add half the stroke at both ends, which makes 42 % look like ~48 %. The arc is
 * shortened by the cap angle so the *visible* coloured part equals [progress] exactly.
 */
fun ringArc(progress: Float, strokePx: Float, sizePx: Float): RingArc {
    val p = progress.coerceIn(0f, 1f)
    val radius = (sizePx - strokePx) / 2f
    val capDeg = Math.toDegrees((strokePx / 2f / radius).toDouble()).toFloat()
    val sweep = 360f * p
    return when {
        sweep >= 360f - 0.01f -> RingArc(-90f, 360f, false)
        sweep > 2 * capDeg -> RingArc(-90f + capDeg, sweep - 2 * capDeg, true)
        else -> RingArc(-90f, sweep, false)
    }
}
