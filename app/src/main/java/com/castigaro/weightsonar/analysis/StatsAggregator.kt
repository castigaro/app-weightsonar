package com.castigaro.weightsonar.analysis

import com.castigaro.weightsonar.data.DayRecord
import com.castigaro.weightsonar.data.Nutrients
import com.castigaro.weightsonar.data.Person
import java.time.LocalDate

/**
 * Fasst einen Zeitraum zusammen: Tages-Reihen für die Charts, Summen und
 * Durchschnitte der Nährwerte, Aktivitäts-Kalorien und Ampel-Zählung.
 * Das Gewicht wird über die Tage fortgeschrieben (jüngster bekannter Wert).
 */
object StatsAggregator {

    data class DayPoint(
        val date: LocalDate,
        val hasFood: Boolean,
        val hasData: Boolean,    // irgendetwas erfasst (Essen/Aktivität/Gewicht)
        val intakeKcal: Double,
        val budget: Double?,     // null ohne bekanntes Gewicht
        val saldo: Double?,
        val ampel: NutritionCalc.Ampel,
        val weightKg: Double?,   // nur, wenn an diesem Tag erfasst
    )

    data class ActivitySum(
        val name: String,
        val times: Int,
        val minutes: Int,
        val kcal: Double,
    )

    data class Result(
        val from: LocalDate,
        val to: LocalDate,
        val dayCount: Int,
        val daysWithFood: Int,
        val totals: Nutrients,          // Summe über Tage mit Einträgen
        val activityKcalTotal: Double,
        val saldoTotal: Double?,        // Summe über Tage mit Essen und Budget
        val points: List<DayPoint>,     // jeder Tag des Zeitraums
        val weightSeries: List<Pair<LocalDate, Double>>,
        val activitySums: List<ActivitySum>,
        val greenCount: Int,
        val yellowCount: Int,
        val redCount: Int,
        val missingCount: Int,          // vergangene Tage ganz ohne Daten
        val endWeightKg: Double?,       // fortgeschriebenes Gewicht am Ende
    ) {
        /** Durchschnitt je Tag mit Einträgen — null ohne solche Tage. */
        fun averages(): Nutrients? =
            if (daysWithFood == 0) null else totals.scaled(1.0 / daysWithFood)
    }

    /**
     * [records] dürfen auch Tage vor [from] enthalten — sie liefern das
     * Startgewicht für die Budget-Berechnung.
     */
    fun aggregate(
        person: Person,
        records: List<DayRecord>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): Result {
        val byDate = records.associateBy { it.date }
        var carriedWeight = records
            .filter { it.weightKg != null && it.date < from.toString() }
            .maxByOrNull { it.date }
            ?.weightKg

        val points = mutableListOf<DayPoint>()
        val totals = Nutrients()
        var daysWithFood = 0
        var activityKcalTotal = 0.0
        var saldoTotal = 0.0
        var hasSaldo = false
        var green = 0
        var yellow = 0
        var red = 0
        var missing = 0
        val activityAgg = LinkedHashMap<String, IntArray>() // name -> [times, minutes]
        val activityKcalByName = LinkedHashMap<String, Double>()

        var date = from
        while (!date.isAfter(to)) {
            val record = byDate[date.toString()]
            record?.weightKg?.let { carriedWeight = it }
            val summary = NutritionCalc.summarize(person, record, carriedWeight, date)

            points.add(
                DayPoint(
                    date = date,
                    hasFood = record?.foods?.isNotEmpty() == true,
                    hasData = record != null && !record.isEmpty(),
                    intakeKcal = summary.intake.energyKcal,
                    budget = summary.budget,
                    saldo = summary.saldo,
                    ampel = summary.ampel,
                    weightKg = record?.weightKg,
                )
            )

            if (record != null && record.foods.isNotEmpty()) {
                daysWithFood++
                totals.add(summary.intake)
                summary.saldo?.let {
                    saldoTotal += it
                    hasSaldo = true
                }
            }
            activityKcalTotal += summary.activityKcal
            record?.activities?.forEach { activity ->
                val agg = activityAgg.getOrPut(activity.name) { IntArray(2) }
                agg[0]++
                agg[1] += activity.minutes
                activityKcalByName[activity.name] =
                    (activityKcalByName[activity.name] ?: 0.0) + activity.kcal()
            }

            when (summary.ampel) {
                NutritionCalc.Ampel.GREEN -> green++
                NutritionCalc.Ampel.YELLOW -> yellow++
                NutritionCalc.Ampel.RED -> red++
                NutritionCalc.Ampel.NONE ->
                    if (date.isBefore(today) && (record == null || record.isEmpty())) missing++
            }

            date = date.plusDays(1)
        }

        return Result(
            from = from,
            to = to,
            dayCount = points.size,
            daysWithFood = daysWithFood,
            totals = totals,
            activityKcalTotal = activityKcalTotal,
            saldoTotal = if (hasSaldo) saldoTotal else null,
            points = points,
            weightSeries = points.mapNotNull { p -> p.weightKg?.let { p.date to it } },
            activitySums = activityAgg.map { (name, agg) ->
                ActivitySum(name, agg[0], agg[1], activityKcalByName[name] ?: 0.0)
            },
            greenCount = green,
            yellowCount = yellow,
            redCount = red,
            missingCount = missing,
            endWeightKg = carriedWeight,
        )
    }
}
