package com.appsonar.weightsonar.api

import android.content.Context
import com.appsonar.common.llm.ModelPricing
import com.appsonar.common.llm.ProviderSettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Gemeinsamer HTTP-Kern für Anthropic (Messages API) und OpenAI (Chat
 * Completions): schickt einen Prompt mit optionalem Bild, bucht die
 * geschätzten Kosten und liefert den Text der Antwort.
 */
object LlmClient {

    private val JSON = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    class ApiException(message: String) : IOException(message)

    fun request(
        context: Context,
        systemPrompt: String,
        userText: String,
        imageBase64: String? = null,
    ): String {
        val config = ProviderSettings.activeConfig(context)
            ?: throw ApiException("Kein aktiver API-Key — in den Einstellungen hinterlegen oder aktivieren.")
        return if (config.provider == ProviderSettings.PROVIDER_OPENAI) {
            requestOpenAi(context, config, systemPrompt, userText, imageBase64)
        } else {
            requestAnthropic(context, config, systemPrompt, userText, imageBase64)
        }
    }

    private fun trackCost(
        context: Context,
        provider: String,
        model: String,
        inputTokens: Long,
        outputTokens: Long,
    ) {
        val micros = ModelPricing.estimateCostMicros(provider, model, inputTokens, outputTokens)
        ProviderSettings.addCostMicros(context, provider, micros)
    }

    private fun requestAnthropic(
        context: Context,
        config: ProviderSettings.ActiveConfig,
        systemPrompt: String,
        userText: String,
        imageBase64: String?,
    ): String {
        val content = JSONArray()
        if (imageBase64 != null) {
            content.put(JSONObject().apply {
                put("type", "image")
                put("source", JSONObject().apply {
                    put("type", "base64")
                    put("media_type", "image/jpeg")
                    put("data", imageBase64)
                })
            })
        }
        content.put(JSONObject().apply {
            put("type", "text")
            put("text", userText)
        })

        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", 2048)
            put("system", systemPrompt)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", content)
            }))
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ApiException(apiErrorMessage(responseText, response.code))
            }
            val json = JSONObject(responseText)
            json.optJSONObject("usage")?.let { usage ->
                trackCost(
                    context, ProviderSettings.PROVIDER_ANTHROPIC, config.model,
                    inputTokens = usage.optLong("input_tokens", 0L),
                    outputTokens = usage.optLong("output_tokens", 0L),
                )
            }
            if (json.optString("stop_reason") == "refusal") {
                throw ApiException("Die Anfrage wurde vom Modell abgelehnt.")
            }
            val contentArr = json.getJSONArray("content")
            for (i in 0 until contentArr.length()) {
                val block = contentArr.getJSONObject(i)
                if (block.getString("type") == "text") {
                    return block.getString("text").trim()
                }
            }
            throw ApiException("Leere Antwort vom Modell.")
        }
    }

    private fun requestOpenAi(
        context: Context,
        config: ProviderSettings.ActiveConfig,
        systemPrompt: String,
        userText: String,
        imageBase64: String?,
    ): String {
        val content = JSONArray()
        content.put(JSONObject().apply {
            put("type", "text")
            put("text", userText)
        })
        if (imageBase64 != null) {
            content.put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$imageBase64")
                })
            })
        }

        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", 2048)
            put("messages", JSONArray()
                .put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                .put(JSONObject().apply {
                    put("role", "user")
                    put("content", content)
                })
            )
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ApiException(apiErrorMessage(responseText, response.code))
            }
            val json = JSONObject(responseText)
            json.optJSONObject("usage")?.let { usage ->
                trackCost(
                    context, ProviderSettings.PROVIDER_OPENAI, config.model,
                    inputTokens = usage.optLong("prompt_tokens", 0L),
                    outputTokens = usage.optLong("completion_tokens", 0L),
                )
            }
            return json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
        }
    }

    private fun apiErrorMessage(responseText: String, code: Int): String {
        val detail = runCatching {
            JSONObject(responseText).getJSONObject("error").getString("message")
        }.getOrNull()
        return when {
            detail != null -> "API-Fehler ($code): $detail"
            code == 401 -> "API-Key ungültig (401)."
            code == 429 -> "Rate-Limit erreicht (429) — bitte kurz warten."
            else -> "API-Fehler ($code)."
        }
    }

    /** Zieht das JSON-Objekt aus der Modellantwort (toleriert ```-Zäune und Fließtext). */
    fun extractJson(text: String): JSONObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw ApiException("Antwort nicht lesbar: $text")
        return JSONObject(text.substring(start, end + 1))
    }
}
