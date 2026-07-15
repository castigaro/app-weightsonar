package com.castigaro.weightsonar.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Nährwertsatz je 100 g nach EU-Nährwertdeklaration; Energie in kcal, Rest in g. */
class Nutrients(
    var energyKcal: Double = 0.0,
    var fat: Double = 0.0,
    var saturatedFat: Double = 0.0,
    var carbs: Double = 0.0,
    var sugar: Double = 0.0,
    var protein: Double = 0.0,
    var salt: Double = 0.0,
    var fiber: Double = 0.0,
) {
    fun scaled(factor: Double) = Nutrients(
        energyKcal = energyKcal * factor,
        fat = fat * factor,
        saturatedFat = saturatedFat * factor,
        carbs = carbs * factor,
        sugar = sugar * factor,
        protein = protein * factor,
        salt = salt * factor,
        fiber = fiber * factor,
    )

    fun copyFrom(other: Nutrients) {
        energyKcal = other.energyKcal
        fat = other.fat
        saturatedFat = other.saturatedFat
        carbs = other.carbs
        sugar = other.sugar
        protein = other.protein
        salt = other.salt
        fiber = other.fiber
    }

    fun add(other: Nutrients) {
        energyKcal += other.energyKcal
        fat += other.fat
        saturatedFat += other.saturatedFat
        carbs += other.carbs
        sugar += other.sugar
        protein += other.protein
        salt += other.salt
        fiber += other.fiber
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("energyKcal", energyKcal)
        put("fat", fat)
        put("saturatedFat", saturatedFat)
        put("carbs", carbs)
        put("sugar", sugar)
        put("protein", protein)
        put("salt", salt)
        put("fiber", fiber)
    }

    companion object {
        fun fromJson(json: JSONObject) = Nutrients(
            energyKcal = json.optDouble("energyKcal", 0.0),
            fat = json.optDouble("fat", 0.0),
            saturatedFat = json.optDouble("saturatedFat", 0.0),
            carbs = json.optDouble("carbs", 0.0),
            sugar = json.optDouble("sugar", 0.0),
            protein = json.optDouble("protein", 0.0),
            salt = json.optDouble("salt", 0.0),
            fiber = json.optDouble("fiber", 0.0),
        )
    }
}

/** Ein gegessenes Lebensmittel eines Tages; Nährwerte pro 100 g, Menge in g. */
class FoodEntry(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var grams: Double,
    val per100g: Nutrients,
    var source: String = SOURCE_MANUAL,
    var photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Beitrag des Eintrags zum Tag = Nährwerte pro 100 g × Menge/100. */
    fun contribution(): Nutrients = per100g.scaled(grams / 100.0)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("grams", grams)
        put("per100g", per100g.toJson())
        put("source", source)
        put("photoPath", photoPath ?: JSONObject.NULL)
        put("createdAt", createdAt)
    }

    companion object {
        const val SOURCE_MEAL = "meal"
        const val SOURCE_LABEL = "label"
        const val SOURCE_MANUAL = "manual"

        fun fromJson(json: JSONObject) = FoodEntry(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name"),
            grams = json.optDouble("grams", 0.0),
            per100g = json.optJSONObject("per100g")?.let { Nutrients.fromJson(it) } ?: Nutrients(),
            source = json.optString("source", SOURCE_MANUAL),
            photoPath = json.optString("photoPath").ifBlank { null }
                .takeIf { json.opt("photoPath") != JSONObject.NULL },
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

/** Eine gebuchte Aktivität eines Tages; verbrannte kcal = kcal/h × Minuten/60. */
class ActivityEntry(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var minutes: Int,
    var kcalPerHour: Double,
) {
    fun kcal(): Double = kcalPerHour * minutes / 60.0

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("minutes", minutes)
        put("kcalPerHour", kcalPerHour)
    }

    companion object {
        fun fromJson(json: JSONObject) = ActivityEntry(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name"),
            minutes = json.optInt("minutes", 0),
            kcalPerHour = json.optDouble("kcalPerHour", 0.0),
        )
    }
}

/** Alles Erfasste eines Tages einer Person; Datum als yyyy-MM-dd (lokal). */
class DayRecord(
    val personId: String,
    val date: String,
    val foods: MutableList<FoodEntry> = mutableListOf(),
    val activities: MutableList<ActivityEntry> = mutableListOf(),
    var weightKg: Double? = null,
) {
    fun isEmpty(): Boolean = foods.isEmpty() && activities.isEmpty() && weightKg == null

    fun toJson(): JSONObject = JSONObject().apply {
        put("personId", personId)
        put("date", date)
        put("foods", JSONArray().also { arr -> foods.forEach { arr.put(it.toJson()) } })
        put("activities", JSONArray().also { arr -> activities.forEach { arr.put(it.toJson()) } })
        put("weightKg", weightKg ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): DayRecord {
            val record = DayRecord(
                personId = json.optString("personId"),
                date = json.optString("date"),
                weightKg = if (json.isNull("weightKg")) null else json.optDouble("weightKg"),
            )
            val foods = json.optJSONArray("foods") ?: JSONArray()
            for (i in 0 until foods.length()) {
                record.foods.add(FoodEntry.fromJson(foods.getJSONObject(i)))
            }
            val activities = json.optJSONArray("activities") ?: JSONArray()
            for (i in 0 until activities.length()) {
                record.activities.add(ActivityEntry.fromJson(activities.getJSONObject(i)))
            }
            return record
        }
    }
}

/** Ein Personen-Profil. Das aktuelle Gewicht kommt aus dem jüngsten Tagesgewicht. */
class Person(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var sex: String = SEX_MALE,
    var birthDate: String, // yyyy-MM-dd
    var heightCm: Double,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun birthLocalDate(): LocalDate? = runCatching { LocalDate.parse(birthDate) }.getOrNull()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("sex", sex)
        put("birthDate", birthDate)
        put("heightCm", heightCm)
        put("createdAt", createdAt)
    }

    companion object {
        const val SEX_MALE = "m"
        const val SEX_FEMALE = "w"

        fun fromJson(json: JSONObject) = Person(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name"),
            sex = json.optString("sex", SEX_MALE),
            birthDate = json.optString("birthDate"),
            heightCm = json.optDouble("heightCm", 0.0),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

/** Eine Aktivität des Katalogs (vorbelegt oder vom Nutzer angelegt). */
class CatalogEntry(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var kcalPerHour: Double,
    val custom: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("kcalPerHour", kcalPerHour)
        put("custom", custom)
    }

    companion object {
        fun fromJson(json: JSONObject) = CatalogEntry(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name"),
            kcalPerHour = json.optDouble("kcalPerHour", 0.0),
            custom = json.optBoolean("custom", false),
        )
    }
}
