package com.carlos.garmin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carlos.garmin.Activity
import com.carlos.garmin.AppSettings
import com.carlos.garmin.Blocks
import com.carlos.garmin.DashboardData
import com.carlos.garmin.HomeBlock
import com.carlos.garmin.Metrics
import com.carlos.garmin.Recommender
import com.carlos.garmin.RecoLevel
import java.time.LocalDate

// ------------------------------------------------------------------ utilidades

private fun mondayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

private class Cell(val id: String, val label: String, val value: String, val sub: String? = null, val color: Color? = null)

private class Tot(
    val km: Double, val min: Double, val elev: Double, val n: Int, val kcal: Double,
    val power: Double?, val hr: Double?,
)

private fun tot(acts: List<Activity>): Tot = Tot(
    km = acts.sumOf { it.km },
    min = acts.sumOf { it.minutes },
    elev = acts.sumOf { it.elevationM ?: 0.0 },
    n = acts.size,
    kcal = acts.sumOf { it.kcal ?: 0.0 },
    power = acts.mapNotNull { it.powerW }.takeIf { it.isNotEmpty() }?.average(),
    hr = acts.mapNotNull { it.hrAvg }.takeIf { it.isNotEmpty() }?.average(),
)

private fun inRange(d: LocalDate, from: LocalDate, to: LocalDate) = !d.isBefore(from) && !d.isAfter(to)

private fun actsIn(data: DashboardData, from: LocalDate, to: LocalDate) =
    data.activities.filter { inRange(it.date, from, to) }

/** Eficiencia media (W por lpm) de las salidas en bici con potencia y pulso. */
private fun effAvg(acts: List<Activity>): Double? =
    acts.filter { it.isBike && it.powerW != null && it.hrAvg != null && it.hrAvg > 0 }
        .map { it.powerW!! / it.hrAvg!! }.takeIf { it.isNotEmpty() }?.average()

private fun pct(cur: Double, prev: Double): Double? = if (prev > 0.0) (cur - prev) / prev * 100.0 else null

private fun pctText(p: Double): String = "${Fmt.sg(p, 0)} %"

private fun sleepCell(data: DashboardData, from: LocalDate, to: LocalDate, id: String = "sleep"): Cell {
    val nights = data.sleep.filter { inRange(it.date, from, to) }
    if (nights.isEmpty()) return Cell(id, "Sueño medio", "—")
    val h = nights.map { it.hours }.average()
    val sc = nights.mapNotNull { it.score?.toDouble() }.takeIf { it.isNotEmpty() }?.average()
    return Cell(id, "Sueño medio", Fmt.dur(h * 60), sc?.let { "puntuación ${Fmt.n(it)}" })
}

private fun stepsCell(data: DashboardData, from: LocalDate, to: LocalDate, id: String = "steps"): Cell {
    val days = data.steps.filter { inRange(it.date, from, to) }
    return Cell(id, "Pasos / día", if (days.isEmpty()) "—" else Fmt.n(days.map { it.steps }.average()))
}

private fun hrvCell(data: DashboardData, from: LocalDate, to: LocalDate): Cell {
    val v = data.hrv.filter { inRange(it.date, from, to) }.mapNotNull { it.lastNight?.toDouble() }
    return Cell("hrv", "VFC media", if (v.isEmpty()) "—" else Fmt.n(v.average()) + " ms")
}

private fun rhrCell(data: DashboardData, from: LocalDate, to: LocalDate): Cell {
    val v = data.rhr.filter { inRange(it.date, from, to) }.map { it.value }
    return Cell("rhr", "FC reposo media", if (v.isEmpty()) "—" else Fmt.n(v.average()) + " lpm")
}

// ------------------------------------------------------------------ piezas visuales

@Composable
private fun BlockCard(title: String, whenText: String? = null, content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (whenText != null) {
                    Text(whenText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun RowScope.CellBox(c: Cell) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(c.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(c.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (c.sub != null) {
            Text(c.sub, style = MaterialTheme.typography.labelSmall, color = c.color ?: MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CellGrid(cells: List<Cell>) {
    if (cells.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c -> CellBox(c) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Chip(text: String, good: Boolean?) {
    val bg = when (good) {
        true -> Color(0xFFDDF0E4)
        false -> Color(0xFFFDEBD0)
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (good) {
        true -> Color(0xFF14532D)
        false -> Color(0xFF7A3E06)
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
private fun Headline(km: Double, chip: Pair<String, Boolean?>?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            Text(Fmt.n(km, if (km < 100) 1 else 0), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(" km", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (chip != null) Chip(chip.first, chip.second)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProgressBar(frac: Float, marker: Float?) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = Palette.primary
    val mark = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val h = size.height * 0.7f
        val top = (size.height - h) / 2f
        drawRoundRect(track, Offset(0f, top), Size(size.width, h), CornerRadius(h / 2f))
        val w = size.width * frac.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(fill, Offset(0f, top), Size(w, h), CornerRadius(h / 2f))
        if (marker != null) {
            val x = size.width * marker.coerceIn(0f, 1f)
            drawRect(mark, Offset(x - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height))
        }
    }
}

// ------------------------------------------------------------------ pantalla

@Composable
fun HomeScreen(data: DashboardData, settings: AppSettings) {
    Page {
        if (data.warnings.isNotEmpty()) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Algunos datos no se han podido cargar", style = MaterialTheme.typography.labelLarge)
                        data.warnings.take(4).forEach {
                            Text("• $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        settings.blocks.filter { it.enabled }.forEach { b ->
            item(key = b.id) {
                when (b.id) {
                    Blocks.RECO -> RecoBlock(data, settings)
                    Blocks.TODAY -> TodayBlock(data, b)
                    Blocks.YESTERDAY -> YesterdayBlock(data, b)
                    Blocks.WEEK -> WeekBlock(data, b)
                    Blocks.MONTH -> MonthBlock(data, b)
                    Blocks.YEAR -> YearBlock(data, b)
                    Blocks.LAST -> LastBlock(data)
                    Blocks.GOAL -> GoalBlock(data, settings.goalKm)
                    Blocks.TRENDS -> BlockCard("¿Mejoro o empeoro?", "8 semanas") { TrendList(data, max = 6) }
                    Blocks.RANGE -> RangeBlock(data, b)
                    else -> {}
                }
            }
        }
    }
}

// ------------------------------------------------------------------ recomendación

@Composable
private fun RecoBlock(data: DashboardData, settings: AppSettings) {
    val r = Recommender.compute(data) ?: return
    val o = settings.reco
    val bg = if (Palette.dark) Color(0xFF1E3A6E) else Color(0xFF0F2E5C)
    val soft = Color(0xFFD3E2F7)
    val label = Color(0xFFB5CEF2)
    val boxBg = Color.White.copy(alpha = 0.10f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RECOMENDACIÓN DE HOY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = label)
            val good = r.level == RecoLevel.QUALITY || r.level == RecoLevel.NORMAL
            Text(
                r.badge, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = if (good) Color(0xFF0B3D20) else Color(0xFF3D2300),
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (good) Color(0xFF8FD3A8) else Color(0xFFF6C97A))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        if (o.verdict) {
            Text(r.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text(r.summary, style = MaterialTheme.typography.bodyMedium, color = soft)
        }
        if (o.session) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(boxBg).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("SI ENTRENAS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = label)
                Text(r.sessionTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text(r.sessionDetail, style = MaterialTheme.typography.bodySmall, color = soft)
            }
        }
        if (o.reasons) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                r.reasons.forEach { reason ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Spacer(
                            Modifier.padding(top = 5.dp).height(10.dp).widthIn(min = 10.dp, max = 10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (reason.good) Color(0xFF8FD3A8) else Color(0xFFF6C97A)),
                        )
                        Text(reason.text, style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                }
            }
        }
        if (o.tomorrow) {
            Text(r.tomorrow, style = MaterialTheme.typography.bodySmall, color = soft)
        }
        Text(
            "Guía orientativa basada en reglas; no es un consejo médico.",
            style = MaterialTheme.typography.labelSmall, color = label,
        )
    }
}

// ------------------------------------------------------------------ hoy

@Composable
private fun TodayBlock(data: DashboardData, b: HomeBlock) {
    val today = data.today
    val night = data.sleep.lastOrNull { !it.date.isBefore(today.minusDays(1)) }
    val hrv = data.hrv.lastOrNull { it.lastNight != null && !it.date.isBefore(today.minusDays(1)) }
    val rhr = data.rhr.lastOrNull { !it.date.isBefore(today.minusDays(1)) }
    val ratio = Recommender.loadRatio(data)
    val weight = data.weight.lastOrNull()
    val steps = data.steps.lastOrNull { it.date == today }

    val hrvState = hrv?.let {
        val low = it.balancedLow
        val high = it.balancedHigh
        when {
            low != null && it.lastNight!! < low -> "baja" to false
            high != null && it.lastNight!! > high -> "alta" to null
            else -> "equilibrada" to true
        }
    }
    val cells = listOf(
        Cell("sleep", "Sueño", night?.score?.toString() ?: "—", night?.let { Fmt.dur(it.hours * 60) }),
        Cell("hrv", "VFC", hrv?.lastNight?.let { "$it ms" } ?: "—", hrvState?.first, hrvState?.second?.let { if (it) GOOD else BAD }),
        Cell("rhr", "FC reposo", rhr?.let { Fmt.n(it.value) } ?: "—", "lpm"),
        Cell(
            "load", "Carga 7/28 días", ratio?.let { "×" + Fmt.n(it, 1) } ?: "—",
            ratio?.let { if (it > 1.3) "alta" else if (it < 0.6) "baja" else "equilibrada" },
            ratio?.let { if (it > 1.3) BAD else GOOD },
        ),
        Cell("battery", "Body Battery ganada", night?.bodyBattery?.let { "+$it" } ?: "—"),
        Cell("steps", "Pasos de hoy", steps?.let { Fmt.n(it.steps.toDouble()) } ?: "—"),
        Cell("weight", "Peso", weight?.let { Fmt.n(it.value, 1) + " kg" } ?: "—", weight?.let { Fmt.date(it.date) }),
    ).filter { it.id in b.metrics }
    if (cells.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEach { c ->
            AppCard {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(min = 84.dp)) {
                    Text(c.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(c.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.sub != null) Text(c.sub, style = MaterialTheme.typography.labelSmall, color = c.color ?: MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ ayer

private fun activityCells(label: String, a: Activity?, t: Tot, b: HomeBlock): List<Cell> {
    val speed = a?.speedKmh ?: if (t.min > 0) t.km / (t.min / 60.0) else null
    return listOf(
        Cell("dist", "Distancia", Fmt.n(t.km, 1) + " km"),
        Cell("time", "Tiempo", Fmt.dur(t.min)),
        Cell("elev", "Desnivel", Fmt.n(t.elev) + " m"),
        Cell("power", "Potencia media", t.power?.let { Fmt.n(it) + " W" } ?: "—"),
        Cell("hr", "FC media", t.hr?.let { Fmt.n(it) + " lpm" } ?: "—"),
        Cell("speed", "Velocidad", speed?.let { Fmt.n(it, 1) + " km/h" } ?: "—"),
        Cell("kcal", "Calorías", Fmt.n(t.kcal) + " kcal"),
        Cell("hrmax", "FC máxima", a?.hrMax?.let { Fmt.n(it) + " lpm" } ?: "—"),
        Cell("te", "Efecto aeróbico", a?.aerobicTe?.let { Fmt.n(it, 1) } ?: "—"),
    ).filter { it.id in b.metrics }
}

@Composable
private fun YesterdayBlock(data: DashboardData, b: HomeBlock) {
    val day = data.today.minusDays(1)
    val acts = data.activities.filter { it.date == day }.sortedBy { it.start }
    BlockCard("Ayer", "${Fmt.dow(day)} ${Fmt.date(day)}") {
        if (acts.isEmpty()) {
            Text("Sin actividad registrada: día de descanso.", style = MaterialTheme.typography.bodyMedium)
        } else if (b.sumAll && acts.size > 1) {
            Text("${acts.size} actividades", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            CellGrid(activityCells("", null, tot(acts), b))
        } else {
            acts.forEach { a ->
                Text(a.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                CellGrid(activityCells(a.name, a, tot(listOf(a)), b))
            }
        }
        if ("steps" in b.metrics) {
            InfoRow("Pasos", data.steps.firstOrNull { it.date == day }?.let { Fmt.n(it.steps.toDouble()) } ?: "—")
        }
        if ("sleep" in b.metrics) {
            val n = data.sleep.firstOrNull { it.date == data.today }
            InfoRow("Sueño de esa noche", n?.let { "${Fmt.dur(it.hours * 60)} · ${it.score ?: "—"}" } ?: "—")
        }
    }
}

// ------------------------------------------------------------------ semana

@Composable
private fun WeekBlock(data: DashboardData, b: HomeBlock) {
    val today = data.today
    val mon = mondayOf(today)
    val acts = actsIn(data, mon, today)
    val prevActs = actsIn(data, mon.minusWeeks(1), today.minusWeeks(1))
    val t = tot(acts)
    val p = tot(prevActs)
    val delta = pct(t.km, p.km)
    val intensity = data.intensity.firstOrNull { it.week == mon }

    BlockCard("Esta semana", "${Fmt.date(mon)} – ${Fmt.date(today)}") {
        if ("km" in b.metrics) {
            Headline(t.km, if ("vsprev" in b.metrics && delta != null) (pctText(delta) + " vs semana anterior") to (delta >= 0) else null)
        }
        if ("perday" in b.metrics) {
            val letters = listOf("L", "M", "X", "J", "V", "S", "D")
            BarChart(
                (0 until 7).map { i ->
                    val d = mon.plusDays(i.toLong())
                    val km = if (d.isAfter(today)) 0.0 else actsIn(data, d, d).sumOf { it.km }
                    BarItem(
                        letters[i],
                        listOf(Seg(km.toFloat(), if (d == today) PALETTE[1] else PALETTE[0])),
                        "${Fmt.dow(d)} ${Fmt.date(d)}: ${Fmt.n(km, 1)} km",
                    )
                },
                height = 130.dp,
            )
        }
        val cells = listOf(
            Cell("sessions", "Salidas", t.n.toString()),
            Cell("time", "Tiempo", Fmt.dur(t.min)),
            Cell("elev", "Desnivel", Fmt.n(t.elev) + " m"),
            stepsCell(data, mon, today),
            sleepCell(data, mon, today),
            Cell(
                "intensity", "Minutos de intensidad",
                intensity?.let { Fmt.n((it.moderate + 2 * it.vigorous).toDouble()) } ?: "—",
                intensity?.let { "objetivo ${it.goal}" },
            ),
            hrvCell(data, mon, today),
            rhrCell(data, mon, today),
            Cell("kcal", "Calorías de actividades", Fmt.n(t.kcal)),
            Cell("power", "Potencia media", t.power?.let { Fmt.n(it) + " W" } ?: "—"),
        ).filter { it.id in b.metrics }
        CellGrid(cells)
    }
}

// ------------------------------------------------------------------ mes

@Composable
private fun MonthBlock(data: DashboardData, b: HomeBlock) {
    val today = data.today
    val first = today.withDayOfMonth(1)
    val acts = actsIn(data, first, today)
    val t = tot(acts)
    val y = Metrics.year(data.activities, today)
    val avg = y.avgMonthKm
    val len = today.lengthOfMonth()
    val frac = today.dayOfMonth.toFloat() / len
    val projection = t.km / today.dayOfMonth * len
    val prevDelta = pct(t.km, y.prevSamePeriodKm)
    val best = acts.maxByOrNull { it.km }
    val monthName = Fmt.MES_LARGO[today.monthValue - 1]

    BlockCard("Mes en curso", "$monthName · día ${today.dayOfMonth} de $len") {
        val ahead = avg > 0 && t.km / avg >= frac
        Headline(t.km, if ("pace" in b.metrics && avg > 0) (if (ahead) "Por delante del ritmo" else "Por detrás del ritmo") to ahead else null)
        if ("pace" in b.metrics && avg > 0) {
            ProgressBar((t.km / avg).toFloat(), frac)
            Text(
                "${Fmt.n(t.km / avg * 100)} % de tu media (${Fmt.n(avg)} km) · hoy marca el ${Fmt.n(frac * 100.0)} % del mes",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val cells = listOf(
            Cell("sessions", "Actividades", t.n.toString()),
            Cell("time", "Tiempo", Fmt.dur(t.min)),
            Cell(
                "projection", "Proyección a fin de mes", "~" + Fmt.n(projection) + " km",
                if (avg > 0) pctText(pct(projection, avg) ?: 0.0) + " vs media" else null,
                if (avg > 0 && projection >= avg) GOOD else BAD,
            ),
            Cell(
                "prevmonth", "Mes anterior (mismos días)", Fmt.n(y.prevSamePeriodKm) + " km",
                prevDelta?.let { pctText(it) }, prevDelta?.let { if (it >= 0) GOOD else BAD },
            ),
            Cell("elev", "Desnivel", Fmt.n(t.elev) + " m"),
            Cell("best", "Mejor salida", best?.let { Fmt.n(it.km, 1) + " km" } ?: "—", best?.let { Fmt.date(it.date) }),
            Cell("eff", "Eficiencia", effAvg(acts)?.let { Fmt.n(it, 2) + " W/lpm" } ?: "—"),
            sleepCell(data, first, today),
            Cell("weight", "Peso", data.weight.lastOrNull { !it.date.isBefore(first) }?.let { Fmt.n(it.value, 1) + " kg" } ?: "—"),
        ).filter { it.id in b.metrics }
        CellGrid(cells)
    }
}

// ------------------------------------------------------------------ año

@Composable
private fun YearBlock(data: DashboardData, b: HomeBlock) {
    val today = data.today
    val y = Metrics.year(data.activities, today)
    val yearStart = LocalDate.of(today.year, 1, 1)
    val weightNow = data.weight.lastOrNull()
    val weightRef = data.weight.lastOrNull { it.date.isBefore(yearStart) } ?: data.weight.firstOrNull { !it.date.isBefore(yearStart) }
    val vo2 = data.vo2.lastOrNull()
    val bike = data.activities.filter { !it.date.isBefore(yearStart) }

    BlockCard("Este año", "desde el 1 de enero") {
        if ("km" in b.metrics) Headline(y.kmYear, null)
        if ("permonth" in b.metrics) {
            BarChart(monthItems(data), height = 130.dp, refY = y.avgMonthKm.toFloat())
        }
        val cells = listOf(
            Cell("avgmonth", "Media mensual", Fmt.n(y.avgMonthKm) + " km", "${y.completedMonths} meses completos"),
            Cell("hours", "Tiempo total", Fmt.n(y.hours) + " h"),
            Cell("sessions", "Actividades", y.sessions.toString()),
            Cell("vo2", "VO2 máx.", vo2?.let { Fmt.n(it.value, 1) } ?: "—"),
            Cell(
                "weight", "Peso", weightNow?.let { Fmt.n(it.value, 1) + " kg" } ?: "—",
                if (weightNow != null && weightRef != null) Fmt.sg(weightNow.value - weightRef.value) + " kg en el año" else null,
            ),
            Cell(
                "bestmonth", "Mejor mes", y.bestMonth?.let { Fmt.n(it.km) + " km" } ?: "—",
                y.bestMonth?.let { Fmt.MES_LARGO[it.month.monthValue - 1] },
            ),
            Cell("elev", "Desnivel acumulado", Fmt.n(y.elevationM) + " m"),
            Cell("eff", "Eficiencia", effAvg(bike)?.let { Fmt.n(it, 2) + " W/lpm" } ?: "—"),
            sleepCell(data, today.minusDays(27), today).let { Cell("sleep", "Sueño medio", it.value, "últimas 4 semanas") },
        ).filter { it.id in b.metrics }
        CellGrid(cells)
    }
}

// ------------------------------------------------------------------ extras

@Composable
private fun LastBlock(data: DashboardData) {
    val a = data.activities.maxByOrNull { it.start }
    BlockCard("Última actividad", a?.let { "${Fmt.dow(it.date)} ${Fmt.date(it.date)}" }) {
        if (a == null) Text("Sin actividades este año.", style = MaterialTheme.typography.bodyMedium)
        else {
            Text(a.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            CellGrid(
                listOf(
                    Cell("dist", "Distancia", Fmt.n(a.km, 1) + " km"),
                    Cell("time", "Tiempo", Fmt.dur(a.minutes)),
                    Cell("elev", "Desnivel", a.elevationM?.let { Fmt.n(it) + " m" } ?: "—"),
                    Cell("power", "Potencia media", a.powerW?.let { Fmt.n(it) + " W" } ?: "—"),
                    Cell("hr", "FC media", a.hrAvg?.let { Fmt.n(it) + " lpm" } ?: "—"),
                    Cell("te", "Efecto aeróbico", a.aerobicTe?.let { Fmt.n(it, 1) } ?: "—"),
                ),
            )
        }
    }
}

@Composable
private fun GoalBlock(data: DashboardData, goalKm: Int) {
    val today = data.today
    val km = Metrics.year(data.activities, today).kmYear
    BlockCard("Objetivo anual", if (goalKm > 0) "${Fmt.n(goalKm.toDouble())} km" else null) {
        if (goalKm <= 0) {
            Text(
                "Fija tu objetivo anual en Personalizar inicio, dentro de este bloque.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@BlockCard
        }
        val doy = today.dayOfYear
        val len = today.lengthOfYear()
        val expected = goalKm.toDouble() * doy / len
        val projection = km / doy * len
        Headline(km, (if (km >= expected) "Por delante del plan" else "Por detrás del plan") to (km >= expected))
        ProgressBar((km / goalKm).toFloat(), doy.toFloat() / len)
        Text(
            "${Fmt.n(km / goalKm * 100)} % del objetivo · hoy toca el ${Fmt.n(doy * 100.0 / len)} %",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CellGrid(
            listOf(
                Cell("rest", "Te faltan", Fmt.n((goalKm - km).coerceAtLeast(0.0)) + " km"),
                Cell("proj", "Proyección a fin de año", Fmt.n(projection) + " km", pctText(pct(projection, goalKm.toDouble()) ?: 0.0) + " vs objetivo", if (projection >= goalKm) GOOD else BAD),
                Cell("perweek", "Necesitas", Fmt.n(((goalKm - km).coerceAtLeast(0.0)) / ((len - doy) / 7.0).coerceAtLeast(1.0)) + " km/sem"),
            ),
        )
    }
}

@Composable
private fun RangeBlock(data: DashboardData, b: HomeBlock) {
    val today = data.today
    val from = today.minusDays((b.days - 1).toLong())
    val acts = actsIn(data, from, today)
    val t = tot(acts)
    BlockCard("Últimos ${b.days} días", "${Fmt.date(from)} – ${Fmt.date(today)}") {
        val cells = listOf(
            Cell("km", "Km", Fmt.n(t.km, 1)),
            Cell("time", "Tiempo", Fmt.dur(t.min)),
            Cell("sessions", "Actividades", t.n.toString()),
            Cell("elev", "Desnivel", Fmt.n(t.elev) + " m"),
            stepsCell(data, from, today),
            sleepCell(data, from, today),
            Cell("power", "Potencia media", t.power?.let { Fmt.n(it) + " W" } ?: "—"),
            Cell("hr", "FC media", t.hr?.let { Fmt.n(it) + " lpm" } ?: "—"),
            hrvCell(data, from, today),
            rhrCell(data, from, today),
        ).filter { it.id in b.metrics }
        CellGrid(cells)
    }
}
