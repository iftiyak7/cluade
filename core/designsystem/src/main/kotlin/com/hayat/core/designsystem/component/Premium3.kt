package com.hayat.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hayat.core.designsystem.theme.HayatThemeExt
import com.hayat.core.designsystem.theme.LocalReduceMotion
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class ButtonVariant { PRIMARY, TONAL, TEXT }

/**
 * The one button of the design system: 52 dp high, 16 dp radius, semibold label. Press scales
 * to 0.97 with a light haptic tick. [loading] keeps the size and shows a spinner; it also blocks
 * clicks. [onLongClick] is optional (e.g. custom amount).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HayatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    height: Dp = 52.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val active = enabled && !loading
    val (bg, fg) = when (variant) {
        ButtonVariant.PRIMARY -> scheme.primary to scheme.onPrimary
        ButtonVariant.TONAL -> scheme.primaryContainer to scheme.onPrimaryContainer
        ButtonVariant.TEXT -> Color.Transparent to scheme.primary
    }
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier
            .heightIn(min = maxOf(height, 48.dp))
            .pressScale(interaction)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(bg)
            .combinedClickable(
                interactionSource = interaction, indication = ripple(color = fg), enabled = active, role = Role.Button,
                onClickLabel = null, onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick?.let { lc -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); lc() } },
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.VirtualKey); onClick() },
            )
            .semantics { if (loading) stateDescription = "…" }
            .padding(horizontal = if (variant == ButtonVariant.TEXT) 12.dp else 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = fg, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(22.dp), tint = fg)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Shimmering placeholder block used instead of spinners while content loads. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large) {
    val reduce = LocalReduceMotion.current
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val hi = MaterialTheme.colorScheme.surfaceContainerLowest
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(0f, if (reduce) 0f else 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "x")
    Box(
        modifier.clip(shape).background(
            Brush.linearGradient(listOf(base, hi.copy(alpha = 0.6f), base), start = Offset(x * 1600f - 600f, 0f), end = Offset(x * 1600f, 0f)),
        ),
    )
}

/** Generic page skeleton: header line, hero block and three cards. */
@Composable
fun SkeletonPage(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp).semantics { contentDescription = "Loading" }, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Skeleton(Modifier.width(180.dp).height(22.dp), RoundedCornerShape(8.dp))
        Skeleton(Modifier.fillMaxWidth().height(170.dp))
        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(84.dp)) }
    }
}

/** Small coloured dot with an accessible label (e.g. server connection status). */
@Composable
fun StatusDot(color: Color, description: String, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color).border(2.dp, MaterialTheme.colorScheme.background, CircleShape).semantics { contentDescription = description })
}

/**
 * Full 7-day week that always fits the screen width, with a month label and chevrons; swipe for
 * previous/next week. [marked] days get a small dot (e.g. all done).
 */
@Composable
fun WeekStrip(
    today: LocalDate,
    selected: LocalDate?,
    firstDayOfWeek: DayOfWeek,
    onSelect: (LocalDate) -> Unit,
    monthLabel: (LocalDate) -> String,
    weekdayLabel: (DayOfWeek) -> String,
    dayLabel: (LocalDate) -> String,
    previousLabel: String,
    nextLabel: String,
    todayLabel: String,
    modifier: Modifier = Modifier,
    marked: Set<LocalDate> = emptySet(),
) {
    val pages = 105 // ±52 weeks around this week
    val center = 52
    val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val pager = rememberPagerState(initialPage = center) { pages }
    val scope = rememberCoroutineScope()
    val shownStart = thisWeekStart.plusWeeks((pager.currentPage - center).toLong())
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(monthLabel(shownStart.plusDays(3)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) { Icon(Icons.Outlined.ChevronLeft, previousLabel) }
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) { Icon(Icons.Outlined.ChevronRight, nextLabel) }
        }
        HorizontalPager(pager, Modifier.fillMaxWidth(), beyondViewportPageCount = 0) { page ->
            val start = thisWeekStart.plusWeeks((page - center).toLong())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 7).forEach { i ->
                    val d = start.plusDays(i.toLong())
                    val isSel = d == (selected ?: today)
                    val isToday = d == today
                    val bg by animateColorAsState(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent, tween(180), label = "wd")
                    val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(bg)
                            .then(if (!isSel && isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
                            .clickable(role = Role.Button) { onSelect(d) }
                            .semantics { this.selected = isSel; if (isToday) stateDescription = todayLabel }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(weekdayLabel(d.dayOfWeek), style = MaterialTheme.typography.labelMedium, color = if (isSel) fg else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(dayLabel(d), style = MaterialTheme.typography.titleMedium, color = fg)
                        Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(if (d in marked) (if (isSel) fg else MaterialTheme.colorScheme.primary) else Color.Transparent))
                    }
                }
            }
        }
    }
    LaunchedEffect(today) { if (ChronoUnit.WEEKS.between(thisWeekStart, today) != 0L) pager.scrollToPage(center) }
}

/**
 * Bar chart with exact bar heights, horizontal y-axis guides (0, ½, max) and their values, and
 * one x label per bar. [summary] is announced to screen readers.
 */
@Composable
fun ValueBarChart(
    values: List<Float>,
    labels: List<String>,
    summary: String,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    axisLabel: (Float) -> String = { it.toInt().toString() },
    highlightIndex: Int = -1,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val reduce = LocalReduceMotion.current
    val grow by animateFloatAsState(1f, tween(if (reduce) 0 else 450, easing = FastOutSlowInEasing), label = "bars")
    val grid = MaterialTheme.colorScheme.outlineVariant
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val max = (values.maxOrNull() ?: 0f).let { if (it <= 0f) 1f else it }
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = summary }) {
        Row(Modifier.fillMaxWidth().height(height)) {
            Column(Modifier.fillMaxSize().weight(1f)) {
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    listOf(0f, 0.5f, 1f).forEach { f ->
                        val y = size.height - f * size.height
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, pathEffect = if (f == 0f) null else dash)
                    }
                    if (values.isEmpty()) return@Canvas
                    val slot = size.width / values.size
                    val barW = (slot * 0.56f).coerceAtMost(28.dp.toPx())
                    values.forEachIndexed { i, v ->
                        val x = slot * i + (slot - barW) / 2
                        drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2.5f))
                        val h = size.height * (v / max) * grow
                        if (h > 0f) drawRoundRect(if (i == highlightIndex) color else color.copy(alpha = 0.72f), Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2.5f))
                    }
                }
            }
            Column(Modifier.width(40.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                Text(axisLabel(max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(axisLabel(max / 2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(axisLabel(0f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = 40.dp, top = 6.dp)) {
            labels.forEachIndexed { i, l ->
                Text(l, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1,
                    color = if (i == highlightIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == highlightIndex) FontWeight.SemiBold else null)
            }
        }
    }
}

/** Achievement medallion (gold ring) — earned badges are coloured, locked ones are muted. */
@Composable
fun AchievementBadge(icon: ImageVector, label: String, earned: Boolean, modifier: Modifier = Modifier, lockedLabel: String = "") {
    val ext = HayatThemeExt.colors
    val gold = ext.gold
    Column(modifier.width(84.dp).semantics(mergeDescendants = true) { if (!earned) stateDescription = lockedLabel }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .background(if (earned) Brush.linearGradient(listOf(gold.copy(alpha = 0.95f), gold.copy(alpha = 0.6f))) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh)))
                .padding(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(28.dp), tint = if (earned) gold else MaterialTheme.colorScheme.outline)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 6.dp),
            color = if (earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Tappable mini statistic with a category tint (used in the Home hero card). */
@Composable
fun MiniStat(icon: ImageVector, label: String, value: String, tone: Pair<Color, Color>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.heightIn(min = 48.dp).pressScale(interaction).clip(RoundedCornerShape(14.dp)).background(tone.first)
            .clickable(interaction, ripple(), role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = tone.second)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tone.second, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
    }
}

/** Card header row: tinted circular icon + title (+ optional trailing). */
@Composable
fun CardTitle(icon: ImageVector, title: String, tone: Pair<Color, Color>, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(tone.first), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = tone.second)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

/** Empty state inside a card with an optional action (e.g. "Start"). */
@Composable
fun InlineEmpty(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.let { Spacer(Modifier.width(8.dp)); it() }
    }
}

