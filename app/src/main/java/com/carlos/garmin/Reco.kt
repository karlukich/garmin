package com.carlos.garmin

import java.time.LocalDate
import kotlin.math.roundToInt

enum class RecoLevel { REST, EASY, NORMAL, QUALITY }

data class RecoReason(val text: String, val good: Boolean)

data class Reco(
    val level: RecoLevel,
    val badge: String,
    val title: String,
    val summary: String,
    val sessionTitle: String,
    val sessionDetail: String,
    val reasons: List<RecoReason>,
    val tomorrow: String,
)

/**
 * Recomendación diaria con reglas sencillas sobre tu descanso (sueño, VFC, pulso en reposo)
 * y tu carga reciente. Es una guía orientativa, no un consejo médico.
 */
object Recommender {

    private fun median(v: List<Double>): Double? {
        if (v.isEmpty()) return null
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    private fun hm(hours: Double): String {
        val t = (hours * 60).roundToInt()
        return "${t / 60} h ${t % 60} min"
    }

    /** Minutos de actividad en [from, to] (ambos incluidos). */
    private fun minutes(d: DashboardData, from: LocalDate, to: LocalDate): Double =
        d.activities.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sumOf { it.minutes }

    /** Carga de 7 días frente a la media semanal de los últimos 28; 1,0 = igual que de costumbre. */
    fun loadRatio(d: DashboardData): Double? {
        val acute = minutes(d, d.today.minusDays(6), d.today)
        val chronic = minutes(d, d.today.minusDays(27), d.today) / 4.0
        return if (chronic <= 0.0) null else acute / chronic
    }

    fun compute(d: DashboardData): Reco? {
        val today = d.today
        val night = d.sleep.lastOrNull { !it.date.isBefore(today.minusDays(1)) }
        val hrv = d.hrv.lastOrNull { it.lastNight != null && !it.date.isBefore(today.minusDays(1)) }
        val rhrToday = d.rhr.lastOrNull { !it.date.isBefore(today.minusDays(1)) }?.value
        val rhrBase = d.rhr.filter { it.date.isBefore(today.minusDays(2)) }.takeLast(28).map { it.value }
            .takeIf { it.size >= 7 }?.average()
        if (night == null && hrv == null && rhrToday == null) return null

        var score = 0
        val reasons = ArrayList<RecoReason>()

        // --- VFC
        val vNow = hrv?.lastNight
        if (hrv != null && vNow != null) {
            val v: Int = vNow
            val low = hrv.balancedLow
            val high = hrv.balancedHigh
            val avg = hrv.weeklyAvg
            when {
                low != null && v < low * 0.9 -> {
                    score -= 3
                    reasons.add(RecoReason("VFC $v ms, bien por debajo de tu banda equilibrada (${low}–${high ?: "—"})", false))
                }
                low != null && v < low -> {
                    score -= 2
                    reasons.add(RecoReason("VFC $v ms, por debajo de tu banda equilibrada (${low}–${high ?: "—"})", false))
                }
                avg != null && v < avg * 0.9 -> {
                    score -= 1
                    reasons.add(RecoReason("VFC $v ms, algo por debajo de tu media de 7 días ($avg)", false))
                }
                else -> {
                    score += 1
                    reasons.add(RecoReason("VFC $v ms, dentro de tu rango", true))
                }
            }
        }

        // --- Pulso en reposo
        if (rhrToday != null && rhrBase != null) {
            val delta = rhrToday - rhrBase
            when {
                delta >= 4 -> {
                    score -= 2
                    reasons.add(RecoReason("FC en reposo ${rhrToday.roundToInt()} lpm, ${delta.roundToInt()} por encima de tu media (${rhrBase.roundToInt()})", false))
                }
                delta >= 2 -> {
                    score -= 1
                    reasons.add(RecoReason("FC en reposo ${rhrToday.roundToInt()} lpm, algo por encima de tu media (${rhrBase.roundToInt()})", false))
                }
                delta <= 0 -> {
                    score += 1
                    reasons.add(RecoReason("FC en reposo ${rhrToday.roundToInt()} lpm, en tu media o por debajo", true))
                }
            }
        }

        // --- Sueño
        if (night != null) {
            when {
                night.hours < 6.0 -> {
                    score -= 2
                    reasons.add(RecoReason("Solo dormiste ${hm(night.hours)}", false))
                }
                night.hours < 7.0 -> {
                    score -= 1
                    reasons.add(RecoReason("Dormiste ${hm(night.hours)}, algo corto", false))
                }
                night.hours >= 7.5 && (night.score ?: 0) >= 80 -> {
                    score += 1
                    reasons.add(RecoReason("Dormiste ${hm(night.hours)} con puntuación ${night.score}", true))
                }
                else -> reasons.add(RecoReason("Dormiste ${hm(night.hours)}", night.hours >= 7.5))
            }
            val sc = night.score
            if (sc != null && sc < 60) {
                score -= 1
                reasons.add(RecoReason("Puntuación de sueño baja ($sc)", false))
            }
            if (night.deepH < 0.6 && night.hours >= 5.0) {
                score -= 1
                reasons.add(RecoReason("Solo ${(night.deepH * 60).roundToInt()} min de sueño profundo", false))
            }
        }

        // --- Esfuerzo de ayer
        val yesterday = d.activities.filter { it.date == today.minusDays(1) }
        if (yesterday.isNotEmpty()) {
            val te = yesterday.maxOfOrNull { it.aerobicTe ?: 0.0 } ?: 0.0
            val min = yesterday.sumOf { it.minutes }
            val km = yesterday.sumOf { it.km }
            when {
                te >= 4.5 || min >= 180 || km >= 100 -> {
                    score -= 2
                    reasons.add(RecoReason("Ayer ${hm(min / 60.0)}" + (if (te > 0) " con efecto aeróbico ${"%.1f".format(te)}" else ""), false))
                }
                te >= 3.5 || min >= 90 -> {
                    score -= 1
                    reasons.add(RecoReason("Ayer una sesión exigente (${hm(min / 60.0)})", false))
                }
                else -> reasons.add(RecoReason("Ayer una sesión ligera (${hm(min / 60.0)})", true))
            }
        }

        // --- Carga acumulada
        val ratio = loadRatio(d)
        if (ratio != null) {
            val r = "%.1f".format(ratio).replace('.', ',')
            when {
                ratio > 1.5 -> {
                    score -= 2
                    reasons.add(RecoReason("Esta semana llevas mucha más carga de la habitual (×$r)", false))
                }
                ratio > 1.3 -> {
                    score -= 1
                    reasons.add(RecoReason("Esta semana llevas más carga de la habitual (×$r)", false))
                }
                ratio < 0.6 -> {
                    score += 1
                    reasons.add(RecoReason("Carga reciente baja (×$r): puedes apretar", true))
                }
                else -> reasons.add(RecoReason("Carga de la semana equilibrada (×$r)", true))
            }
        }

        // --- Pautas personales a partir de tus rodajes suaves
        val easy = d.activities.filter { it.isBike && it.minutes >= 30 && (it.aerobicTe ?: 9.0) < 3.0 }
        val easyHr = median(easy.mapNotNull { it.hrAvg })?.let { (it / 5.0).roundToInt() * 5 }
        val easyW = median(easy.mapNotNull { it.powerW })?.let { (it / 5.0).roundToInt() * 5 }
        val hrTxt = easyHr?.let { "FC por debajo de $it lpm" } ?: "FC en zona 1-2"
        val wTxt = easyW?.let { ", unos $it W" } ?: ""

        val hrvLow = hrv?.balancedLow
        val rhrLimit = rhrBase?.let { (it + 2).roundToInt() }
        val tomorrowRecover = buildString {
            append("Mañana: si ")
            if (hrvLow != null) append("la VFC vuelve a $hrvLow ms o más") else append("la VFC se recupera")
            if (rhrLimit != null) append(" y el pulso en reposo baja de ${rhrLimit + 1}")
            append(", vuelve a una sesión de calidad.")
        }

        return when {
            score <= -4 -> Reco(
                RecoLevel.REST, "Descanso",
                "Descansa hoy",
                "Tu cuerpo da varias señales de fatiga. Hoy rinde más recuperar que sumar carga.",
                "Descanso o movilidad",
                "Paseo o 20 minutos de movilidad. Prioriza dormir pronto y comer bien.",
                reasons, tomorrowRecover,
            )
            score <= -2 -> Reco(
                RecoLevel.EASY, "Recuperación",
                "Rodaje suave o descanso",
                "Hay señales de fatiga: hoy toca absorber, no sumar carga.",
                "Rodaje de recuperación · 45–60 min",
                "$hrTxt$wTxt, cadencia alta y sin intervalos. Mejor en rodillo o en llano.",
                reasons, tomorrowRecover,
            )
            score <= 1 -> Reco(
                RecoLevel.NORMAL, "Normal",
                "Entrena con normalidad, sin pasarte",
                "Estás razonablemente recuperado. Sesión aeróbica estable y deja la intensidad para otro día.",
                "Resistencia aeróbica · 60–90 min",
                "Ritmo constante en zona 2" + (easyHr?.let { ", FC alrededor de ${it + 10} lpm" } ?: "") + ". Sin picos de esfuerzo.",
                reasons, "Mañana: si mantienes este nivel de descanso, puedes plantear una sesión de calidad.",
            )
            else -> Reco(
                RecoLevel.QUALITY, "Listo",
                "Buen día para una sesión de calidad",
                "Descanso y carga acompañan: aprovecha para trabajar intensidad.",
                "Intervalos · 60–75 min",
                "Calienta 15 min y haz 4–5 bloques de 6 min a ritmo de umbral con 3 min suaves entre ellos. Vuelta a la calma 10 min.",
                reasons, "Mañana: rodaje suave para asimilar el trabajo.",
            )
        }
    }
}
