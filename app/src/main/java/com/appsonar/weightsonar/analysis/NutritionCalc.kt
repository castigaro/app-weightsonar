package com.appsonar.weightsonar.analysis

import com.appsonar.weightsonar.data.DayRecord
import com.appsonar.weightsonar.data.Nutrients
import com.appsonar.weightsonar.data.Person
import java.time.LocalDate
import java.time.Period
import java.util.Locale

/**
 * Deterministische Berechnung von Grundumsatz, BMI, Tages-Summen und
 * Bilanz-Ampel. Die KI liefert nur Werte und Texte — gerechnet wird hier.
 */
object NutritionCalc {

    enum class Ampel { GREEN, YELLOW, RED, NONE }

    /** Gelb endet bei +250 kcal über dem Budget, darüber Rot. */
    const val YELLOW_MAX_EXCESS_KCAL = 250.0

    // Fließkomma-Staub darf die Ampel an den Grenzen nicht kippen —
    // winzige Toleranz wie im NutriSonar-Aggregator.
    private const val EPSILON = 1e-9

    fun ageYears(birthDate: LocalDate, on: LocalDate): Int =
        Period.between(birthDate, on).years

    /** Grundumsatz nach Mifflin-St Jeor in kcal/Tag. */
    fun bmr(sex: String, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return if (sex == Person.SEX_FEMALE) base - 161.0 else base + 5.0
    }

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val meters = heightCm / 100.0
        return weightKg / (meters * meters)
    }

    /** Summe aller Nährwert-Beiträge eines Tages. */
    fun intake(day: DayRecord): Nutrients =
        Nutrients().also { sum -> day.foods.forEach { sum.add(it.contribution()) } }

    /** Verbrannte kcal aller gebuchten Aktivitäten eines Tages. */
    fun activityKcal(day: DayRecord): Double = day.activities.sumOf { it.kcal() }

    fun ampelFor(saldoKcal: Double): Ampel = when {
        saldoKcal <= 0.0 + EPSILON -> Ampel.GREEN
        saldoKcal <= YELLOW_MAX_EXCESS_KCAL + EPSILON -> Ampel.YELLOW
        else -> Ampel.RED
    }

    data class DaySummary(
        val intake: Nutrients,
        val activityKcal: Double,
        val bmr: Double?,      // null ohne bekanntes Gewicht
        val budget: Double?,   // Grundumsatz + Aktivitäten
        val saldo: Double?,    // aufgenommen − Budget
        val ampel: Ampel,      // NONE ohne Essen oder ohne Budget
    )

    /**
     * Bewertet einen Tag. [weightKg] ist das jüngste bekannte Gewicht bis zu
     * diesem Tag — ohne Gewicht gibt es weder Budget noch Ampel.
     */
    fun summarize(person: Person, day: DayRecord?, weightKg: Double?, date: LocalDate): DaySummary {
        val intake = day?.let { intake(it) } ?: Nutrients()
        val activity = day?.let { activityKcal(it) } ?: 0.0
        val birth = person.birthLocalDate()
        val bmr = if (weightKg != null && birth != null) {
            bmr(person.sex, weightKg, person.heightCm, ageYears(birth, date))
        } else {
            null
        }
        val budget = bmr?.plus(activity)
        val saldo = budget?.let { intake.energyKcal - it }
        val ampel = if (day == null || day.foods.isEmpty() || saldo == null) {
            Ampel.NONE
        } else {
            ampelFor(saldo)
        }
        return DaySummary(intake, activity, bmr, budget, saldo, ampel)
    }

    /** Zahl mit maximal einer Nachkommastelle, deutsches Komma. */
    fun formatAmount(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format("%.1f", value).replace('.', ',')

    /** Ganze kcal mit Tausenderpunkt. */
    fun formatKcal(value: Double): String =
        String.format(Locale.GERMANY, "%,.0f", value)

    /** kcal mit Vorzeichen (+/−) für Salden. */
    fun formatSignedKcal(value: Double): String {
        val abs = formatKcal(kotlin.math.abs(value))
        return when {
            value > 0.5 -> "+$abs"
            value < -0.5 -> "−$abs"
            else -> "±0"
        }
    }
}
