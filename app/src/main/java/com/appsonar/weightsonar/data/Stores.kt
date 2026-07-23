package com.appsonar.weightsonar.data

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.time.LocalDate

/** JSON-Datei-Speicher für Profile — gleiche Bauart wie in den anderen Apps. */
object PersonStore {

    private var persons: MutableList<Person>? = null

    private fun file(context: Context): File = File(context.filesDir, "persons.json")

    private fun load(context: Context): MutableList<Person> {
        persons?.let { return it }
        val loaded = mutableListOf<Person>()
        val f = file(context)
        if (f.exists()) {
            runCatching {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) loaded.add(Person.fromJson(arr.getJSONObject(i)))
            }
        }
        persons = loaded
        return loaded
    }

    fun save(context: Context) {
        val arr = JSONArray()
        load(context).forEach { arr.put(it.toJson()) }
        file(context).writeText(arr.toString())
    }

    fun getAll(context: Context): List<Person> =
        load(context).sortedBy { it.name.lowercase() }

    fun get(context: Context, id: String): Person? =
        load(context).firstOrNull { it.id == id }

    fun add(context: Context, person: Person) {
        load(context).add(person)
        save(context)
    }

    fun delete(context: Context, person: Person) {
        load(context).removeAll { it.id == person.id }
        save(context)
        DayStore.deleteAllOf(context, person.id)
    }
}

/**
 * JSON-Datei-Speicher für Tage: eine Datei je Person (days/<personId>.json),
 * darin ein Array von DayRecords. Ein Tag entsteht lazy beim ersten Eintrag;
 * wieder geleerte Tage werden beim Speichern verworfen.
 */
object DayStore {

    private val cache = mutableMapOf<String, MutableList<DayRecord>>()

    private fun dir(context: Context): File =
        File(context.filesDir, "days").apply { mkdirs() }

    private fun file(context: Context, personId: String): File =
        File(dir(context), "$personId.json")

    private fun load(context: Context, personId: String): MutableList<DayRecord> {
        cache[personId]?.let { return it }
        val loaded = mutableListOf<DayRecord>()
        val f = file(context, personId)
        if (f.exists()) {
            runCatching {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) loaded.add(DayRecord.fromJson(arr.getJSONObject(i)))
            }
        }
        cache[personId] = loaded
        return loaded
    }

    fun save(context: Context, personId: String) {
        val records = load(context, personId)
        records.removeAll { it.isEmpty() }
        val arr = JSONArray()
        records.forEach { arr.put(it.toJson()) }
        file(context, personId).writeText(arr.toString())
    }

    fun get(context: Context, personId: String, date: LocalDate): DayRecord? =
        load(context, personId).firstOrNull { it.date == date.toString() }

    fun getOrCreate(context: Context, personId: String, date: LocalDate): DayRecord =
        get(context, personId, date)
            ?: DayRecord(personId, date.toString()).also { load(context, personId).add(it) }

    /** Alle Tage der Person, aufsteigend nach Datum (ISO-Datum sortiert lexikographisch). */
    fun all(context: Context, personId: String): List<DayRecord> =
        load(context, personId).sortedBy { it.date }

    /** Jüngstes erfasstes Gewicht bis einschließlich [onOrBefore] — sonst null. */
    fun latestWeight(context: Context, personId: String, onOrBefore: LocalDate): Double? =
        load(context, personId)
            .filter { it.weightKg != null && it.date <= onOrBefore.toString() }
            .maxByOrNull { it.date }
            ?.weightKg

    /** Löscht einen Essens-Eintrag; das Foto fällt mit, wenn es niemand mehr nutzt. */
    fun deleteFood(context: Context, personId: String, record: DayRecord, food: FoodEntry) {
        record.foods.removeAll { it.id == food.id }
        save(context, personId)
        val path = food.photoPath ?: return
        val stillUsed = load(context, personId).any { day -> day.foods.any { it.photoPath == path } }
        if (!stillUsed) runCatching { File(path).delete() }
    }

    /** Kaskade beim Löschen einer Person: alle Tage samt Fotos entfernen. */
    fun deleteAllOf(context: Context, personId: String) {
        val records = load(context, personId)
        records.flatMap { it.foods }.mapNotNull { it.photoPath }.distinct().forEach { path ->
            runCatching { File(path).delete() }
        }
        cache.remove(personId)
        file(context, personId).delete()
    }
}

/**
 * Katalog der buchbaren Aktivitäten: vorbelegte Richtwerte (MET-basiert,
 * ~75 kg), pro Eintrag editierbar, plus freie Custom-Einträge.
 */
object ActivityCatalogStore {

    private var entries: MutableList<CatalogEntry>? = null

    private fun file(context: Context): File = File(context.filesDir, "catalog.json")

    private fun defaults(): MutableList<CatalogEntry> = mutableListOf(
        CatalogEntry(name = "Spazieren", kcalPerHour = 200.0),
        CatalogEntry(name = "Gehen (zügig)", kcalPerHour = 300.0),
        CatalogEntry(name = "Radfahren", kcalPerHour = 450.0),
        CatalogEntry(name = "Schwimmen", kcalPerHour = 500.0),
        CatalogEntry(name = "Joggen", kcalPerHour = 650.0),
        CatalogEntry(name = "Krafttraining", kcalPerHour = 350.0),
        CatalogEntry(name = "Wandern", kcalPerHour = 380.0),
        CatalogEntry(name = "Hausarbeit", kcalPerHour = 250.0),
        CatalogEntry(name = "Gartenarbeit", kcalPerHour = 330.0),
        CatalogEntry(name = "Arbeit am PC (sitzend)", kcalPerHour = 110.0),
        CatalogEntry(name = "Körperliche Arbeit", kcalPerHour = 400.0),
    )

    private fun load(context: Context): MutableList<CatalogEntry> {
        entries?.let { return it }
        var loaded: MutableList<CatalogEntry>? = null
        val f = file(context)
        if (f.exists()) {
            runCatching {
                val arr = JSONArray(f.readText())
                val list = mutableListOf<CatalogEntry>()
                for (i in 0 until arr.length()) list.add(CatalogEntry.fromJson(arr.getJSONObject(i)))
                loaded = list
            }
        }
        val result = loaded ?: defaults().also { seeded ->
            entries = seeded
            save(context)
        }
        entries = result
        return result
    }

    fun save(context: Context) {
        val arr = JSONArray()
        load(context).forEach { arr.put(it.toJson()) }
        file(context).writeText(arr.toString())
    }

    fun getAll(context: Context): List<CatalogEntry> =
        load(context).sortedBy { it.name.lowercase() }

    fun add(context: Context, entry: CatalogEntry) {
        load(context).add(entry)
        save(context)
    }

    fun delete(context: Context, entry: CatalogEntry) {
        load(context).removeAll { it.id == entry.id }
        save(context)
    }
}
