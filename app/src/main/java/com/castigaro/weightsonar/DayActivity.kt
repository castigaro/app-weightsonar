package com.castigaro.weightsonar

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.castigaro.weightsonar.analysis.NutritionCalc
import com.castigaro.weightsonar.data.ActivityEntry
import com.castigaro.weightsonar.data.DayStore
import com.castigaro.weightsonar.data.FoodEntry
import com.castigaro.weightsonar.data.Person
import com.castigaro.weightsonar.data.PersonStore
import com.castigaro.weightsonar.databinding.ActivityDayBinding
import com.castigaro.weightsonar.databinding.DialogWeightBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Der Tages-Hub: Bilanz mit Ampel, Lebensmittel, Aktivitäten und Gewicht
 * eines Tages. Ohne EXTRA_DATE zeigt er immer den heutigen Tag (das Datum
 * wird bei onResume neu bestimmt — über Mitternacht hinweg springt die
 * Anzeige also von selbst auf den neuen, leeren Tag); mit EXTRA_DATE dient
 * er als Editor für die Vergangenheit.
 */
class DayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDayBinding
    private lateinit var person: Person
    private lateinit var foodAdapter: TwoLineAdapter<FoodEntry>
    private lateinit var activityAdapter: TwoLineAdapter<ActivityEntry>
    private var explicitDate: LocalDate? = null
    private var date: LocalDate = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val loaded = PersonStore.get(this, intent.getStringExtra(EXTRA_PERSON_ID).orEmpty())
        if (loaded == null) {
            finish()
            return
        }
        person = loaded
        explicitDate = intent.getStringExtra(EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        foodAdapter = TwoLineAdapter(
            onClick = { food -> editFood(food) },
            onLongClick = { food -> confirmDeleteFood(food) },
            describe = { food ->
                food.name to getString(
                    R.string.food_subtitle,
                    NutritionCalc.formatAmount(food.grams),
                    NutritionCalc.formatKcal(food.contribution().energyKcal),
                )
            },
        )
        binding.foodList.layoutManager = LinearLayoutManager(this)
        binding.foodList.adapter = foodAdapter

        activityAdapter = TwoLineAdapter(
            onClick = { activity -> editActivity(activity) },
            onLongClick = { activity -> confirmDeleteActivity(activity) },
            describe = { activity ->
                activity.name to getString(
                    R.string.activity_subtitle,
                    activity.minutes.toString(),
                    NutritionCalc.formatKcal(activity.kcal()),
                )
            },
        )
        binding.activityList.layoutManager = LinearLayoutManager(this)
        binding.activityList.adapter = activityAdapter

        binding.headerCard.setOnClickListener {
            Dialogs.showPerson(this, person) { refresh() }
        }
        binding.weightLine.setOnClickListener { showWeightDialog() }
        binding.buttonAddFood.setOnClickListener {
            startActivity(
                Intent(this, CaptureActivity::class.java)
                    .putExtra(CaptureActivity.EXTRA_PERSON_ID, person.id)
                    .putExtra(CaptureActivity.EXTRA_DATE, date.toString())
            )
        }
        binding.buttonAddActivity.setOnClickListener { addActivity() }
    }

    override fun onResume() {
        super.onResume()
        date = explicitDate ?: LocalDate.now()
        refresh()
    }

    private fun refresh() {
        val record = DayStore.get(this, person.id, date)
        val weight = DayStore.latestWeight(this, person.id, date)
        val summary = NutritionCalc.summarize(person, record, weight, date)

        supportActionBar?.title = person.name
        val dateText = date.format(
            DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", Locale.GERMAN))
        supportActionBar?.subtitle =
            if (explicitDate == null) "$dateText (${getString(R.string.today_suffix)})" else dateText

        // Kopf: Alter, Größe, BMI, Grundumsatz.
        val headParts = mutableListOf<String>()
        person.birthLocalDate()?.let {
            headParts.add(getString(R.string.age_years, NutritionCalc.ageYears(it, date)))
        }
        headParts.add(getString(R.string.height_cm, NutritionCalc.formatAmount(person.heightCm)))
        if (weight != null) {
            headParts.add(getString(R.string.weight_kg, NutritionCalc.formatAmount(weight)))
            headParts.add(getString(
                R.string.bmi_format,
                NutritionCalc.formatAmount(NutritionCalc.bmi(weight, person.heightCm))))
        }
        summary.bmr?.let {
            headParts.add(getString(R.string.bmr_format, NutritionCalc.formatKcal(it)))
        }
        if (weight == null) headParts.add(getString(R.string.no_weight_yet))
        binding.personDetails.text = headParts.joinToString(" · ")

        // Bilanz.
        binding.intakeLine.text =
            getString(R.string.intake_line, NutritionCalc.formatKcal(summary.intake.energyKcal))
        if (summary.budget != null && summary.bmr != null) {
            binding.budgetLine.text = getString(
                R.string.budget_line,
                NutritionCalc.formatKcal(summary.budget),
                NutritionCalc.formatKcal(summary.bmr),
                NutritionCalc.formatKcal(summary.activityKcal),
            )
        } else {
            binding.budgetLine.text = getString(R.string.budget_unknown)
        }
        if (summary.saldo != null) {
            binding.saldoLine.visibility = View.VISIBLE
            binding.saldoLine.text = getString(
                R.string.saldo_line,
                NutritionCalc.formatSignedKcal(summary.saldo),
                getString(ampelLabel(summary.ampel)),
            )
            binding.saldoLine.setTextColor(ContextCompat.getColor(this, ampelColor(summary.ampel)))
        } else {
            binding.saldoLine.visibility = View.GONE
        }
        binding.macrosLine.text = getString(
            R.string.macros_line,
            NutritionCalc.formatAmount(summary.intake.fat),
            NutritionCalc.formatAmount(summary.intake.carbs),
            NutritionCalc.formatAmount(summary.intake.protein),
        )

        binding.weightLine.text = record?.weightKg?.let {
            getString(R.string.weight_line, NutritionCalc.formatAmount(it))
        } ?: getString(R.string.weight_missing)

        val foods = record?.foods ?: emptyList()
        foodAdapter.submit(foods)
        binding.foodsEmpty.visibility = if (foods.isEmpty()) View.VISIBLE else View.GONE

        val activities = record?.activities ?: emptyList()
        activityAdapter.submit(activities)
        binding.activitiesEmpty.visibility =
            if (activities.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun ampelLabel(ampel: NutritionCalc.Ampel): Int = when (ampel) {
        NutritionCalc.Ampel.GREEN -> R.string.ampel_green_label
        NutritionCalc.Ampel.YELLOW -> R.string.ampel_yellow_label
        NutritionCalc.Ampel.RED -> R.string.ampel_red_label
        NutritionCalc.Ampel.NONE -> R.string.ampel_none_label
    }

    private fun ampelColor(ampel: NutritionCalc.Ampel): Int = when (ampel) {
        NutritionCalc.Ampel.GREEN -> R.color.ampel_green
        NutritionCalc.Ampel.YELLOW -> R.color.ampel_yellow
        NutritionCalc.Ampel.RED -> R.color.ampel_red
        NutritionCalc.Ampel.NONE -> R.color.ampel_none
    }

    // ---- Lebensmittel ----

    private fun editFood(food: FoodEntry) {
        Dialogs.showFood(
            this, R.string.edit_food_title, food.name, food.grams, food.per100g,
        ) { name, grams, per100g ->
            food.name = name
            food.grams = grams
            food.per100g.copyFrom(per100g)
            DayStore.save(this, person.id)
            refresh()
        }
    }

    private fun confirmDeleteFood(food: FoodEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_entry)
            .setMessage(getString(R.string.delete_entry_message, food.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                DayStore.get(this, person.id, date)?.let {
                    DayStore.deleteFood(this, person.id, it, food)
                }
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Aktivitäten ----

    private fun addActivity() {
        Dialogs.showActivity(
            this, R.string.book_activity_title, "", null, null,
            weightKg = DayStore.latestWeight(this, person.id, date),
        ) { name, minutes, kcalPerHour ->
            val record = DayStore.getOrCreate(this, person.id, date)
            record.activities.add(
                ActivityEntry(name = name, minutes = minutes, kcalPerHour = kcalPerHour))
            DayStore.save(this, person.id)
            refresh()
        }
    }

    private fun editActivity(activity: ActivityEntry) {
        Dialogs.showActivity(
            this, R.string.edit_activity_title,
            activity.name, activity.minutes, activity.kcalPerHour,
            weightKg = DayStore.latestWeight(this, person.id, date),
        ) { name, minutes, kcalPerHour ->
            activity.name = name
            activity.minutes = minutes
            activity.kcalPerHour = kcalPerHour
            DayStore.save(this, person.id)
            refresh()
        }
    }

    private fun confirmDeleteActivity(activity: ActivityEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_entry)
            .setMessage(getString(R.string.delete_entry_message, activity.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                DayStore.get(this, person.id, date)?.let { record ->
                    record.activities.removeAll { it.id == activity.id }
                    DayStore.save(this, person.id)
                }
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Gewicht ----

    private fun showWeightDialog() {
        val dialogBinding = DialogWeightBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.weight_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val weight = dialogBinding.inputWeight.text.toString().trim()
                    .replace(',', '.').toDoubleOrNull()
                val record = DayStore.getOrCreate(this, person.id, date)
                record.weightKg = if (weight != null && weight > 0) weight else null
                DayStore.save(this, person.id)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            DayStore.get(this, person.id, date)?.weightKg?.let {
                dialogBinding.inputWeight.setText(Dialogs.editText(it))
            }
        }
        dialog.show()
    }

    // ---- Menü ----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_day, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_calendar -> {
            startActivity(
                Intent(this, CalendarActivity::class.java)
                    .putExtra(CalendarActivity.EXTRA_PERSON_ID, person.id)
            )
            true
        }
        R.id.action_stats -> {
            startActivity(
                Intent(this, StatsActivity::class.java)
                    .putExtra(StatsActivity.EXTRA_PERSON_ID, person.id)
            )
            true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_PERSON_ID = "personId"
        const val EXTRA_DATE = "date"
    }
}
