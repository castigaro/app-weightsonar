package com.appsonar.weightsonar

import android.app.DatePickerDialog
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.appsonar.common.llm.ProviderSettings
import com.appsonar.weightsonar.api.ActivityEstimateApi
import com.appsonar.weightsonar.data.ActivityCatalogStore
import com.appsonar.weightsonar.data.Nutrients
import com.appsonar.weightsonar.data.Person
import com.appsonar.weightsonar.data.PersonStore
import com.appsonar.weightsonar.databinding.DialogActivityBinding
import com.appsonar.weightsonar.databinding.DialogFoodBinding
import com.appsonar.weightsonar.databinding.DialogPersonBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Gemeinsame AlertDialoge zum Anlegen und Bearbeiten. */
object Dialogs {

    private val BIRTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Zahl fürs Eingabefeld: ganze Zahlen ohne Nachkommastellen, sonst mit Punkt. */
    fun editText(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun parse(text: CharSequence?): Double? =
        text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()

    // ---- Profil ----

    fun showPerson(activity: AppCompatActivity, person: Person?, onSaved: () -> Unit) {
        val binding = DialogPersonBinding.inflate(activity.layoutInflater)
        var birthDate: LocalDate? = person?.birthLocalDate()

        binding.inputBirthDate.setOnClickListener {
            val initial = birthDate ?: LocalDate.of(1980, 1, 1)
            DatePickerDialog(
                activity,
                { _, year, month, day ->
                    birthDate = LocalDate.of(year, month + 1, day)
                    binding.inputBirthDate.setText(BIRTH_FORMAT.format(birthDate))
                },
                initial.year, initial.monthValue - 1, initial.dayOfMonth,
            ).show()
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (person == null) R.string.add_person_title else R.string.edit_person_title)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null) // Handler unten: validiert, ohne zu schließen
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            binding.sexGroup.check(
                if (person?.sex == Person.SEX_FEMALE) R.id.sexFemale else R.id.sexMale)
            person?.let {
                binding.inputName.setText(it.name)
                birthDate?.let { birth -> binding.inputBirthDate.setText(BIRTH_FORMAT.format(birth)) }
                if (it.heightCm > 0) binding.inputHeight.setText(editText(it.heightCm))
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.inputName.text.toString().trim()
                val height = parse(binding.inputHeight.text)
                val birth = birthDate
                if (name.isEmpty() || birth == null || height == null || height <= 0) {
                    binding.inputName.error =
                        if (name.isEmpty()) activity.getString(R.string.person_incomplete) else null
                    if (birth == null) binding.inputBirthDate.error =
                        activity.getString(R.string.person_incomplete)
                    if (height == null || height <= 0) binding.inputHeight.error =
                        activity.getString(R.string.person_incomplete)
                    return@setOnClickListener
                }
                val sex = if (binding.sexGroup.checkedButtonId == R.id.sexFemale) {
                    Person.SEX_FEMALE
                } else {
                    Person.SEX_MALE
                }
                if (person == null) {
                    PersonStore.add(
                        activity,
                        Person(name = name, sex = sex, birthDate = birth.toString(), heightCm = height),
                    )
                } else {
                    person.name = name
                    person.sex = sex
                    person.birthDate = birth.toString()
                    person.heightCm = height
                    PersonStore.save(activity)
                }
                dialog.dismiss()
                onSaved()
            }
        }
        dialog.show()
    }

    // ---- Lebensmittel ----

    fun showFood(
        activity: AppCompatActivity,
        titleRes: Int,
        name: String,
        grams: Double?,
        per100g: Nutrients,
        onSave: (name: String, grams: Double, per100g: Nutrients) -> Unit,
    ) {
        val binding = DialogFoodBinding.inflate(activity.layoutInflater)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        fun prefill(view: com.google.android.material.textfield.TextInputEditText, value: Double) {
            if (value != 0.0) view.setText(editText(value))
        }

        dialog.setOnShowListener {
            binding.inputFoodName.setText(name)
            grams?.let { if (it > 0) binding.inputGrams.setText(editText(it)) }
            prefill(binding.inputEnergy, per100g.energyKcal)
            prefill(binding.inputFat, per100g.fat)
            prefill(binding.inputSaturated, per100g.saturatedFat)
            prefill(binding.inputCarbs, per100g.carbs)
            prefill(binding.inputSugar, per100g.sugar)
            prefill(binding.inputProtein, per100g.protein)
            prefill(binding.inputSalt, per100g.salt)
            prefill(binding.inputFiber, per100g.fiber)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsedGrams = parse(binding.inputGrams.text)
                if (parsedGrams == null || parsedGrams <= 0) {
                    binding.inputGrams.error = activity.getString(R.string.grams_invalid)
                    return@setOnClickListener
                }
                val nutrients = Nutrients(
                    energyKcal = parse(binding.inputEnergy.text) ?: 0.0,
                    fat = parse(binding.inputFat.text) ?: 0.0,
                    saturatedFat = parse(binding.inputSaturated.text) ?: 0.0,
                    carbs = parse(binding.inputCarbs.text) ?: 0.0,
                    sugar = parse(binding.inputSugar.text) ?: 0.0,
                    protein = parse(binding.inputProtein.text) ?: 0.0,
                    salt = parse(binding.inputSalt.text) ?: 0.0,
                    fiber = parse(binding.inputFiber.text) ?: 0.0,
                )
                val finalName = binding.inputFoodName.text.toString().trim()
                    .ifBlank { activity.getString(R.string.unnamed_food) }
                dialog.dismiss()
                onSave(finalName, parsedGrams, nutrients)
            }
        }
        dialog.show()
    }

    // ---- Aktivität ----

    fun showActivity(
        activity: AppCompatActivity,
        titleRes: Int,
        name: String,
        minutes: Int?,
        kcalPerHour: Double?,
        weightKg: Double?,
        onSave: (name: String, minutes: Int, kcalPerHour: Double) -> Unit,
    ) {
        val binding = DialogActivityBinding.inflate(activity.layoutInflater)
        val catalog = ActivityCatalogStore.getAll(activity)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            binding.inputActivityName.setSimpleItems(catalog.map { it.name }.toTypedArray())
            binding.inputActivityName.setText(name, false)
            // Beim Tippen filtert die Dropdown-Liste — die Position zählt dann in
            // der gefilterten Liste, deshalb über den angezeigten Namen auflösen.
            binding.inputActivityName.setOnItemClickListener { parent, _, position, _ ->
                val selectedName = parent.getItemAtPosition(position) as? String
                catalog.firstOrNull { it.name == selectedName }?.let {
                    binding.inputKcalPerHour.setText(editText(it.kcalPerHour))
                }
            }
            minutes?.let { if (it > 0) binding.inputMinutes.setText(it.toString()) }
            kcalPerHour?.let { if (it > 0) binding.inputKcalPerHour.setText(editText(it)) }

            binding.buttonEstimate.setOnClickListener {
                val description = binding.inputActivityName.text.toString().trim()
                if (description.isEmpty()) {
                    binding.estimateStatus.text = activity.getString(R.string.activity_invalid)
                    return@setOnClickListener
                }
                if (!ProviderSettings.isConfigured(activity)) {
                    binding.estimateStatus.text = activity.getString(R.string.needs_key)
                    return@setOnClickListener
                }
                binding.estimateProgress.visibility = View.VISIBLE
                binding.estimateStatus.text = activity.getString(R.string.estimating)
                activity.lifecycleScope.launch {
                    try {
                        val estimated =
                            ActivityEstimateApi.estimate(activity, description, weightKg)
                        binding.inputKcalPerHour.setText(editText(estimated))
                        binding.estimateStatus.text = ""
                    } catch (e: Exception) {
                        binding.estimateStatus.text =
                            activity.getString(R.string.estimate_error, e.message ?: "?")
                    } finally {
                        binding.estimateProgress.visibility = View.GONE
                    }
                }
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val finalName = binding.inputActivityName.text.toString().trim()
                val finalMinutes = binding.inputMinutes.text.toString().trim().toIntOrNull()
                val finalKcal = parse(binding.inputKcalPerHour.text)
                if (finalName.isEmpty() || finalMinutes == null || finalMinutes <= 0 ||
                    finalKcal == null || finalKcal <= 0
                ) {
                    binding.estimateStatus.text = activity.getString(R.string.activity_invalid)
                    return@setOnClickListener
                }
                dialog.dismiss()
                onSave(finalName, finalMinutes, finalKcal)
            }
        }
        dialog.show()
    }
}
