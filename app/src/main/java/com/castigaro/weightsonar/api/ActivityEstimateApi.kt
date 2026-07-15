package com.castigaro.weightsonar.api

import android.content.Context
import com.castigaro.weightsonar.analysis.NutritionCalc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Schätzt den Energieverbrauch einer frei beschriebenen Aktivität
 * („Rasenmähen", „Situps") in kcal pro Stunde. Der Nutzer kann den Wert
 * übernehmen oder überschreiben — gebucht wird immer der sichtbare Wert.
 */
object ActivityEstimateApi {

    private val SYSTEM_PROMPT = """
        Du schätzt den Energieverbrauch körperlicher Aktivitäten.
        Gib den Verbrauch in Kilokalorien pro Stunde bei durchschnittlicher
        Intensität an; berücksichtige das Körpergewicht, falls angegeben
        (sonst ~75 kg annehmen).
        Antworte ausschließlich mit JSON in exakt dieser Form:
        {"kcalPerHour":Zahl}
    """.trimIndent()

    suspend fun estimate(context: Context, activity: String, weightKg: Double?): Double =
        withContext(Dispatchers.IO) {
            val userText = buildString {
                append("Aktivität: ").append(activity)
                weightKg?.let {
                    append("\nKörpergewicht: ${NutritionCalc.formatAmount(it)} kg")
                }
            }
            val text = LlmClient.request(context, SYSTEM_PROMPT, userText)
            val value = LlmClient.extractJson(text).optDouble("kcalPerHour", 0.0)
            if (value <= 0.0) throw LlmClient.ApiException("Keine Schätzung erhalten.")
            value
        }
}
