package com.hayat.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hayat.core.designsystem.theme.HayatThemeExt
import com.hayat.core.designsystem.theme.LocalReduceMotion

/** Circle with the user's initial (or an icon) — like the avatar in a greeting header. */
@Composable
fun InitialAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val ext = HayatThemeExt.colors
    Box(modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(ext.gradientStart, ext.gradientEnd))), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "☺", style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

/** Round icon button with an optional unread dot (e.g. announcements bell). */
@Composable
fun BadgeIconButton(icon: ImageVector, contentDescription: String, showDot: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.size(48.dp).pressScale(interaction).shadow(6.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary.copy(0.06f), spotColor = MaterialTheme.colorScheme.primary.copy(0.1f))
            .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(interaction, ripple(), role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
        if (showDot) Box(Modifier.align(Alignment.TopEnd).offset((-11).dp, 11.dp).size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
    }
}

/** Large rounded call-to-action with the brand gradient. */
@Composable
fun GradientCta(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: ImageVector? = null) {
    val ext = HayatThemeExt.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(interaction)
            .shadow(10.dp, RoundedCornerShape(50), ambientColor = ext.gradientStart.copy(0.25f), spotColor = ext.gradientStart.copy(0.35f))
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(ext.gradientStart, ext.gradientEnd)))
            .clickable(interaction, ripple(color = Color.White), role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = Color.White); Spacer(Modifier.width(10.dp)) }
        Text(text, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.let { Icon(it, null, tint = Color.White.copy(alpha = 0.9f)) }
    }
}

/** A labelled metric row with a tinted circular icon (used beside the score ring). */
data class Metric(val icon: ImageVector, val label: String, val value: String, val tone: Pair<Color, Color>)

@Composable
fun MetricRow(m: Metric, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 36.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(m.tone.first), contentAlignment = Alignment.Center) {
            Icon(m.icon, null, Modifier.size(17.dp), tint = m.tone.second)
        }
        Spacer(Modifier.width(10.dp))
        Text(m.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(m.value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Score ring + breakdown, like a health-report card. */
@Composable
fun ScoreCard(
    title: String,
    progress: Float,
    centerValue: String,
    centerLabel: String,
    description: String,
    metrics: List<Metric>,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val ext = HayatThemeExt.colors
    HayatCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            action?.invoke()
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress, size = 128.dp, stroke = 14.dp, description = description,
                color = ext.gradientStart, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(centerValue, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { metrics.forEach { MetricRow(it) } }
        }
    }
}

/** A day in the week strip. */
data class DayChip(val key: String, val top: String, val bottom: String, val isToday: Boolean, val done: Boolean, val selected: Boolean)

/** Horizontal week strip: past days show a check when done, today is highlighted. */
@Composable
fun DayStrip(days: List<DayChip>, onSelect: (String) -> Unit, modifier: Modifier = Modifier, doneLabel: String, todayLabel: String) {
    val ext = HayatThemeExt.colors
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)) {
        itemsIndexed(days, key = { _, d -> d.key }) { i, d ->
            val highlighted = d.selected || (d.isToday && days.none { it.selected })
            val bg by animateColorAsState(if (highlighted) ext.gradientStart else MaterialTheme.colorScheme.surfaceContainerLow, label = "day")
            val fg = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurface
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier.animateEnter(i).width(58.dp).pressScale(interaction).clip(RoundedCornerShape(29.dp)).background(bg)
                    .clickable(interaction, ripple(), role = Role.Button) { onSelect(d.key) }
                    .semantics(mergeDescendants = true) { stateDescription = listOfNotNull(if (d.isToday) todayLabel else null, if (d.done) doneLabel else null).joinToString(", ") }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(d.top, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.8f))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(32.dp).clip(CircleShape).background(if (highlighted) Color.White.copy(0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    if (d.done) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = fg) else Text(d.bottom, style = MaterialTheme.typography.labelLarge, color = fg)
                }
            }
        }
    }
}

/** Pill-shaped segmented control with an animated sliding indicator. */
@Composable
fun PillTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val dark = HayatThemeExt.colors.isDark
    val track = if (dark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val thumb = if (dark) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface
    BoxWithConstraints(modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(track).padding(4.dp)) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val offset by animateDpAsState(segment * selected, tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "tab")
        Box(Modifier.offset(x = offset).width(segment).height(40.dp).shadow(if (dark) 0.dp else 3.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(thumb))
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Tab) { onSelect(i) }
                        .semantics { stateDescription = if (i == selected) "✓" else "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

/** Vertical timeline row: check circle + connector line on the left, content card on the right. */
@Composable
fun TimelineEntry(done: Boolean, isLast: Boolean, onToggle: () -> Unit, toggleLabel: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ext = HayatThemeExt.colors
    val circle by animateColorAsState(if (done) ext.success else MaterialTheme.colorScheme.surfaceContainerHighest, label = "check")
    Row(modifier.fillMaxWidth().heightIn(min = 76.dp)) {
        Box(Modifier.width(36.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!isLast) Box(Modifier.padding(top = 30.dp).width(2.dp).height(64.dp).background(if (done) ext.success else MaterialTheme.colorScheme.outlineVariant))
            Box(
                Modifier.padding(top = 18.dp).size(28.dp).clip(CircleShape).background(circle).clickable(role = Role.Checkbox, onClickLabel = toggleLabel, onClick = onToggle)
                    .semantics { stateDescription = if (done) "✓" else "" },
                contentAlignment = Alignment.Center,
            ) { if (done) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = Color.White) }
        }
        Spacer(Modifier.width(8.dp))
        HayatCard(Modifier.weight(1f).padding(bottom = 10.dp), contentPadding = PaddingValues(14.dp), content = content)
    }
}

/** Colourful tile with a big number, like "10 Alarms Set". */
@Composable
fun PastelStat(value: @Composable () -> Unit, label: String, tone: Pair<Color, Color>, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.then(if (onClick != null) Modifier.pressScale(interaction) else Modifier).clip(MaterialTheme.shapes.large).background(tone.first)
            .then(if (onClick != null) Modifier.clickable(interaction, ripple(), role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}.padding(16.dp),
    ) {
        icon?.let { Icon(it, null, tint = tone.second, modifier = Modifier.size(22.dp)); Spacer(Modifier.height(8.dp)) }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides tone.second) { value() }
        Text(label, style = MaterialTheme.typography.labelLarge, color = tone.second.copy(alpha = 0.85f), maxLines = 2)
    }
}

/** Achievement pill (earned achievements only — computed from real records). */
@Composable
fun AchievementChip(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    val tone = HayatThemeExt.colors.lavender
    Row(modifier.clip(RoundedCornerShape(50)).background(tone.first).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = tone.second)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tone.second)
    }
}

/** Smooth line chart with soft area fill. [summary] is announced to screen readers. */
@Composable
fun SmoothLineChart(values: List<Float>, labels: List<String>, summary: String, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    val ext = HayatThemeExt.colors
    val line = ext.gradientStart
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = summary }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.size < 2) return@Canvas
            val max = values.max().coerceAtLeast(1f)
            val stepX = size.width / (values.size - 1)
            val pts = values.mapIndexed { i, v -> Offset(i * stepX, size.height - (v / max) * (size.height * 0.85f) - size.height * 0.05f) }
            repeat(4) { r -> val y = size.height * (r + 1) / 5f; drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f) }
            val path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                // Straight segments: a curve could overshoot and suggest values that were never recorded.
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            }
            val area = Path().apply { addPath(path); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close() }
            drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), Color.Transparent)))
            drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            pts.forEach { drawCircle(Color.White, 4.dp.toPx(), it); drawCircle(line, 4.dp.toPx(), it, style = Stroke(2.dp.toPx())) }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}

/**
 * Original illustration: a gently animated sky for the greeting card — sun by day, moon and
 * stars at night. Drawn with Canvas (no image assets, works offline, tiny).
 */
@Composable
fun SkyScene(hour: Int, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val night = hour < 5 || hour >= 19
    val transition = rememberInfiniteTransition(label = "sky")
    val float by transition.animateFloat(0f, if (reduce) 0f else 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "float")
    val sky = when {
        night -> listOf(Color(0xFF1E2A4A), Color(0xFF3B3F78))
        hour < 8 -> listOf(Color(0xFFFFD3A5), Color(0xFFFFE9C9))
        hour < 17 -> listOf(Color(0xFFBFE7D6), Color(0xFFE6F6EE))
        else -> listOf(Color(0xFFFFB79A), Color(0xFFD9B8F5))
    }
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(sky))
        val bob = float * 6.dp.toPx()
        if (night) {
            val moon = Offset(size.width * 0.78f, size.height * 0.38f + bob)
            drawCircle(Color(0xFFFFF6D8), size.minDimension * 0.16f, moon)
            drawCircle(sky.first(), size.minDimension * 0.15f, moon + Offset(size.minDimension * 0.07f, -size.minDimension * 0.04f))
            listOf(0.92f to 0.15f, 0.6f to 0.12f, 0.95f to 0.62f, 0.55f to 0.7f, 0.66f to 0.42f).forEachIndexed { i, (x, y) ->
                drawCircle(Color.White.copy(alpha = 0.5f + 0.5f * if (i % 2 == 0) float else 1 - float), 2.dp.toPx(), Offset(size.width * x, size.height * y))
            }
        } else {
            val sun = Offset(size.width * 0.78f, size.height * 0.4f + bob)
            drawCircle(Color(0xFFFFC94A).copy(alpha = 0.25f), size.minDimension * 0.26f, sun)
            drawCircle(Color(0xFFFFB627), size.minDimension * 0.16f, sun)
            fun cloud(cx: Float, cy: Float, s: Float) {
                drawCircle(Color.White.copy(alpha = 0.9f), s, Offset(cx, cy))
                drawCircle(Color.White.copy(alpha = 0.9f), s * 0.8f, Offset(cx + s, cy + s * 0.2f))
                drawCircle(Color.White.copy(alpha = 0.9f), s * 0.7f, Offset(cx - s, cy + s * 0.25f))
            }
            cloud(size.width * 0.55f + float * 10.dp.toPx(), size.height * 0.62f, size.minDimension * 0.09f)
        }
        // Rolling hills in brand greens.
        val hill = Path().apply {
            moveTo(0f, size.height * 0.82f)
            cubicTo(size.width * 0.3f, size.height * 0.65f, size.width * 0.6f, size.height * 0.95f, size.width, size.height * 0.75f)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(hill, if (night) Color(0xFF16302A) else Color(0xFF7FD4AE))
    }
}

/** Card with the sky illustration behind a headline (greeting hero). */
@Composable
fun SkyHeroCard(hour: Int, title: String, subtitle: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit = {}) {
    val night = hour < 5 || hour >= 19
    Box(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).heightIn(min = 150.dp)) {
        SkyScene(hour, Modifier.matchParentSize())
        Column(Modifier.padding(20.dp).padding(bottom = 28.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(0.7f), color = if (night) Color.White else Color(0xFF14231C))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = if (night) Color.White.copy(0.85f) else Color(0xFF2E4A3E), modifier = Modifier.fillMaxWidth(0.65f))
            content()
        }
    }
}

