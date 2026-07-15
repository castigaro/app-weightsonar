package com.castigaro.weightsonar

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.castigaro.common.update.UpdateChecker
import com.castigaro.common.update.UpdateUi
import com.castigaro.weightsonar.analysis.NutritionCalc
import com.castigaro.weightsonar.data.DayStore
import com.castigaro.weightsonar.data.Person
import com.castigaro.weightsonar.data.PersonStore
import com.castigaro.weightsonar.databinding.ActivityMainBinding
import java.time.LocalDate

/** Profil-Liste: Einstieg der App; Tap öffnet den heutigen Tag des Profils. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: TwoLineAdapter<Person>
    private val updateChecker = UpdateChecker(VERSION_URL, BuildConfig.VERSION_CODE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = TwoLineAdapter(
            onClick = { person -> openDay(person) },
            onLongClick = { person -> confirmDelete(person) },
            describe = { person -> person.name to subtitleFor(person) },
        )
        binding.personList.layoutManager = LinearLayoutManager(this)
        binding.personList.adapter = adapter

        binding.fabAddPerson.setOnClickListener {
            Dialogs.showPerson(this, null) { refresh() }
        }

        // Stille Prüfung beim Start — zeigt nur etwas, wenn eine neue Version da ist.
        UpdateUi.checkSilently(this, binding.root, updateChecker)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val persons = PersonStore.getAll(this)
        adapter.submit(persons)
        binding.emptyState.visibility =
            if (persons.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun subtitleFor(person: Person): String {
        val today = LocalDate.now()
        val parts = mutableListOf<String>()
        parts.add(getString(if (person.sex == Person.SEX_FEMALE) R.string.sex_female else R.string.sex_male))
        person.birthLocalDate()?.let {
            parts.add(getString(R.string.age_years, NutritionCalc.ageYears(it, today)))
        }
        parts.add(getString(R.string.height_cm, NutritionCalc.formatAmount(person.heightCm)))
        val weight = DayStore.latestWeight(this, person.id, today)
        if (weight == null) {
            parts.add(getString(R.string.no_weight_yet))
        } else {
            parts.add(getString(R.string.weight_kg, NutritionCalc.formatAmount(weight)))
            parts.add(getString(
                R.string.bmi_format,
                NutritionCalc.formatAmount(NutritionCalc.bmi(weight, person.heightCm))))
        }
        return parts.joinToString(" · ")
    }

    private fun openDay(person: Person) {
        startActivity(
            Intent(this, DayActivity::class.java)
                .putExtra(DayActivity.EXTRA_PERSON_ID, person.id)
        )
    }

    private fun confirmDelete(person: Person) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_person)
            .setMessage(getString(R.string.delete_person_message, person.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                PersonStore.delete(this, person)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_help -> {
            startActivity(Intent(this, HelpActivity::class.java))
            true
        }
        R.id.action_check_update -> {
            UpdateUi.checkManually(this, binding.root, updateChecker)
            true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        private const val VERSION_URL =
            "https://appsonar.de/downloads/weightsonar-version.json"
    }
}
