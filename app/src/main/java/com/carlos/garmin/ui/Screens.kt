package com.carlos.garmin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carlos.garmin.Activity
import com.carlos.garmin.DashboardData
import com.carlos.garmin.EffPoint
import com.carlos.garmin.Metrics
import com.carlos.garmin.Trend
import com.carlos.garmin.TrainingParts
import com.carlos.garmin.Verdict
import java.time.LocalDate
import java.time.YearMonth

internal val TYPE_ES = mapOf(
    "road_biking" to "Ruta", "mountain_biking" to "MTB", "indoor_cycling" to "Rodillo",
    "gravel_cycling" to "Gravel", "walking" to "Caminar", "strength_training" to "Fuerza", "mobility" to "Movilidad",
)

@Composable
internal fun Page(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

internal fun pts(rows: List<Pair<LocalDate, Double?>>): List<Pt> =
    rows.mapNotNull { (d, v) -> if (v == null) null else Pt(epochX(d), v.toFloat()) }

internal fun monthItems(data: DashboardData): List<BarItem> =
    Metrics.monthly(data.activities, data.today).map { m ->
        val name = Fmt.MES_LARGO[m.month.monthValue - 1]
        BarItem(
            label = name.take(3),
            segs = listOf(Seg(m.km.toFloat(), if (m.partial) PALETTE[1] else PALETTE[0])),
            tip = name.replaceFirstChar { it.uppercase() } + (if (m.partial) " (en curso)" else "") +
                ": ${Fmt.n(m.km, 1)} km · ${Fmt.n(m.hours, 1)} h · ${Fmt.n(m.elevationM)} m · ${m.sessions} salidas",
        )
    }

// ---------------------------------------------------------------- Sueño
@Composable
fun SleepScreen(data: DashboardData) {
    Page {
        item {
            SectionCard("Puntuación de sueño", "Garmin, 0–100 · últimas 4 semanas (línea discontinua: 80)") {
                LineChart(
                    listOf(Series("Puntuación", PALETTE[0], pts(data.sleep.map { it.date to it.score?.toDouble() }))),
                    yMin = 40f, yMax = 100f, refY = 80f,
                )
            }
        }
        item {
            SectionCard("Fases del sueño", "Horas por noche") {
                val f = { v: Double -> Fmt.n(v, 1) + " h" }
                BarChart(
                    data.sleep.map {
                        BarItem(
                            label = Fmt.date(it.date),
                            segs = listOf(Seg(it.deepH.toFloat(), PALETTE[3]), Seg(it.remH.toFloat(), PALETTE[1]), Seg(it.lightH.toFloat(), PALETTE[2]), Seg(it.awakeH.toFloat(), NEUTRAL)),
                            tip = "${Fmt.date(it.date)}: profundo ${f(it.deepH)} · REM ${f(it.remH)} · ligero ${f(it.lightH)} · despierto ${f(it.awakeH)}",
                        )
                    },
                    height = 200.dp,
                )
                Legend(listOf("Profundo" to PALETTE[3], "REM" to PALETTE[1], "Ligero" to PALETTE[2], "Despierto" to NEUTRAL))
            }
        }
        item {
            SectionCard("HRV nocturna", "Media de la noche, media de 7 días y rango equilibrado (discontinuas)") {
                val h = data.hrv
                LineChart(
                    listOf(
                        Series("Noche", PALETTE[1], pts(h.map { it.date to it.lastNight?.toDouble() })),
                        Series("Media 7 d", PALETTE[0], pts(h.map { it.date to it.weeklyAvg?.toDouble() }), dots = false),
                        Series("Mín. equilibrado", NEUTRAL, pts(h.map { it.date to it.balancedLow?.toDouble() }), dots = false, dashed = true),
                        Series("Máx. equilibrado", NEUTRAL, pts(h.map { it.date to it.balancedHigh?.toDouble() }), dots = false, dashed = true),
                    ),
                    height = 200.dp,
                )
            }
        }
        item {
            SectionCard("FC en reposo", "Latidos por minuto") {
                LineChart(
                    listOf(Series("FC reposo", PALETTE[3], pts(data.rhr.map { it.date to it.value }), fmt = { Fmt.n(it.toDouble(), 0) + " lpm" })),
                )
            }
        }
        item {
            SectionCard("Body Battery recuperada durante el sueño", "Puntos ganados cada noche") {
                BarChart(
                    data.sleep.filter { it.bodyBattery != null }.map {
                        BarItem(Fmt.date(it.date), listOf(Seg(it.bodyBattery!!.toFloat(), PALETTE[4])), "${Fmt.date(it.date)}: +${it.bodyBattery}")
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Entrenamiento
@Composable
private fun PerfPanels(rows: List<Activity>) {
    val bike = rows.filter { it.isBike }.sortedBy { it.start }
    if (bike.isEmpty()) {
        Text("Sin salidas en bici", style = MaterialTheme.typography.bodySmall)
        return
    }
    fun trend(values: List<Pair<LocalDate, Double?>>): List<Pt> {
        val v = values.filter { it.second != null }
        return v.map { (d, _) ->
            val w = v.filter { !it.first.isAfter(d) && it.first.isAfter(d.minusDays(28)) }.map { it.second!! }
            Pt(epochX(d), w.average().toFloat())
        }
    }
    val watts = bike.map { it.date to it.powerW }
    val hr = bike.map { it.date to it.hrAvg }
    val mins = bike.map { it.date to it.minutes }
    Text("Vatios medios", style = MaterialTheme.typography.labelLarge)
    LineChart(
        listOf(
            Series("Salida", PALETTE[0], pts(watts), line = false, fmt = { Fmt.n(it.toDouble()) + " W" }),
            Series("Media 28 d", Color.Gray, trend(watts), dots = false, fmt = { Fmt.n(it.toDouble()) + " W" }),
        ),
    )
    Text("FC media (lpm)", style = MaterialTheme.typography.labelLarge)
    LineChart(
        listOf(
            Series("Salida", PALETTE[3], pts(hr), line = false, fmt = { Fmt.n(it.toDouble()) + " lpm" }),
            Series("Media 28 d", Color.Gray, trend(hr), dots = false, fmt = { Fmt.n(it.toDouble()) + " lpm" }),
        ),
    )
    Text("Duración (h)", style = MaterialTheme.typography.labelLarge)
    LineChart(
        listOf(
            Series("Salida", PALETTE[2], pts(mins.map { it.first to it.second / 60.0 }), line = false, fmt = { Fmt.n(it.toDouble(), 1) + " h" }),
            Series("Media 28 d", Color.Gray, trend(mins).map { Pt(it.x, it.y / 60f) }, dots = false, fmt = { Fmt.n(it.toDouble(), 1) + " h" }),
        ),
        yFmt = { Fmt.n(it.toDouble(), 1) },
    )
    Legend(listOf("Cada salida" to PALETTE[0], "Media 28 días" to Color.Gray))
}

@Composable
private fun EffChart(points: List<EffPoint>) {
    LineChart(
        listOf(
            Series("Salida", PALETTE[0], points.map { Pt(epochX(it.date), it.eff.toFloat()) }, line = false, fmt = { Fmt.n(it.toDouble(), 2) }),
            Series("Media 28 d", PALETTE[1], points.map { Pt(epochX(it.date), it.eff28.toFloat()) }, dots = false, fmt = { Fmt.n(it.toDouble(), 2) }),
            Series("Tendencia", Color.Gray, points.map { Pt(epochX(it.date), it.trend.toFloat()) }, dots = false, dashed = true, fmt = { Fmt.n(it.toDouble(), 2) }),
        ),
        height = 200.dp,
        yFmt = { Fmt.n(it.toDouble(), 2) },
    )
    val a = points.firstOrNull()
    val b = points.lastOrNull()
    if (a != null && b != null && a.trend > 0) {
        val tot = (b.trend - a.trend) / a.trend * 100
        Text(
            "La tendencia pasa de ${Fmt.n(a.trend, 2)} a ${Fmt.n(b.trend, 2)} W/lpm (${Fmt.sg(tot)} %).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TrendRow(t: Trend) {
    val color = when (t.verdict) {
        Verdict.MEJORA -> GOOD
        Verdict.EMPEORA -> BAD
        else -> NEUTRAL
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(t.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${Fmt.n(t.previous, t.decimals)} → ${Fmt.n(t.recent, t.decimals)} ${t.unit}".trim(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("${Fmt.sg(t.deltaPct)} %", color = color, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ActivityRow(a: Activity) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(a.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(TYPE_ES[a.type] ?: a.type, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${Fmt.date(a.date)} ${a.start.toLocalTime().toString().take(5)} · ${Fmt.n(a.km, 1)} km · ${Fmt.hm(a.minutes)} h" +
                    (a.elevationM?.let { " · ${Fmt.n(it)} m" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                listOfNotNull(
                    a.hrAvg?.let { "FC ${Fmt.n(it)} lpm" },
                    a.powerW?.let { "${Fmt.n(it)} W" },
                    a.speedKmh?.let { "${Fmt.n(it, 1)} km/h" },
                    a.aerobicTe?.let { "TE ${Fmt.n(it, 1)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TrainingScreen(data: DashboardData, parts: Set<String>) {
    var shown by remember { mutableStateOf(20) }
    val sorted = data.activities.sortedByDescending { it.start }
    Page {
        if (TrainingParts.MONTHS in parts) {
            item {
                SectionCard("Kilómetros por mes", "Todas las actividades con distancia") {
                    BarChart(monthItems(data), height = 200.dp, refY = Metrics.year(data.activities, data.today).avgMonthKm.toFloat())
                }
            }
        }
        if (TrainingParts.WEEKLY in parts) {
            item {
                SectionCard("Volumen semanal", "Kilómetros por semana (lunes a domingo)") {
                    val weekly = Metrics.weeklyKm(data.activities, data.today)
                    val monday = data.today.minusDays((data.today.dayOfWeek.value - 1).toLong()).minusWeeks((weekly.size - 1).toLong())
                    BarChart(
                        weekly.mapIndexed { i, km ->
                            val d = monday.plusWeeks(i.toLong())
                            BarItem(Fmt.date(d), listOf(Seg(km.toFloat(), PALETTE[0])), "Semana del ${Fmt.date(d)}: ${Fmt.n(km, 1)} km")
                        },
                        height = 200.dp,
                    )
                }
            }
        }
        if (TrainingParts.TRENDS in parts) {
            item {
                SectionCard("¿Mejoro o empeoro?", "Periodo reciente frente al anterior (bici, VO2 máx. y peso: 8 semanas; volumen: 4; recuperación y pasos: 28 días). Estable = menos de ±2 %.") {
                    TrendList(data)
                }
            }
        }
        if (TrainingParts.PERF in parts) {
            item {
                SectionCard("Rendimiento en bici", "Vatios, FC y duración de cada salida (ruta, MTB, rodillo, gravel)") {
                    PerfPanels(data.activities)
                }
            }
        }
        if (TrainingParts.EFF in parts) {
            item {
                SectionCard("Eficiencia · vatios por pulsación", "Vatios medios entre FC media de cada salida. Cuanto más alto, mejor.") {
                    EffChart(Metrics.efficiency(data.activities, roadOnly = false))
                }
            }
            item {
                SectionCard("Eficiencia solo en ruta", "Comparación en las mismas condiciones (sin rodillo ni MTB)") {
                    EffChart(Metrics.efficiency(data.activities, roadOnly = true))
                }
            }
        }
        if (TrainingParts.VO2 in parts) {
            item {
                SectionCard("VO2 máx. (ciclismo)", "Estimación de Garmin tras cada actividad") {
                    LineChart(
                        listOf(Series("VO2 máx.", PALETTE[2], pts(data.vo2.map { it.date to it.value }), fmt = { Fmt.n(it.toDouble(), 1) })),
                        yFmt = { Fmt.n(it.toDouble(), 1) },
                    )
                }
            }
        }
        if (TrainingParts.INTENSITY in parts) {
            item {
                SectionCard("Minutos de intensidad por semana", "Moderados y vigorosos; línea discontinua: objetivo semanal de 150") {
                    BarChart(
                        data.intensity.map {
                            BarItem(
                                Fmt.date(it.week),
                                listOf(Seg(it.moderate.toFloat(), PALETTE[1]), Seg(it.vigorous.toFloat(), PALETTE[0])),
                                "Semana del ${Fmt.date(it.week)}: ${it.moderate} moderados · ${it.vigorous} vigorosos (objetivo ${it.goal})",
                            )
                        },
                        refY = 150f,
                    )
                    Legend(listOf("Moderados" to PALETTE[1], "Vigorosos" to PALETTE[0]))
                }
            }
        }
        if (TrainingParts.ACTIVITIES in parts) {
            item { Text("Actividades (${sorted.size})", style = MaterialTheme.typography.titleLarge) }
            items(sorted.take(shown), key = { it.id }) { ActivityRow(it) }
            if (sorted.size > shown) {
                item { TextButton(onClick = { shown += 30 }) { Text("Mostrar más") } }
            }
        }
    }
}

/** Lista de tendencias, reutilizada en Entreno y en el bloque del Inicio. */
@Composable
fun TrendList(data: DashboardData, max: Int = Int.MAX_VALUE) {
    val tr = Metrics.trends(data)
    if (tr.isEmpty()) Text("Sin datos suficientes", style = MaterialTheme.typography.bodySmall)
    tr.take(max).forEach { TrendRow(it) }
}

// ---------------------------------------------------------------- Diario
@Composable
fun DailyScreen(data: DashboardData) {
    Page {
        item {
            SectionCard("Pasos", "Verde: objetivo cumplido · gris: no alcanzado") {
                BarChart(
                    data.steps.map {
                        val ok = it.goal != null && it.steps >= it.goal
                        BarItem(
                            Fmt.date(it.date),
                            listOf(Seg(it.steps.toFloat(), if (ok) GOOD else NEUTRAL)),
                            "${Fmt.date(it.date)}: ${Fmt.n(it.steps.toDouble())} pasos · ${Fmt.n(it.distanceM / 1000, 1)} km" +
                                (it.goal?.let { g -> " · objetivo ${Fmt.n(g.toDouble())}" } ?: ""),
                        )
                    },
                    height = 200.dp,
                    yFmt = { if (it >= 1000f) Fmt.n(it / 1000.0, 0) + "k" else Fmt.n(it.toDouble()) },
                )
            }
        }
        item {
            val last = data.steps.takeLast(28)
            SectionCard("Resumen de los últimos ${last.size} días") {
                if (last.isNotEmpty()) {
                    Text("Media: ${Fmt.n(last.map { it.steps }.average())} pasos al día", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Objetivo cumplido ${last.count { it.goal != null && it.steps >= it.goal }} de ${last.size} días",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Cuerpo
@Composable
fun BodyScreen(data: DashboardData) {
    Page {
        item {
            SectionCard("Peso", "Registros manuales y de apps conectadas") {
                LineChart(
                    listOf(Series("Peso", PALETTE[5], pts(data.weight.map { it.date to it.value }), fmt = { Fmt.n(it.toDouble(), 1) + " kg" })),
                    yFmt = { Fmt.n(it.toDouble(), 1) },
                )
            }
        }
        item {
            SectionCard("Estrés medio semanal", "Escala 0–100 de Garmin; más alto = más estrés") {
                BarChart(
                    data.stress.map {
                        BarItem(Fmt.date(it.date), listOf(Seg(it.value.toFloat(), PALETTE[6])), "Semana del ${Fmt.date(it.date)}: ${Fmt.n(it.value)}")
                    },
                )
            }
        }
    }
}
