package com.castigaro.weightsonar.analysis

import com.castigaro.weightsonar.data.DayRecord
import com.castigaro.weightsonar.data.FoodEntry
import com.castigaro.weightsonar.data.Nutrients
import com.castigaro.weightsonar.data.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NutritionCalcTest {

    // ---- Grundumsatz (Mifflin-St Jeor) ----

    @Test
    fun `BMR Mann folgt Mifflin-St Jeor`() {
        // 10·80 + 6,25·180 − 5·40 + 5 = 1730
        assertEquals(1730.0, NutritionCalc.bmr(Person.SEX_MALE, 80.0, 180.0, 40), 1e-9)
    }

    @Test
    fun `BMR Frau folgt Mifflin-St Jeor`() {
        // 10·65 + 6,25·165 − 5·35 − 161 = 1345,25
        assertEquals(1345.25, NutritionCalc.bmr(Person.SEX_FEMALE, 65.0, 165.0, 35), 1e-9)
    }

    // ---- BMI und Alter ----

    @Test
    fun `BMI ist Gewicht durch Groesse im Quadrat`() {
        assertEquals(80.0 / (1.8 * 1.8), NutritionCalc.bmi(80.0, 180.0), 1e-9)
    }

    @Test
    fun `Alter springt erst am Geburtstag um`() {
        val birth = LocalDate.parse("1990-06-15")
        assertEquals(35, NutritionCalc.ageYears(birth, LocalDate.parse("2026-06-14")))
        assertEquals(36, NutritionCalc.ageYears(birth, LocalDate.parse("2026-06-15")))
    }

    // ---- Ampel-Schwellen ----

    @Test
    fun `Ampel wechselt bei 0 und 250 kcal Saldo`() {
        assertEquals(NutritionCalc.Ampel.GREEN, NutritionCalc.ampelFor(-500.0))
        assertEquals(NutritionCalc.Ampel.GREEN, NutritionCalc.ampelFor(0.0))
        assertEquals(NutritionCalc.Ampel.YELLOW, NutritionCalc.ampelFor(1.0))
        assertEquals(NutritionCalc.Ampel.YELLOW, NutritionCalc.ampelFor(250.0))
        assertEquals(NutritionCalc.Ampel.RED, NutritionCalc.ampelFor(251.0))
    }

    @Test
    fun `Fliesskomma-Staub kippt die Ampel an den Grenzen nicht`() {
        // Rundungsreste in der Größenordnung von 1e-10 dürfen nichts ändern.
        assertEquals(NutritionCalc.Ampel.GREEN, NutritionCalc.ampelFor(1e-10))
        assertEquals(NutritionCalc.Ampel.YELLOW, NutritionCalc.ampelFor(250.0 + 1e-10))
    }

    // ---- Tages-Summen ----

    private fun person() = Person(
        name = "Test",
        sex = Person.SEX_MALE,
        birthDate = "1980-01-01",
        heightCm = 180.0,
    )

    private fun food(grams: Double, kcalPer100: Double) = FoodEntry(
        name = "Testessen",
        grams = grams,
        per100g = Nutrients(energyKcal = kcalPer100, fat = 10.0, protein = 5.0),
    )

    @Test
    fun `Beitrag ist Nährwerte pro 100 g mal Menge`() {
        val entry = food(grams = 150.0, kcalPer100 = 200.0)
        assertEquals(300.0, entry.contribution().energyKcal, 1e-9)
        assertEquals(15.0, entry.contribution().fat, 1e-9)
    }

    @Test
    fun `summarize bilanziert Aufnahme gegen Grundumsatz plus Aktivitaeten`() {
        val date = LocalDate.parse("2026-07-01") // Alter 46 → BMR 1700 bei 80 kg
        val day = DayRecord(personId = "p", date = date.toString())
        day.foods.add(food(grams = 100.0, kcalPer100 = 1800.0))
        day.activities.add(
            com.castigaro.weightsonar.data.ActivityEntry(
                name = "Radfahren", minutes = 30, kcalPerHour = 400.0))

        val summary = NutritionCalc.summarize(person(), day, weightKg = 80.0, date = date)
        assertEquals(1700.0, summary.bmr!!, 1e-9)
        assertEquals(200.0, summary.activityKcal, 1e-9)
        assertEquals(1900.0, summary.budget!!, 1e-9)
        assertEquals(-100.0, summary.saldo!!, 1e-9)
        assertEquals(NutritionCalc.Ampel.GREEN, summary.ampel)
    }

    @Test
    fun `ohne Gewicht gibt es kein Budget und keine Ampel`() {
        val date = LocalDate.parse("2026-07-01")
        val day = DayRecord(personId = "p", date = date.toString())
        day.foods.add(food(grams = 100.0, kcalPer100 = 500.0))

        val summary = NutritionCalc.summarize(person(), day, weightKg = null, date = date)
        assertNull(summary.budget)
        assertNull(summary.saldo)
        assertEquals(NutritionCalc.Ampel.NONE, summary.ampel)
    }

    @Test
    fun `ohne Essen bleibt die Ampel aus`() {
        val date = LocalDate.parse("2026-07-01")
        val summary = NutritionCalc.summarize(person(), null, weightKg = 80.0, date = date)
        assertEquals(NutritionCalc.Ampel.NONE, summary.ampel)
        assertEquals(0.0, summary.intake.energyKcal, 1e-9)
    }
}
