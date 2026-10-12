package com.carlos.garmin

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class MonthKm(
    val month: YearMonth,
    val km: Double,
    val hours: Double,
    val elevationM: Double,
    val sessions: Int,
    val partial: Boolean,
)

data class YearSummary(
    val kmYear: Double,
    val sessions: Int,
    val hours: Double,
    val elevationM: Double,
    val avgMonthKm: Double,
    val avgWeekKm: Double,
    val completedMonths: Int,
    val bestMonth: MonthKm?,
    val current: MonthKm?,
    val paceKmPerDay: Double,
    val projectedKm: Double,
    val prevSamePeriodKm: Double,
    val curWatts: Double?,
    val curHr: Double?,
)

data class EffPoint(
    val date: LocalDate,
    val activity: Activity,
    val eff: Double,
    val eff28: Double,
    val trend: Double,
)

enum class Verdict { MEJORA, EMPEORA, ESTABLE, CAMBIA }

data class Trend(
    val label: String,
    val recent: Double,
    val previous: Double,
    val deltaPct: Double,
    val unit: String,
    val decimals: Int,
    val verdict: Verdict,
)

object Metrics {
    fun monthly(acts: List<Activity>, today: LocalDate): List<MonthKm> {
        val cur = YearMonth.from(today)
        return acts.groupBy { YearMonth.from(it.date) }.toSortedMap().map { (m, list) ->
            MonthKm(
                month = m,
                km = list.sumOf { it.km },
                hours = list.sumOf { it.minutes } / 60.0,
                elevationM = list.sumOf { it.elevationM ?: 0.0 },
                sessions = list.size,
                partial = m == cur,
            )
        }
    }

    fun year(acts: List<Activity>, today: LocalDate): YearSummary {
        val months = monthly(acts, today)
        val done = months.filter { !it.partial }
        val cur = months.firstOrNull { it.partial }
        val avgMonth = if (done.isEmpty()) 0.0 else done.sumOf { it.km } / done.size
        val kmYear = months.sumOf { it.km }
        val weeks = today.dayOfYear / 7.0
        val day = today.dayOfMonth
        val pace = (cur?.km ?: 0.0) / day
        val prev = YearMonth.from(today).minusMonths(1)
        val prevKm = acts.filter { YearMonth.from(it.date) == prev && it.date.dayOfMonth <= day }.sumOf { it.km }
        val bike = acts.filter { YearMonth.from(it.date) == YearMonth.from(today) && it.isBike }
        return YearSummary(
            kmYear = kmYear,
            sessions = months.sumOf { it.sessions },
            hours = months.sumOf { it.hours },
            elevationM = months.sumOf { it.elevationM },
            avgMonthKm = avgMonth,
            avgWeekKm = if (weeks > 0) kmYear / weeks else 0.0,
            completedMonths = done.size,
            bestMonth = done.maxByOrNull { it.km },
            current = cur,
            paceKmPerDay = pace,
            projectedKm = pace * today.lengthOfMonth(),
            prevSamePeriodKm = prevKm,
            curWatts = bike.mapNotNull { it.powerW }.takeIf { it.isNotEmpty() }?.average(),
            curHr = bike.mapNotNull { it.hrAvg }.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    /** Salidas en bici con vatios y FC: eficiencia (W/lpm), media de 28 días y recta de tendencia. */
    fun efficiency(acts: List<Activity>, roadOnly: Boolean): List<EffPoint> {
        val rows = acts.filter { it.isBike && it.powerW != null && it.hrAvg != null && it.hrAvg > 0 }
            .filter { !roadOnly || it.type == "road_biking" }
            .sortedBy { it.start }
        if (rows.isEmpty()) return emptyList()
        val effs = rows.map { it.powerW!! / it.hrAvg!! }
        val base = rows.first().date
        val xs = rows.map { ChronoUnit.DAYS.between(base, it.date).toDouble() }
        val n = rows.size
        val mx = xs.average()
        val my = effs.average()
        var sxy = 0.0
        var sxx = 0.0
        for (i in 0 until n) {
            sxy += (xs[i] - mx) * (effs[i] - my)
            sxx += (xs[i] - mx) * (xs[i] - mx)
        }
        val slope = if (sxx > 0) sxy / sxx else 0.0
        return rows.mapIndexed { i, a ->
            val window = rows.indices.filter { j -> xs[j] <= xs[i] && xs[j] > xs[i] - 28 }.map { effs[it] }
            EffPoint(a.date, a, effs[i], window.average(), my + slope * (xs[i] - mx))
        }
    }

    fun trends(d: DashboardData): List<Trend> {
        val out = ArrayList<Trend>()
        val end = d.today

        fun mean(values: List<Pair<LocalDate, Double?>>, days: Long, back: Int): Double? {
            val hi = end.minusDays(back * days)
            val lo = hi.minusDays(days)
            val v = values.filter { it.first.isAfter(lo) && !it.first.isAfter(hi) }.mapNotNull { it.second }
            return if (v.isEmpty()) null else v.average()
        }

        fun add(label: String, values: List<Pair<LocalDate, Double?>>, days: Long, dir: Int, unit: String, dec: Int) {
            val cur = mean(values, days, 0) ?: return
            val prev = mean(values, days, 1) ?: return
            if (prev == 0.0) return
            val pct = (cur - prev) / prev * 100.0
            val verdict = when {
                abs(pct) < 2.0 -> Verdict.ESTABLE
                dir == 0 -> Verdict.CAMBIA
                pct * dir > 0 -> Verdict.MEJORA
                else -> Verdict.EMPEORA
            }
            out.add(Trend(label, cur, prev, pct, unit, dec, verdict))
        }

        val bike = d.activities.filter { it.isBike }
        add("Vatios medios (bici)", bike.map { it.date to it.powerW }, 56, 1, "W", 0)
        add("FC media (bici)", bike.map { it.date to it.hrAvg }, 56, 0, "lpm", 0)
        add("Vatios por pulsación", bike.map { it.date to if (it.powerW != null && it.hrAvg != null && it.hrAvg > 0) it.powerW / it.hrAvg else null }, 56, 1, "W/lpm", 2)
        add("VO2 máx.", d.vo2.map { it.date to it.value }, 56, 1, "", 1)

        // Km por semana: 4 semanas frente a las 4 anteriores.
        val weekly = weeklyKm(d.activities, d.today)
        if (weekly.size >= 8) {
            val cur = weekly.takeLast(4).average()
            val prev = weekly.dropLast(4).takeLast(4).average()
            if (prev > 0) {
                val pct = (cur - prev) / prev * 100.0
                val v = if (abs(pct) < 2.0) Verdict.ESTABLE else if (pct > 0) Verdict.MEJORA else Verdict.EMPEORA
                out.add(Trend("Km por semana", cur, prev, pct, "km", 0, v))
            }
        }

        add("HRV nocturna", d.hrv.map { it.date to it.lastNight?.toDouble() }, 28, 1, "ms", 0)
        add("FC en reposo", d.rhr.map { it.date to it.value }, 28, -1, "lpm", 1)
        add("Puntuación de sueño", d.sleep.map { it.date to it.score?.toDouble() }, 28, 1, "", 0)
        add("Pasos diarios", d.steps.map { it.date to it.steps.toDouble() }, 28, 1, "", 0)
        add("Peso", d.weight.map { it.date to it.value }, 56, 0, "kg", 1)
        return out
    }

    /** Km por semana natural (lunes a domingo), sin huecos, hasta la semana de hoy. */
    fun weeklyKm(acts: List<Activity>, today: LocalDate): List<Double> {
        if (acts.isEmpty()) return emptyList()
        fun monday(d: LocalDate) = d.minusDays((d.dayOfWeek.value - 1).toLong())
        val by = acts.groupBy { monday(it.date) }.mapValues { e -> e.value.sumOf { it.km } }
        val first = monday(acts.minByOrNull { it.start }!!.date)
        val last = monday(today)
        val out = ArrayList<Double>()
        var w = first
        while (!w.isAfter(last)) {
            out.add(by[w] ?: 0.0)
            w = w.plusWeeks(1)
        }
        return out
    }
}
