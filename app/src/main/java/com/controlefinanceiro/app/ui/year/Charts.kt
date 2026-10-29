package com.controlefinanceiro.app.ui.year

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val MONTH_INITIALS = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

/** Tap or slide a finger over the 12 month slots to pick one. */
private fun Modifier.monthPicker(onSelect: (Int) -> Unit) = this
    .pointerInput(onSelect) { detectTapGestures { onSelect(slotAt(it.x, size.width.toFloat())) } }
    .pointerInput(onSelect) { detectHorizontalDragGestures { change, _ -> onSelect(slotAt(change.position.x, size.width.toFloat())) } }

private fun slotAt(x: Float, width: Float) = (x / (width / 12f)).toInt().coerceIn(0, 11)

/** Initials under the chart, one per slot; the picked month stands out. */
@Composable
private fun MonthAxis(selected: Int?) {
    Row(Modifier.fillMaxWidth()) {
        MONTH_INITIALS.forEachIndexed { i, label ->
            Text(
                label, Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Light band behind the picked month. */
private fun DrawScope.highlight(selected: Int?, color: Color) {
    if (selected == null) return
    val slot = size.width / 12f
    drawRoundRect(color, Offset(slot * selected + slot * 0.08f, 0f), Size(slot * 0.84f, size.height), CornerRadius(8.dp.toPx()))
}

private fun DrawScope.dashedLine(y: Float, color: Color, width: Float = 1.dp.toPx()) =
    drawLine(color, Offset(0f, y), Offset(size.width, y), width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))

/**
 * Bars for the 12 months. [series] holds one list of 12 values per series, drawn side by side, or
 * one on top of the other when [stacked]. Negative values go below a zero line. [average] draws a
 * dashed line across. Months from [fadedFrom] on (still to come) are drawn lighter.
 */
@Composable
fun MonthBarChart(
    series: List<List<Float>>,
    colors: List<Color>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    average: Float? = null,
    fadedFrom: Int? = null,
    height: Dp = 170.dp,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val band = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    val averageColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height).monthPicker(onSelect)) {
            val totals = (0 until 12).map { m -> series.map { it[m] } }
            val top = maxOf(
                if (stacked) totals.maxOf { v -> v.filter { it > 0 }.sum() } else series.maxOf { s -> s.maxOrNull() ?: 0f },
                average ?: 0f, 0f,
            )
            val bottom = minOf(if (stacked) 0f else series.minOf { s -> s.minOrNull() ?: 0f }, average ?: 0f, 0f)
            val range = (top - bottom).takeIf { it > 0f } ?: 1f
            fun y(v: Float) = size.height * (top - v) / range

            highlight(selected, band)
            listOf(top, top / 2).filter { it > 0f }.forEach { dashedLine(y(it), grid) }
            drawLine(grid, Offset(0f, y(0f)), Offset(size.width, y(0f)), 1.dp.toPx())

            val slot = size.width / 12f
            val groupWidth = slot * if (stacked) 0.46f else 0.62f
            val barWidth = if (stacked) groupWidth else groupWidth / series.size
            // Stacked pieces touch each other, so they only get a hint of rounding.
            val radius = CornerRadius((barWidth / 2).coerceAtMost((if (stacked) 1.5f else 4f).dp.toPx()))
            for (m in 0 until 12) {
                val alpha = (if (selected == null || selected == m) 1f else 0.35f) * (if (fadedFrom != null && m >= fadedFrom) 0.45f else 1f)
                val left = slot * m + (slot - groupWidth) / 2
                var stackTop = 0f
                series.forEachIndexed { s, values ->
                    val v = values[m]
                    if (v == 0f) return@forEachIndexed
                    val (from, to) = if (stacked) stackTop to (stackTop + v).also { stackTop = it } else 0f to v
                    val x = if (stacked) left else left + barWidth * s
                    val y1 = y(maxOf(from, to))
                    val y2 = y(minOf(from, to))
                    // A hairline gap separates the stacked pieces.
                    val gap = if (stacked) 1.dp.toPx() else 0f
                    drawRoundRect(
                        colors[s].copy(alpha = colors[s].alpha * alpha), Offset(x + barWidth * 0.08f, y1 + gap / 2),
                        Size(barWidth * 0.84f, (y2 - y1 - gap).coerceAtLeast(1.dp.toPx())), radius,
                    )
                }
            }
            average?.let { dashedLine(y(it), averageColor, 1.5.dp.toPx()) }
        }
        Spacer(Modifier.height(6.dp))
        MonthAxis(selected)
    }
}

/**
 * A line over the 12 months with a soft fill below it, like an investment app's balance chart.
 * Null values (months with nothing yet) are left out; from [projectedFrom] on the line is dashed,
 * as a projection. [compare] (e.g. the year before) is drawn as a thin grey line behind.
 */
@Composable
fun MonthLineChart(
    values: List<Float?>,
    color: Color,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    projectedFrom: Int? = null,
    compare: List<Float?>? = null,
    height: Dp = 170.dp,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val compareColor = MaterialTheme.colorScheme.outline
    val marker = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height).monthPicker(onSelect)) {
            val known = values.filterNotNull() + compare.orEmpty().filterNotNull()
            val top = maxOf(known.maxOrNull() ?: 0f, 0f)
            val bottom = minOf(known.minOrNull() ?: 0f, 0f)
            val range = (top - bottom).takeIf { it > 0f } ?: 1f
            // A little room above and below so the line and its dots are never cut.
            val pad = 8.dp.toPx()
            fun y(v: Float) = pad + (size.height - 2 * pad) * (top - v) / range
            val slot = size.width / 12f
            fun x(i: Int) = slot * i + slot / 2

            listOf(top, top / 2).filter { it > 0f }.forEach { dashedLine(y(it), grid) }
            drawLine(grid, Offset(0f, y(0f)), Offset(size.width, y(0f)), 1.dp.toPx())

            fun curve(points: List<Offset>) = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.zipWithNext { a, b ->
                    val mid = (a.x + b.x) / 2
                    cubicTo(mid, a.y, mid, b.y, b.x, b.y)
                }
            }
            compare?.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), y(it)) } }?.takeIf { it.size > 1 }?.let {
                drawPath(curve(it), compareColor, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val indexed = values.mapIndexedNotNull { i, v -> v?.let { i to Offset(x(i), y(it)) } }
            if (indexed.isEmpty()) return@Canvas
            val all = indexed.map { it.second }
            // The real part ends at the last month before the projection; the dashed part starts there.
            val real = indexed.filter { projectedFrom == null || it.first < projectedFrom }.map { it.second }
            val projected = if (projectedFrom == null) emptyList() else real.takeLast(1) + indexed.filter { it.first >= projectedFrom }.map { it.second }
            val area = Path().apply {
                addPath(curve(all))
                lineTo(all.last().x, y(0f))
                lineTo(all.first().x, y(0f))
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f)), startY = y(top), endY = y(0f).coerceAtLeast(y(top) + 1f)))
            if (real.isNotEmpty()) drawPath(curve(real), color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (projected.size > 1) {
                drawPath(
                    curve(projected), color.copy(alpha = 0.8f),
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }
            real.forEach { drawCircle(color, 3.dp.toPx(), it) }

            val picked = selected?.let { s -> values.getOrNull(s)?.let { Offset(x(s), y(it)) } }
            if (picked != null) {
                drawLine(marker.copy(alpha = 0.4f), Offset(picked.x, 0f), Offset(picked.x, size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                drawCircle(surface, 7.dp.toPx(), picked)
                drawCircle(marker, 7.dp.toPx(), picked, style = Stroke(2.5.dp.toPx()))
                drawCircle(color, 4.dp.toPx(), picked)
            }
        }
        Spacer(Modifier.height(6.dp))
        MonthAxis(selected)
    }
}

/** A ring split by value, with something in the middle (usually the total). */
@Composable
fun Donut(
    slices: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    thickness: Dp = 22.dp,
    center: @Composable () -> Unit = {},
) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = thickness.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val total = slices.sumOf { it.first.toDouble() }.toFloat()
            if (total <= 0f) {
                drawArc(empty, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                return@Canvas
            }
            // A thin gap between slices, only when there is more than one.
            val gap = if (slices.count { it.first > 0f } > 1) 2f else 0f
            var start = -90f
            slices.filter { it.first > 0f }.forEach { (value, color) ->
                val sweep = 360f * value / total
                drawArc(color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                start += sweep
            }
        }
        center()
    }
}
