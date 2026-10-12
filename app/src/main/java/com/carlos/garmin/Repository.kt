package com.carlos.garmin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Descarga de Garmin Connect todo lo que muestra la app. */
object Repository {
    private val START_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend fun load(client: GarminClient, today: LocalDate): DashboardData = coroutineScope {
        val gate = Semaphore(4)
        val warnings = java.util.Collections.synchronizedList(ArrayList<String>())

        suspend fun <T> io(block: () -> T): T = gate.withPermit { withContext(Dispatchers.IO) { block() } }

        suspend fun <T> safe(what: String, empty: T, block: () -> T): T {
            return try {
                io(block)
            } catch (e: GarminException) {
                if (e.code == 401 || e.code == 403 || e.code == 429) throw e
                warnings.add("$what: ${e.message}")
                empty
            } catch (e: Exception) {
                warnings.add("$what: ${e.message ?: e.javaClass.simpleName}")
                empty
            }
        }

        val profile = io { client.getJson("/userprofile-service/socialProfile") } as? JSONObject
        val name = profile?.strOrNull("displayName")
            ?: throw GarminException("No se pudo leer el perfil de Garmin.", 401)

        val yearStart = LocalDate.of(today.year, 1, 1)
        val dailyFrom = today.minusDays(55)

        val sleepJob = async {
            val nights = (0L until 28L).map { back ->
                val d = today.minusDays(back)
                async { safe("Sueño $d", null as SleepNight?) { fetchSleep(client, name, d) } }
            }.awaitAll().filterNotNull().sortedBy { it.date }
            nights
        }
        val hrvJob = async {
            chunks(dailyFrom, today, 28).map { (s, e) ->
                async { safe("HRV", emptyList<HrvDay>()) { fetchHrv(client, s, e) } }
            }.awaitAll().flatten().sortedBy { it.date }
        }
        val rhrJob = async { safe("FC en reposo", emptyList<DayValue>()) { fetchRhr(client, name, dailyFrom, today) } }
        val stepsJob = async {
            chunks(dailyFrom, today, 28).map { (s, e) ->
                async { safe("Pasos", emptyList<StepsDay>()) { fetchSteps(client, s, e) } }
            }.awaitAll().flatten().sortedBy { it.date }
        }
        val intensityJob = async {
            safe("Minutos de intensidad", emptyList<WeekIntensity>()) {
                fetchIntensity(client, today.minusWeeks(26), today)
            }
        }
        val stressJob = async { safe("Estrés semanal", emptyList<DayValue>()) { fetchStress(client, today, 26) } }
        val vo2Job = async { safe("VO2 máx.", emptyList<DayValue>()) { fetchVo2(client, today.minusDays(200), today) } }
        val weightJob = async { safe("Peso", emptyList<DayValue>()) { fetchWeight(client, today.minusDays(400), today) } }
        val actsJob = async { safe("Actividades", emptyList<Activity>()) { fetchActivities(client, yearStart, today) } }

        DashboardData(
            today = today,
            sleep = sleepJob.await(),
            hrv = hrvJob.await(),
            rhr = rhrJob.await(),
            steps = stepsJob.await(),
            intensity = intensityJob.await(),
            stress = stressJob.await(),
            vo2 = vo2Job.await(),
            weight = weightJob.await(),
            activities = actsJob.await(),
            warnings = warnings.toList(),
        )
    }

    private fun chunks(from: LocalDate, to: LocalDate, days: Long): List<Pair<LocalDate, LocalDate>> {
        val out = ArrayList<Pair<LocalDate, LocalDate>>()
        var s = from
        while (!s.isAfter(to)) {
            val cand = s.plusDays(days - 1)
            val e = if (cand.isAfter(to)) to else cand
            out.add(Pair(s, e))
            s = e.plusDays(1)
        }
        return out
    }

    private fun fetchSleep(c: GarminClient, name: String, day: LocalDate): SleepNight? {
        val root = c.getJson(
            "/wellness-service/wellness/dailySleepData/$name",
            listOf("date" to day.toString(), "nonSleepBufferMinutes" to "60"),
        ) as? JSONObject ?: return null
        val dto = root.objOrNull("dailySleepDTO") ?: return null
        val total = dto.dblOrNull("sleepTimeSeconds") ?: return null
        if (total <= 0.0) return null
        val h = { k: String -> (dto.dblOrNull(k) ?: 0.0) / 3600.0 }
        return SleepNight(
            date = day,
            score = dto.objOrNull("sleepScores")?.objOrNull("overall")?.intOrNull("value"),
            hours = total / 3600.0,
            deepH = h("deepSleepSeconds"),
            lightH = h("lightSleepSeconds"),
            remH = h("remSleepSeconds"),
            awakeH = h("awakeSleepSeconds"),
            avgHr = dto.dblOrNull("avgHeartRate"),
            bodyBattery = root.intOrNull("bodyBatteryChange"),
            hrvNight = root.dblOrNull("avgOvernightHrv"),
            rhr = root.intOrNull("restingHeartRate"),
        )
    }

    private fun fetchHrv(c: GarminClient, s: LocalDate, e: LocalDate): List<HrvDay> {
        val root = c.getJson("/hrv-service/hrv/daily/$s/$e") as? JSONObject ?: return emptyList()
        val arr = root.arrOrNull("hrvSummaries") ?: return emptyList()
        return arr.objects().mapNotNull { o ->
            val d = o.strOrNull("calendarDate")?.let { LocalDate.parse(it) } ?: return@mapNotNull null
            val base = o.objOrNull("baseline")
            HrvDay(
                date = d,
                lastNight = o.intOrNull("lastNightAvg"),
                weeklyAvg = o.intOrNull("weeklyAvg"),
                balancedLow = base?.intOrNull("balancedLow"),
                balancedHigh = base?.intOrNull("balancedUpper"),
                status = o.strOrNull("status"),
            )
        }
    }

    private fun fetchRhr(c: GarminClient, name: String, s: LocalDate, e: LocalDate): List<DayValue> {
        val root = c.getJson(
            "/userstats-service/wellness/daily/$name",
            listOf("fromDate" to s.toString(), "untilDate" to e.toString(), "metricId" to "60"),
        ) as? JSONObject ?: return emptyList()
        val rows = root.objOrNull("allMetrics")?.objOrNull("metricsMap")?.arrOrNull("WELLNESS_RESTING_HEART_RATE")
            ?: return emptyList()
        return rows.objects().mapNotNull { o ->
            val d = o.strOrNull("calendarDate")?.let { LocalDate.parse(it) } ?: return@mapNotNull null
            val v = o.dblOrNull("value") ?: return@mapNotNull null
            DayValue(d, v)
        }.sortedBy { it.date }
    }

    private fun fetchSteps(c: GarminClient, s: LocalDate, e: LocalDate): List<StepsDay> {
        val arr = c.getJson("/usersummary-service/stats/steps/daily/$s/$e") as? JSONArray ?: return emptyList()
        return arr.objects().mapNotNull { o ->
            val d = o.strOrNull("calendarDate")?.let { LocalDate.parse(it) } ?: return@mapNotNull null
            StepsDay(d, o.intOrNull("totalSteps") ?: 0, o.dblOrNull("totalDistance") ?: 0.0, o.intOrNull("stepGoal"))
        }
    }

    private fun fetchIntensity(c: GarminClient, s: LocalDate, e: LocalDate): List<WeekIntensity> {
        val arr = c.getJson("/usersummary-service/stats/im/weekly/$s/$e") as? JSONArray ?: return emptyList()
        return arr.objects().mapNotNull { o ->
            val d = o.strOrNull("calendarDate")?.let { LocalDate.parse(it) } ?: return@mapNotNull null
            WeekIntensity(d, o.intOrNull("weeklyGoal") ?: 150, o.intOrNull("moderateValue") ?: 0, o.intOrNull("vigorousValue") ?: 0)
        }.sortedBy { it.week }
    }

    private fun fetchStress(c: GarminClient, end: LocalDate, weeks: Int): List<DayValue> {
        val arr = c.getJson("/usersummary-service/stats/stress/weekly/$end/$weeks") as? JSONArray ?: return emptyList()
        return arr.objects().mapNotNull { o ->
            val d = o.strOrNull("calendarDate")?.let { LocalDate.parse(it) } ?: return@mapNotNull null
            val v = o.dblOrNull("value") ?: return@mapNotNull null
            DayValue(d, v)
        }.sortedBy { it.date }
    }

    private fun fetchVo2(c: GarminClient, s: LocalDate, e: LocalDate): List<DayValue> {
        val arr = c.getJson("/metrics-service/metrics/maxmet/daily/$s/$e") as? JSONArray ?: return emptyList()
        return arr.objects().mapNotNull { o ->
            val block = o.objOrNull("cycling") ?: o.objOrNull("generic") ?: return@mapNotNull null
            val d = block.strOrNull("calendarDate")?.let { LocalDate.parse(it) } ?: return@mapNotNull null
            val v = block.dblOrNull("vo2MaxPreciseValue") ?: block.dblOrNull("vo2MaxValue") ?: return@mapNotNull null
            DayValue(d, v)
        }.sortedBy { it.date }
    }

    private fun fetchWeight(c: GarminClient, s: LocalDate, e: LocalDate): List<DayValue> {
        val root = c.getJson("/weight-service/weight/range/$s/$e", listOf("includeAll" to "true")) as? JSONObject
            ?: return emptyList()
        val arr = root.arrOrNull("dailyWeightSummaries") ?: return emptyList()
        return arr.objects().mapNotNull { o ->
            val latest = o.objOrNull("latestWeight") ?: return@mapNotNull null
            val d = (latest.strOrNull("calendarDate") ?: o.strOrNull("summaryDate"))?.let { LocalDate.parse(it) }
                ?: return@mapNotNull null
            val grams = latest.dblOrNull("weight") ?: return@mapNotNull null
            DayValue(d, grams / 1000.0)
        }.sortedBy { it.date }
    }

    private fun fetchActivities(c: GarminClient, s: LocalDate, e: LocalDate): List<Activity> {
        val out = ArrayList<Activity>()
        var start = 0
        val limit = 100
        repeat(15) {
            val arr = c.getJson(
                "/activitylist-service/activities/search/activities",
                listOf("startDate" to s.toString(), "endDate" to e.toString(), "start" to start.toString(), "limit" to limit.toString()),
            ) as? JSONArray ?: return out
            val items = arr.objects()
            for (o in items) {
                val st = o.strOrNull("startTimeLocal")?.let { runCatching { LocalDateTime.parse(it, START_FMT) }.getOrNull() }
                    ?: continue
                val speed = o.dblOrNull("averageSpeed")
                out.add(
                    Activity(
                        id = o.optLong("activityId"),
                        name = o.strOrNull("activityName") ?: "Actividad",
                        type = o.objOrNull("activityType")?.strOrNull("typeKey") ?: "other",
                        start = st,
                        km = (o.dblOrNull("distance") ?: 0.0) / 1000.0,
                        minutes = (o.dblOrNull("duration") ?: 0.0) / 60.0,
                        elevationM = o.dblOrNull("elevationGain"),
                        hrAvg = o.dblOrNull("averageHR"),
                        hrMax = o.dblOrNull("maxHR"),
                        speedKmh = speed?.let { it * 3.6 },
                        powerW = o.dblOrNull("avgPower"),
                        kcal = o.dblOrNull("calories"),
                        aerobicTe = o.dblOrNull("aerobicTrainingEffect"),
                    ),
                )
            }
            if (items.size < limit) return out
            start += limit
        }
        return out
    }
}
