package com.carlos.garmin.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.abs

/** Punto de una serie: x = día epoch (o índice), y = valor. */
data class Pt(val x: Float, val y: Float)

data class Series(
    val name: String,
    val color: Color,
    val pts: List<Pt>,
    val dots: Boolean = true,
    val line: Boolean = true,
    val dashed: Boolean = false,
    val fmt: (Float) -> String = { Fmt.n(it.toDouble(), 0) },
)

data class Seg(val value: Float, val color: Color)

data class BarItem(val label: String, val segs: List<Seg>, val tip: String)

fun epochX(d: LocalDate): Float = d.toEpochDay().toFloat()

@Composable
private fun EmptyChart(height: Dp) {
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text("Sin datos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LineChart(
    series: List<Series>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    yMin: Float? = null,
    yMax: Float? = null,
    refY: Float? = null,
    yFmt: (Float) -> String = { Fmt.n(it.toDouble(), 0) },
    xIsDate: Boolean = true,
) {
    val all = series.flatMap { it.pts }
    if (all.isEmpty()) {
        EmptyChart(height)
        return
    }
    val density = LocalDensity.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val rule = MaterialTheme.colorScheme.outline
    val lp = with(density) { 40.dp.toPx() }
    val rp = with(density) { 8.dp.toPx() }
    val tp = with(density) { 8.dp.toPx() }
    val bp = with(density) { 22.dp.toPx() }
    val dot = with(density) { 3.dp.toPx() }
    val stroke = with(density) { 2.dp.toPx() }
    val paint = remember(muted, density) {
        Paint().apply {
            color = muted.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            isAntiAlias = true
        }
    }

    val x0 = all.minOf { it.x }
    val x1 = all.maxOf { it.x }.let { if (it == x0) x0 + 1f else it }
    var lo = yMin ?: all.minOf { it.y }
    var hi = yMax ?: all.maxOf { it.y }
    if (refY != null) {
        lo = minOf(lo, refY)
        hi = maxOf(hi, refY)
    }
    if (hi == lo) hi = lo + 1f
    val pad = (hi - lo) * 0.1f
    if (yMin == null) lo -= pad
    if (yMax == null) hi += pad

    var sel by remember(series) { mutableStateOf<Float?>(null) }

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(series) {
                    detectTapGestures { off ->
                        val f = ((off.x - lp) / (size.width - lp - rp)).coerceIn(0f, 1f)
                        sel = x0 + f * (x1 - x0)
                    }
                }
                .pointerInput(series) {
                    detectDragGestures(
                        onDragStart = { off ->
                            val f = ((off.x - lp) / (size.width - lp - rp)).coerceIn(0f, 1f)
                            sel = x0 + f * (x1 - x0)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val f = ((change.position.x - lp) / (size.width - lp - rp)).coerceIn(0f, 1f)
                            sel = x0 + f * (x1 - x0)
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            fun xp(x: Float) = lp + (x - x0) / (x1 - x0) * (w - lp - rp)
            fun yp(y: Float) = tp + (1f - (y - lo) / (hi - lo)) * (h - tp - bp)

            for (i in 0..3) {
                val v = lo + (hi - lo) * i / 3f
                val y = yp(v)
                drawLine(grid, Offset(lp, y), Offset(w - rp, y), strokeWidth = 1f)
                drawIntoCanvas { c ->
                    paint.textAlign = Paint.Align.RIGHT
                    c.nativeCanvas.drawText(yFmt(v), lp - 6f, y + paint.textSize / 3f, paint)
                }
            }
            for (i in 0..3) {
                val x = x0 + (x1 - x0) * i / 3f
                val label = if (xIsDate) Fmt.date(LocalDate.ofEpochDay(x.toLong())) else Fmt.n(x.toDouble(), 0)
                drawIntoCanvas { c ->
                    paint.textAlign = when (i) {
                        0 -> Paint.Align.LEFT
                        3 -> Paint.Align.RIGHT
                        else -> Paint.Align.CENTER
                    }
                    c.nativeCanvas.drawText(label, xp(x), h - 4f, paint)
                }
            }
            if (refY != null) {
                drawLine(
                    rule, Offset(lp, yp(refY)), Offset(w - rp, yp(refY)), strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                )
            }
            for (s in series) {
                if (s.pts.isEmpty()) continue
                if (s.line && s.pts.size > 1) {
                    val path = Path()
                    s.pts.forEachIndexed { i, p ->
                        if (i == 0) path.moveTo(xp(p.x), yp(p.y)) else path.lineTo(xp(p.x), yp(p.y))
                    }
                    drawPath(
                        path, s.color,
                        style = Stroke(
                            width = stroke,
                            pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(18f, 12f)) else null,
                        ),
                    )
                }
                if (s.dots) s.pts.forEach { p -> drawCircle(s.color, dot, Offset(xp(p.x), yp(p.y))) }
            }
            val sx = sel
            if (sx != null) {
                drawLine(rule, Offset(xp(sx), tp), Offset(xp(sx), h - bp), strokeWidth = 1.5f)
                for (s in series) {
                    val p = s.pts.minByOrNull { abs(it.x - sx) } ?: continue
                    drawCircle(s.color, dot * 1.8f, Offset(xp(p.x), yp(p.y)))
                }
            }
        }
        val sx = sel
        if (sx != null) {
            val parts = series.mapNotNull { s ->
                val p = s.pts.minByOrNull { abs(it.x - sx) } ?: return@mapNotNull null
                s.name + ": " + s.fmt(p.y)
            }
            val head = series.flatMap { it.pts }.minByOrNull { abs(it.x - sx) }
            val title = if (head != null && xIsDate) Fmt.date(LocalDate.ofEpochDay(head.x.toLong())) + " · " else ""
            Text(
                title + parts.joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                "Toca o arrastra para ver los valores",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
            )
        }
    }
}

@Composable
fun BarChart(
    items: List<BarItem>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    refY: Float? = null,
    yFmt: (Float) -> String = { Fmt.n(it.toDouble(), 0) },
) {
    if (items.isEmpty()) {
        EmptyChart(height)
        return
    }
    val density = LocalDensity.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val rule = MaterialTheme.colorScheme.outline
    val sel0 = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val lp = with(density) { 40.dp.toPx() }
    val rp = with(density) { 8.dp.toPx() }
    val tp = with(density) { 8.dp.toPx() }
    val bp = with(density) { 22.dp.toPx() }
    val paint = remember(muted, density) {
        Paint().apply {
            color = muted.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            isAntiAlias = true
        }
    }
    val total = items.map { it.segs.sumOf { s -> s.value.toDouble() }.toFloat() }
    var hi = total.maxOrNull() ?: 1f
    if (refY != null) hi = maxOf(hi, refY)
    if (hi <= 0f) hi = 1f
    hi *= 1.08f
    var sel by remember(items) { mutableStateOf<Int?>(null) }
    val n = items.size

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(items) {
                    detectTapGestures { off ->
                        val step = (size.width - lp - rp) / n
                        sel = ((off.x - lp) / step).toInt().coerceIn(0, n - 1)
                    }
                }
                .pointerInput(items) {
                    detectDragGestures(
                        onDragStart = { off ->
                            val step = (size.width - lp - rp) / n
                            sel = ((off.x - lp) / step).toInt().coerceIn(0, n - 1)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val step = (size.width - lp - rp) / n
                            sel = ((change.position.x - lp) / step).toInt().coerceIn(0, n - 1)
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            val step = (w - lp - rp) / n
            val bw = (step * 0.7f).coerceAtMost(with(density) { 28.dp.toPx() })
            fun yp(v: Float) = tp + (1f - v / hi) * (h - tp - bp)
            for (i in 0..3) {
                val v = hi * i / 3f
                val y = yp(v)
                drawLine(grid, Offset(lp, y), Offset(w - rp, y), strokeWidth = 1f)
                drawIntoCanvas { c ->
                    paint.textAlign = Paint.Align.RIGHT
                    c.nativeCanvas.drawText(yFmt(v), lp - 6f, y + paint.textSize / 3f, paint)
                }
            }
            val s0 = sel
            if (s0 != null) {
                drawRect(sel0, Offset(lp + step * s0, tp), Size(step, h - tp - bp))
            }
            items.forEachIndexed { i, item ->
                val cx = lp + step * i + step / 2f
                var acc = 0f
                for (seg in item.segs) {
                    if (seg.value <= 0f) continue
                    val top = yp(acc + seg.value)
                    val bottom = yp(acc)
                    drawRect(seg.color, Offset(cx - bw / 2f, top), Size(bw, bottom - top))
                    acc += seg.value
                }
            }
            if (refY != null) {
                drawLine(
                    rule, Offset(lp, yp(refY)), Offset(w - rp, yp(refY)), strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                )
            }
            val every = Math.ceil(n / 5.0).toInt().coerceAtLeast(1)
            items.forEachIndexed { i, item ->
                if (i % every == 0) {
                    drawIntoCanvas { c ->
                        paint.textAlign = Paint.Align.CENTER
                        c.nativeCanvas.drawText(item.label, lp + step * i + step / 2f, h - 4f, paint)
                    }
                }
            }
        }
        val s = sel
        Text(
            if (s != null) items[s].tip else "Toca o arrastra para ver los valores",
            style = if (s != null) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            color = if (s != null) MaterialTheme.colorScheme.onSurface else muted,
        )
    }
}
