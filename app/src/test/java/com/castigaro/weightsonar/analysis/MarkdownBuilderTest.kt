package com.castigaro.weightsonar.analysis

import com.castigaro.weightsonar.data.ActivityEntry
import com.castigaro.weightsonar.data.DayRecord
import com.castigaro.weightsonar.data.FoodEntry
import com.castigaro.weightsonar.data.Nutrients
import com.castigaro.weightsonar.data.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MarkdownBuilderTest {

    private val person = Person(
        name = "Jörg Müß",
        sex = Person.SEX_MALE,
        birthDate = "1980-01-01",
        heightCm = 180.0,
    )

    private val from = LocalDate.parse("2026-07-01")
    private val to = LocalDate.parse("2026-07-05")

    private fun result(): StatsAggregator.Result {
        val day1 = DayRecord(personId = "p", date = "2026-07-01", weightKg = 80.0)
        day1.foods.add(
            FoodEntry(
                name = "Essen", grams = 100.0,
                per100g = Nutrients(energyKcal = 1700.0, fat = 60.0, protein = 90.0),
            )
        )
        day1.activities.add(ActivityEntry(name = "Radfahren", minutes = 30, kcalPerHour = 400.0))
        return StatsAggregator.aggregate(
            person, listOf(day1), from, to, today = LocalDate.parse("2026-07-05"))
    }

    @Test
    fun `Dateiname enthaelt Slug und Zeitraum`() {
        assertEquals(
            "auswertung-joerg-muess-2026-07-01_2026-07-05.md",
            MarkdownBuilder.fileName(person, from, to),
        )
    }

    @Test
    fun `Markdown enthaelt Kopf Ampel Tabelle und Gewicht`() {
        val markdown = MarkdownBuilder.markdown(person, result())

        assertTrue(markdown.startsWith("# Ernährungs-Auswertung — Jörg Müß"))
        assertTrue(markdown.contains("**Zeitraum:** 01.07.2026 – 05.07.2026 (5 Tage, 1 mit Einträgen)"))
        assertTrue(markdown.contains("**Person:** Jörg Müß (männlich, 46 Jahre, 180 cm)"))
        // Stand: 80 kg, BMI 24,7, Grundumsatz 1.700 kcal
        assertTrue(markdown.contains("**Stand:** 80 kg, BMI 24,7, Grundumsatz 1.700 kcal"))
        // Grün am 01.07., drei vergangene Leertage (02.–04.), heute zählt nicht.
        assertTrue(markdown.contains("**Bilanz:** 🟢 1 · 🟡 0 · 🔴 0 · ✕ 3 ohne Daten"))
        assertTrue(markdown.contains("| Nährwert | Summe | Ø je Tag mit Einträgen |"))
        assertTrue(markdown.contains("| Energie (kcal) | 1.700 | 1.700 |"))
        assertTrue(markdown.contains("| Fett (g) | 60 | 60 |"))
        assertTrue(markdown.contains("- Radfahren: 1× · 30 min · 200 kcal"))
        assertTrue(markdown.contains("| 01.07.2026 | 80 |"))
    }

    @Test
    fun `ohne Aktivitaeten und Gewicht stehen die Platzhalter im Bericht`() {
        val empty = StatsAggregator.aggregate(
            person, emptyList(), from, to, today = LocalDate.parse("2026-07-05"))
        val markdown = MarkdownBuilder.markdown(person, empty)

        assertTrue(markdown.contains("Keine Aktivitäten gebucht."))
        assertTrue(markdown.contains("Kein Gewicht im Zeitraum erfasst."))
    }
}
