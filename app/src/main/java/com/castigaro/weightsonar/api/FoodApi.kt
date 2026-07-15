package com.castigaro.weightsonar.api

import android.content.Context
import com.castigaro.weightsonar.data.Nutrients
import com.castigaro.weightsonar.util.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Erkennt Essen auf Fotos — zwei Pfade: ein Gericht (Positionen samt
 * geschätzter Menge) oder eine Nährwerttabelle (ein Wertesatz pro 100 g,
 * die gegessene Menge trägt der Nutzer ein). Unsichere Werte werden
 * markiert, nie geraten. Gerechnet wird in Kotlin, nie von der KI.
 */
object FoodApi {

    /** Eine erkannte Position; [grams] = gegessene Menge, Nährwerte pro 100 g. */
    data class Item(
        var name: String,
        var grams: Double,
        val per100g: Nutrients,
        var uncertain: Boolean,
    )

    private const val JSON_PER100G =
        """{"energyKcal":Zahl,"fat":Zahl,"saturatedFat":Zahl,"carbs":Zahl,"sugar":Zahl,"protein":Zahl,"salt":Zahl,"fiber":Zahl}"""

    private val MEAL_PROMPT = """
        Du erkennst Lebensmittel auf Essensfotos.
        Erfasse jede sichtbare Position auf dem Teller/im Bild einzeln: Name,
        geschätzte Menge in Gramm und die typischen Nährwerte PRO 100 g
        (EU-Nährwertdeklaration).
        Regeln:
        - "energyKcal" in Kilokalorien pro 100 g, alle anderen Werte in Gramm pro 100 g.
        - Mengen realistisch anhand des Fotos schätzen (Tellergröße als Referenz).
        - Bist du bei einer Position unsicher (Erkennung, Menge oder Nährwerte),
          setze "uncertain": true. NIEMALS raten, ohne es zu markieren.
        - Antworte ausschließlich mit JSON in exakt dieser Form:
          {"items":[{"name":"...","grams":Zahl,"uncertain":false,"per100g":$JSON_PER100G}]}
    """.trimIndent()

    private val LABEL_PROMPT = """
        Du liest Nährwerttabellen von Lebensmittel-Verpackungen ab.
        Erfasse den Produktnamen und die Nährwerte PRO 100 g.
        Regeln:
        - Werte exakt so übernehmen, wie sie für 100 g gedruckt sind — NICHT
          selbst umrechnen. Einzige Ausnahme: Ist die Energie nur in kJ
          angegeben, rechne um: kcal = kJ ÷ 4,184.
        - Fehlt ein Wert auf dem Etikett, trage 0 ein.
        - Ist ein Wert nicht sicher lesbar, setze "uncertain": true und trage
          die beste Lesung ein. NIEMALS raten, ohne es zu markieren.
        - Antworte ausschließlich mit JSON in exakt dieser Form:
          {"name":"...","uncertain":false,"per100g":$JSON_PER100G}
    """.trimIndent()

    /** Gericht: Liste erkannter Positionen mit geschätzten Mengen. */
    suspend fun analyzeMeal(context: Context, photo: File): List<Item> =
        withContext(Dispatchers.IO) {
            val text = LlmClient.request(
                context,
                systemPrompt = MEAL_PROMPT,
                userText = "Erkenne die Lebensmittel auf diesem Foto.",
                imageBase64 = Photos.toBase64(photo),
            )
            val json = LlmClient.extractJson(text)
            val items = mutableListOf<Item>()
            val arr = json.optJSONArray("items") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name").trim()
                val grams = o.optDouble("grams", 0.0)
                if (name.isEmpty() || grams <= 0.0) continue
                items.add(
                    Item(
                        name = name,
                        grams = grams,
                        per100g = parseNutrients(o.optJSONObject("per100g")),
                        uncertain = o.optBoolean("uncertain", false),
                    )
                )
            }
            items
        }

    /** Nährwerttabelle: ein Wertesatz pro 100 g; Menge trägt der Nutzer ein. */
    suspend fun readLabel(context: Context, photo: File): Item =
        withContext(Dispatchers.IO) {
            val text = LlmClient.request(
                context,
                systemPrompt = LABEL_PROMPT,
                userText = "Lies diese Nährwerttabelle ab.",
                imageBase64 = Photos.toBase64(photo),
            )
            val json = LlmClient.extractJson(text)
            Item(
                name = json.optString("name").trim().ifBlank { "" },
                grams = 100.0,
                per100g = parseNutrients(json.optJSONObject("per100g")),
                uncertain = json.optBoolean("uncertain", false),
            )
        }

    private fun parseNutrients(json: JSONObject?): Nutrients =
        json?.let { Nutrients.fromJson(it) } ?: Nutrients()
}
