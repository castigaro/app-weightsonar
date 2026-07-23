package com.appsonar.weightsonar.analysis

import com.appsonar.weightsonar.data.Person
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Baut aus einer Zeitraum-Auswertung die Markdown-Datei: Kopf mit Person und
 * Kennzahlen, Bilanz-Ampel, Nährwert-Tabelle (Summe und Durchschnitt),
 * Aktivitäten und Gewichtsverlauf.
 */
object MarkdownBuilder {

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun fileName(person: Person, from: LocalDate, to: LocalDate): String {
        val slug = person.name.lowercase(Locale.GERMANY)
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-')
            .ifBlank { "person" }
        return "auswertung-$slug-${from}_$to.md"
    }

    fun markdown(person: Person, result: StatsAggregator.Result): String {
        val sb = StringBuilder()
        sb.appendLine("# Ernährungs-Auswertung — ${person.name}")
        sb.appendLine()
        sb.appendLine(
            "**Zeitraum:** ${DATE.format(result.from)} – ${DATE.format(result.to)} " +
                "(${result.dayCount} Tage, ${result.daysWithFood} mit Einträgen)"
        )

        val sexLabel = if (person.sex == Person.SEX_FEMALE) "weiblich" else "männlich"
        val personParts = mutableListOf(sexLabel)
        person.birthLocalDate()?.let {
            personParts.add("${NutritionCalc.ageYears(it, result.to)} Jahre")
        }
        personParts.add("${NutritionCalc.formatAmount(person.heightCm)} cm")
        sb.appendLine("**Person:** ${person.name} (${personParts.joinToString(", ")})")

        val weight = result.endWeightKg
        if (weight != null) {
            val bmi = NutritionCalc.bmi(weight, person.heightCm)
            val birth = person.birthLocalDate()
            val bmrText = birth?.let {
                val bmr = NutritionCalc.bmr(
                    person.sex, weight, person.heightCm, NutritionCalc.ageYears(it, result.to))
                ", Grundumsatz ${NutritionCalc.formatKcal(bmr)} kcal"
            } ?: ""
            sb.appendLine(
                "**Stand:** ${NutritionCalc.formatAmount(weight)} kg, " +
                    "BMI ${NutritionCalc.formatAmount(bmi)}$bmrText"
            )
        }
        sb.appendLine()

        sb.append("**Bilanz:** 🟢 ${result.greenCount} · 🟡 ${result.yellowCount} · " +
            "🔴 ${result.redCount} · ✕ ${result.missingCount} ohne Daten")
        result.saldoTotal?.let {
            sb.append(" — Saldo gesamt ${NutritionCalc.formatSignedKcal(it)} kcal")
        }
        sb.appendLine()
        sb.appendLine()

        sb.appendLine("## Nährwerte")
        sb.appendLine()
        sb.appendLine("| Nährwert | Summe | Ø je Tag mit Einträgen |")
        sb.appendLine("|---|---|---|")
        val totals = result.totals
        val averages = result.averages()
        fun row(label: String, total: Double, average: Double?, kcal: Boolean = false) {
            val sum = if (kcal) NutritionCalc.formatKcal(total) else NutritionCalc.formatAmount(total)
            val avg = average?.let {
                if (kcal) NutritionCalc.formatKcal(it) else NutritionCalc.formatAmount(it)
            } ?: "—"
            sb.appendLine("| $label | $sum | $avg |")
        }
        row("Energie (kcal)", totals.energyKcal, averages?.energyKcal, kcal = true)
        row("Fett (g)", totals.fat, averages?.fat)
        row("davon gesättigte Fettsäuren (g)", totals.saturatedFat, averages?.saturatedFat)
        row("Kohlenhydrate (g)", totals.carbs, averages?.carbs)
        row("davon Zucker (g)", totals.sugar, averages?.sugar)
        row("Eiweiß (g)", totals.protein, averages?.protein)
        row("Salz (g)", totals.salt, averages?.salt)
        row("Ballaststoffe (g)", totals.fiber, averages?.fiber)
        sb.appendLine()

        sb.appendLine("## Aktivitäten")
        sb.appendLine()
        if (result.activitySums.isEmpty()) {
            sb.appendLine("Keine Aktivitäten gebucht.")
        } else {
            sb.appendLine("Gesamt: ${NutritionCalc.formatKcal(result.activityKcalTotal)} kcal verbrannt.")
            sb.appendLine()
            result.activitySums.forEach {
                sb.appendLine(
                    "- ${it.name}: ${it.times}× · ${it.minutes} min · " +
                        "${NutritionCalc.formatKcal(it.kcal)} kcal"
                )
            }
        }
        sb.appendLine()

        sb.appendLine("## Gewichtsverlauf")
        sb.appendLine()
        if (result.weightSeries.isEmpty()) {
            sb.appendLine("Kein Gewicht im Zeitraum erfasst.")
        } else {
            val first = result.weightSeries.first()
            val last = result.weightSeries.last()
            if (result.weightSeries.size > 1) {
                val delta = last.second - first.second
                val sign = if (delta > 0) "+" else if (delta < 0) "−" else "±"
                sb.appendLine(
                    "Veränderung: $sign${NutritionCalc.formatAmount(kotlin.math.abs(delta))} kg " +
                        "(${DATE.format(first.first)} bis ${DATE.format(last.first)})"
                )
                sb.appendLine()
            }
            sb.appendLine("| Datum | Gewicht (kg) |")
            sb.appendLine("|---|---|")
            result.weightSeries.forEach { (date, kg) ->
                sb.appendLine("| ${DATE.format(date)} | ${NutritionCalc.formatAmount(kg)} |")
            }
        }
        sb.appendLine()
        sb.appendLine("_Erstellt mit WeightSonar. Keine ärztliche Beratung._")
        return sb.toString()
    }
}
