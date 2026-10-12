package com.carlos.garmin

import java.time.LocalDate
import java.time.LocalDateTime

data class SleepNight(
    val date: LocalDate,
    val score: Int?,
    val hours: Double,
    val deepH: Double,
    val lightH: Double,
    val remH: Double,
    val awakeH: Double,
    val avgHr: Double?,
    val bodyBattery: Int?,
    val hrvNight: Double?,
    val rhr: Int?,
)

data class HrvDay(
    val date: LocalDate,
    val lastNight: Int?,
    val weeklyAvg: Int?,
    val balancedLow: Int?,
    val balancedHigh: Int?,
    val status: String?,
)

data class DayValue(val date: LocalDate, val value: Double)

data class StepsDay(val date: LocalDate, val steps: Int, val distanceM: Double, val goal: Int?)

data class WeekIntensity(val week: LocalDate, val goal: Int, val moderate: Int, val vigorous: Int)

data class Activity(
    val id: Long,
    val name: String,
    val type: String,
    val start: LocalDateTime,
    val km: Double,
    val minutes: Double,
    val elevationM: Double?,
    val hrAvg: Double?,
    val hrMax: Double?,
    val speedKmh: Double?,
    val powerW: Double?,
    val kcal: Double?,
    val aerobicTe: Double?,
) {
    val date: LocalDate get() = start.toLocalDate()
    val isBike: Boolean get() = type in BIKE_TYPES

    companion object {
        val BIKE_TYPES = setOf("road_biking", "mountain_biking", "indoor_cycling", "gravel_cycling", "cycling", "virtual_ride")
    }
}

data class DashboardData(
    val today: LocalDate,
    val sleep: List<SleepNight>,
    val hrv: List<HrvDay>,
    val rhr: List<DayValue>,
    val steps: List<StepsDay>,
    val intensity: List<WeekIntensity>,
    val stress: List<DayValue>,
    val vo2: List<DayValue>,
    val weight: List<DayValue>,
    val activities: List<Activity>,
    val warnings: List<String>,
)
