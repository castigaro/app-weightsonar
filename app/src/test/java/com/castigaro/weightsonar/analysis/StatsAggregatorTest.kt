package com.castigaro.weightsonar.analysis

import com.castigaro.weightsonar.data.ActivityEntry
import com.castigaro.weightsonar.data.DayRecord
import com.castigaro.weightsonar.data.FoodEntry
import com.castigaro.weightsonar.data.Nutrients
import com.castigaro.weightsonar.data.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StatsAggregatorTest {

    // Alter 46 im Juli 2026 → BMR bei 80 kg: 10·80 + 6,25·180 − 5·46 + 5 = 1700.
    private val person = Person(
        name = "Test",
        sex = Person.SEX_MALE,
        birthDate = "1980-01-01",
        heightCm = 180.0,
    )

    private val today = LocalDate.parse("2026-07-05")
    private val from = LocalDate.parse("2026-07-01")
    private val to = LocalDate.parse("2026-07-05")

    private fun day(
        date: String,
        kcal: Double? = null,
        weight: Double? = null,
        activityKcalPerHour: Double? = null,
    ): DayRecord {
        val record = DayRecord(personId = "p", date = date)
        kcal?.let {
            record.foods.add(
                FoodEntry(name = "Essen", grams = 100.0, per100g = Nutrients(energyKcal = it, fat = 10.0)))
        }
        weight?.let { record.weightKg = it }
        activityKcalPerHour?.let {
            record.activities.add(ActivityEntry(name = "Radfahren", minutes = 30, kcalPerHour = it))
        }
        return record
    }

    private fun aggregate(records: List<DayRecord>) =
        StatsAggregator.aggregate(person, records, from, to, today)

    @Test
    fun `summiert Zeitraum und zaehlt die Ampeln`() {
        val result = aggregate(
            listOf(
                day("2026-07-01", kcal = 1700.0, weight = 80.0),            // Saldo 0 → grün
                day("2026-07-03", kcal = 2000.0, activityKcalPerHour = 400.0), // 2000−1900 → gelb
                day("2026-07-04", kcal = 2500.0, weight = 79.0),            // BMR 1690 → +810 rot
            )
        )
        assertEquals(5, result.dayCount)
        assertEquals(3, result.daysWithFood)
        assertEquals(6200.0, result.totals.energyKcal, 1e-9)
        assertEquals(6200.0 / 3, result.averages()!!.energyKcal, 1e-9)
        assertEquals(200.0, result.activityKcalTotal, 1e-9)
        assertEquals(0.0 + 100.0 + 810.0, result.saldoTotal!!, 1e-9)
        assertEquals(1, result.greenCount)
        assertEquals(1, result.yellowCount)
        assertEquals(1, result.redCount)
        // 02.07. ist vergangen und leer; der heutige 05.07. zählt nicht als fehlend.
        assertEquals(1, result.missingCount)
        assertEquals(79.0, result.endWeightKg!!, 1e-9)
    }

    @Test
    fun `Gewicht wird ueber die Tage fortgeschrieben`() {
        val result = aggregate(
            listOf(
                day("2026-07-01", kcal = 1700.0, weight = 80.0),
                day("2026-07-03", kcal = 1700.0), // kein Gewicht → 80 kg gilt weiter
            )
        )
        val day3 = result.points.first { it.date == LocalDate.parse("2026-07-03") }
        assertEquals(1700.0, day3.budget!!, 1e-9)
        assertEquals(NutritionCalc.Ampel.GREEN, day3.ampel)
    }

    @Test
    fun `Startgewicht kommt auch aus Tagen vor dem Zeitraum`() {
        val result = aggregate(
            listOf(
                day("2026-06-28", weight = 80.0), // liegt vor dem Zeitraum
                day("2026-07-01", kcal = 1700.0),
            )
        )
        val day1 = result.points.first { it.date == from }
        assertEquals(1700.0, day1.budget!!, 1e-9)
    }

    @Test
    fun `ohne jedes Gewicht bleibt der Saldo unbestimmt`() {
        val result = aggregate(listOf(day("2026-07-01", kcal = 1700.0)))
        assertNull(result.saldoTotal)
        assertNull(result.points.first().budget)
    }

    @Test
    fun `Gewichtsreihe enthaelt nur Tage mit erfasstem Gewicht`() {
        val result = aggregate(
            listOf(
                day("2026-07-01", kcal = 1700.0, weight = 80.0),
                day("2026-07-03", kcal = 1700.0),
                day("2026-07-04", weight = 79.0),
            )
        )
        assertEquals(
            listOf(
                LocalDate.parse("2026-07-01") to 80.0,
                LocalDate.parse("2026-07-04") to 79.0,
            ),
            result.weightSeries,
        )
    }

    @Test
    fun `Aktivitaeten werden je Name zusammengefasst`() {
        val result = aggregate(
            listOf(
                day("2026-07-01", activityKcalPerHour = 400.0),
                day("2026-07-03", activityKcalPerHour = 400.0),
            )
        )
        val sums = result.activitySums.single()
        assertEquals("Radfahren", sums.name)
        assertEquals(2, sums.times)
        assertEquals(60, sums.minutes)
        assertEquals(400.0, sums.kcal, 1e-9)
    }

    @Test
    fun `Tag nur mit Gewicht zaehlt nicht als fehlend`() {
        val result = aggregate(listOf(day("2026-07-02", weight = 80.0)))
        // Fehlend sind 01., 03. und 04.; der 02. hat Daten, der 05. ist heute.
        assertEquals(3, result.missingCount)
    }
}
