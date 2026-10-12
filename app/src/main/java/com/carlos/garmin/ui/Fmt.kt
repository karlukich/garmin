package com.carlos.garmin.ui

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

object Fmt {
    private val es = Locale("es", "ES")
    private val MES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    val MES_LARGO = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    fun n(v: Double, decimals: Int = 0): String = String.format(es, "%,." + decimals + "f", v)

    fun sg(v: Double, decimals: Int = 1): String {
        val sign = if (v > 0.0) "+" else if (v < 0.0) "−" else ""
        return sign + n(abs(v), decimals)
    }

    fun date(d: LocalDate): String = "${d.dayOfMonth} ${MES[d.monthValue - 1]}"

    /** Día de la semana abreviado en español: "sáb". */
    fun dow(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, es).trimEnd('.')

    /** Duración legible: "3 h 50". */
    fun dur(minutes: Double): String {
        val t = Math.round(minutes).toInt()
        val m = t % 60
        return "${t / 60} h ${if (m < 10) "0" else ""}$m"
    }

    fun hm(minutes: Double): String {
        val t = Math.round(minutes).toInt()
        val m = t % 60
        return "${t / 60}:${if (m < 10) "0" else ""}$m"
    }
}
