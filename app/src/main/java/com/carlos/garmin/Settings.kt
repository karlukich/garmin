package com.carlos.garmin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Pestañas de la barra inferior. */
object Modules {
    const val HOME = "home"
    const val SLEEP = "sleep"
    const val TRAINING = "training"
    const val DAILY = "daily"
    const val BODY = "body"
    val ALL = listOf(HOME, SLEEP, TRAINING, DAILY, BODY)
    val TITLE = mapOf(HOME to "Inicio", SLEEP to "Sueño", TRAINING to "Entreno", DAILY to "Diario", BODY to "Cuerpo")
    val NOTE = mapOf(
        HOME to "Recomendación, ayer, semana, mes y año",
        SLEEP to "Fases, horas, VFC",
        TRAINING to "Volumen, eficiencia, carga",
        DAILY to "Pasos y actividad diaria",
        BODY to "Peso, estrés",
    )
}

/** Bloques dentro de la pestaña Entreno. */
object TrainingParts {
    const val MONTHS = "months"
    const val WEEKLY = "weekly"
    const val TRENDS = "trends"
    const val PERF = "perf"
    const val EFF = "eff"
    const val VO2 = "vo2"
    const val INTENSITY = "intensity"
    const val ACTIVITIES = "activities"
    val ALL = listOf(MONTHS, WEEKLY, TRENDS, PERF, EFF, VO2, INTENSITY, ACTIVITIES)
    val TITLE = mapOf(
        MONTHS to "Km por mes", WEEKLY to "Km por semana", TRENDS to "¿Mejoro o empeoro?",
        PERF to "Rendimiento en bici", EFF to "Eficiencia (W por lpm)", VO2 to "VO2 máx.",
        INTENSITY to "Minutos de intensidad", ACTIVITIES to "Actividades recientes",
    )
}

/** Un bloque de la pantalla de inicio. */
data class HomeBlock(
    val id: String,
    val enabled: Boolean,
    val metrics: Set<String>,
    val days: Int = 30,
    val sumAll: Boolean = false,
)

data class RecoOptions(
    val verdict: Boolean = true,
    val session: Boolean = true,
    val reasons: Boolean = true,
    val tomorrow: Boolean = true,
)

data class AppSettings(
    val modules: Set<String> = Modules.ALL.toSet(),
    val blocks: List<HomeBlock> = Defaults.blocks(),
    val training: Set<String> = TrainingParts.ALL.toSet(),
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val primary: Long = Swatches.PRIMARY.first().argb,
    val accent: Long = Swatches.ACCENT.first().argb,
    val reco: RecoOptions = RecoOptions(),
    val goalKm: Int = 0,
) {
    /** Pestañas visibles en orden; siempre hay al menos una. */
    fun visibleModules(): List<String> {
        val v = Modules.ALL.filter { it in modules }
        return v.ifEmpty { listOf(Modules.HOME) }
    }
}

class Swatch(val name: String, val argb: Long)

object Swatches {
    val PRIMARY = listOf(
        Swatch("Azul real", 0xFF1F5FBF), Swatch("Marino", 0xFF0F2E5C), Swatch("Cielo", 0xFF2B8FD9),
        Swatch("Índigo", 0xFF3B49C4), Swatch("Turquesa", 0xFF0E8AA0), Swatch("Violeta", 0xFF6B4FC8),
    )
    val ACCENT = listOf(
        Swatch("Ámbar", 0xFFE9A23B), Swatch("Coral", 0xFFE4604B), Swatch("Lima", 0xFF9BB82B),
        Swatch("Cian", 0xFF2BA9C9), Swatch("Magenta", 0xFFC13C9E),
    )
}

/** Catálogo de cifras de cada bloque: id, texto y si está activada por defecto. */
class MetricDef(val id: String, val label: String, val default: Boolean)

object Blocks {
    const val RECO = "reco"
    const val TODAY = "today"
    const val YESTERDAY = "yesterday"
    const val WEEK = "week"
    const val MONTH = "month"
    const val YEAR = "year"
    const val LAST = "last"
    const val GOAL = "goal"
    const val TRENDS = "trends"
    const val RANGE = "range"

    val ORDER = listOf(RECO, TODAY, YESTERDAY, WEEK, MONTH, YEAR, LAST, GOAL, TRENDS, RANGE)
    val CORE = setOf(RECO, TODAY, YESTERDAY, WEEK, MONTH, YEAR)

    val TITLE = mapOf(
        RECO to "Recomendación de hoy", TODAY to "Estado de hoy", YESTERDAY to "Ayer",
        WEEK to "Esta semana", MONTH to "Mes en curso", YEAR to "Este año",
        LAST to "Última actividad", GOAL to "Objetivo anual", TRENDS to "¿Mejoro o empeoro?",
        RANGE to "Rango personalizado",
    )
    val NOTE = mapOf(
        RECO to "Según tu descanso y tu carga reciente", TODAY to "Franja superior con lo esencial",
        YESTERDAY to "Lo que hiciste el día anterior", WEEK to "De lunes a hoy",
        MONTH to "Del día 1 a hoy", YEAR to "Desde el 1 de enero",
        LAST to "La más reciente, sea del día que sea", GOAL to "Km con barra de progreso y ritmo",
        TRENDS to "Potencia, eficiencia y FC frente al periodo anterior",
        RANGE to "Últimos 7, 30 o 90 días",
    )

    val METRICS: Map<String, List<MetricDef>> = mapOf(
        TODAY to listOf(
            MetricDef("sleep", "Sueño", true), MetricDef("hrv", "VFC", true), MetricDef("rhr", "FC reposo", true),
            MetricDef("load", "Carga 7/28 días", true), MetricDef("battery", "Body Battery ganada", false),
            MetricDef("steps", "Pasos de hoy", false), MetricDef("weight", "Peso", false),
        ),
        YESTERDAY to listOf(
            MetricDef("dist", "Distancia", true), MetricDef("time", "Tiempo", true), MetricDef("elev", "Desnivel", true),
            MetricDef("power", "Potencia", true), MetricDef("hr", "FC media", true), MetricDef("speed", "Velocidad", true),
            MetricDef("steps", "Pasos", true), MetricDef("kcal", "Calorías", true),
            MetricDef("hrmax", "FC máxima", false), MetricDef("te", "Efecto aeróbico", false), MetricDef("sleep", "Sueño de esa noche", false),
        ),
        WEEK to listOf(
            MetricDef("km", "Km", true), MetricDef("perday", "Km por día", true), MetricDef("vsprev", "Vs semana anterior", true),
            MetricDef("sessions", "Salidas", true), MetricDef("time", "Tiempo", true), MetricDef("elev", "Desnivel", true),
            MetricDef("steps", "Pasos / día", true), MetricDef("sleep", "Sueño medio", true), MetricDef("intensity", "Minutos de intensidad", true),
            MetricDef("hrv", "VFC media", false), MetricDef("rhr", "FC reposo media", false),
            MetricDef("kcal", "Calorías de actividades", false), MetricDef("power", "Potencia media", false),
        ),
        MONTH to listOf(
            MetricDef("km", "Km", true), MetricDef("pace", "Ritmo vs media", true), MetricDef("sessions", "Actividades", true),
            MetricDef("time", "Tiempo", true), MetricDef("projection", "Proyección", true), MetricDef("prevmonth", "Mes anterior", true),
            MetricDef("elev", "Desnivel", false), MetricDef("best", "Mejor salida", false),
            MetricDef("eff", "Eficiencia W/lpm", false), MetricDef("sleep", "Sueño medio", false), MetricDef("weight", "Peso", false),
        ),
        YEAR to listOf(
            MetricDef("km", "Km", true), MetricDef("permonth", "Km por mes", true), MetricDef("avgmonth", "Media mensual", true),
            MetricDef("hours", "Horas", true), MetricDef("sessions", "Actividades", true), MetricDef("vo2", "VO2 máx.", true),
            MetricDef("weight", "Peso", true), MetricDef("bestmonth", "Mejor mes", true),
            MetricDef("elev", "Desnivel acumulado", false), MetricDef("eff", "Eficiencia W/lpm", false), MetricDef("sleep", "Sueño medio", false),
        ),
        RANGE to listOf(
            MetricDef("km", "Km", true), MetricDef("time", "Tiempo", true), MetricDef("sessions", "Actividades", true),
            MetricDef("elev", "Desnivel", true), MetricDef("steps", "Pasos / día", true), MetricDef("sleep", "Sueño medio", true),
            MetricDef("power", "Potencia media", false), MetricDef("hr", "FC media", false),
            MetricDef("hrv", "VFC media", false), MetricDef("rhr", "FC reposo media", false),
        ),
    )
}

object Defaults {
    fun blocks(): List<HomeBlock> = Blocks.ORDER.map { id ->
        HomeBlock(
            id = id,
            enabled = id in Blocks.CORE,
            metrics = Blocks.METRICS[id]?.filter { it.default }?.map { it.id }?.toSet() ?: emptySet(),
        )
    }
}

/** Guarda los ajustes de la interfaz en el móvil (no son secretos; los tokens van aparte y cifrados). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val raw = prefs.getString("json", null) ?: return AppSettings()
        return try {
            parse(JSONObject(raw))
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun save(s: AppSettings) {
        prefs.edit().putString("json", toJson(s).toString()).apply()
    }

    private fun strSet(a: JSONArray?): Set<String> =
        if (a == null) emptySet() else (0 until a.length()).map { a.getString(it) }.toSet()

    private fun parse(o: JSONObject): AppSettings {
        val base = AppSettings()
        val saved = o.optJSONArray("blocks")
        val parsed = ArrayList<HomeBlock>()
        if (saved != null) {
            for (i in 0 until saved.length()) {
                val b = saved.getJSONObject(i)
                val id = b.getString("id")
                if (id !in Blocks.ORDER) continue
                parsed.add(
                    HomeBlock(
                        id = id,
                        enabled = b.optBoolean("enabled", true),
                        metrics = strSet(b.optJSONArray("metrics")),
                        days = b.optInt("days", 30),
                        sumAll = b.optBoolean("sumAll", false),
                    ),
                )
            }
        }
        // Si en una versión nueva aparece un bloque que aún no estaba guardado, se añade al final.
        for (def in Defaults.blocks()) if (parsed.none { it.id == def.id }) parsed.add(def)
        val r = o.optJSONObject("reco")
        return base.copy(
            modules = o.optJSONArray("modules")?.let { strSet(it) } ?: base.modules,
            blocks = parsed,
            training = o.optJSONArray("training")?.let { strSet(it) } ?: base.training,
            theme = runCatching { ThemeMode.valueOf(o.optString("theme", "SYSTEM")) }.getOrDefault(ThemeMode.SYSTEM),
            primary = o.optLong("primary", base.primary),
            accent = o.optLong("accent", base.accent),
            reco = if (r == null) base.reco else RecoOptions(
                verdict = r.optBoolean("verdict", true),
                session = r.optBoolean("session", true),
                reasons = r.optBoolean("reasons", true),
                tomorrow = r.optBoolean("tomorrow", true),
            ),
            goalKm = o.optInt("goalKm", 0),
        )
    }

    private fun toJson(s: AppSettings): JSONObject {
        val blocks = JSONArray()
        s.blocks.forEach { b ->
            blocks.put(
                JSONObject()
                    .put("id", b.id).put("enabled", b.enabled)
                    .put("metrics", JSONArray(b.metrics.toList()))
                    .put("days", b.days).put("sumAll", b.sumAll),
            )
        }
        return JSONObject()
            .put("modules", JSONArray(s.modules.toList()))
            .put("blocks", blocks)
            .put("training", JSONArray(s.training.toList()))
            .put("theme", s.theme.name)
            .put("primary", s.primary)
            .put("accent", s.accent)
            .put("reco", JSONObject().put("verdict", s.reco.verdict).put("session", s.reco.session).put("reasons", s.reco.reasons).put("tomorrow", s.reco.tomorrow))
            .put("goalKm", s.goalKm)
    }
}
