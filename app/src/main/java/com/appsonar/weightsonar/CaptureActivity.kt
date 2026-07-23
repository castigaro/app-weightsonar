package com.appsonar.weightsonar

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.appsonar.common.llm.ProviderSettings
import com.appsonar.weightsonar.analysis.NutritionCalc
import com.appsonar.weightsonar.api.FoodApi
import com.appsonar.weightsonar.data.DayStore
import com.appsonar.weightsonar.data.FoodEntry
import com.appsonar.weightsonar.data.Nutrients
import com.appsonar.weightsonar.databinding.ActivityCaptureBinding
import com.appsonar.weightsonar.util.Photos
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/**
 * Essen erfassen: Foto aufnehmen oder wählen, Modus Gericht (KI erkennt
 * Positionen samt Menge) oder Nährwerttabelle (KI liest die Tabelle ab,
 * die Menge trägt der Nutzer ein). Alles bleibt editierbar; manuelles
 * Erfassen geht auch ganz ohne Foto und ohne API-Key.
 */
class CaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaptureBinding
    private lateinit var adapter: TwoLineAdapter<FoodApi.Item>
    private lateinit var personId: String
    private lateinit var date: LocalDate

    private var photo: File? = null
    private var pendingPhoto: File? = null
    private var analyzing = false
    private var saved = false

    /** Vom Foto erkannte Positionen (werden bei erneuter Analyse ersetzt) … */
    private val autoItems = mutableListOf<FoodApi.Item>()
    private var autoSource = FoodEntry.SOURCE_MEAL

    /** … und manuell ergänzte (bleiben bei erneuter Analyse erhalten). */
    private val manualItems = mutableListOf<FoodApi.Item>()

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingPhoto
        pendingPhoto = null
        if (success && file != null && file.exists()) {
            usePhoto(file)
        } else {
            file?.delete()
        }
    }

    private val pickPhoto = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val file = Photos.newPhotoFile(this)
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: error("Stream ist null")
        }.onSuccess {
            usePhoto(file)
        }.onFailure {
            file.delete()
            Snackbar.make(
                binding.root,
                getString(R.string.analyze_error, it.message ?: "?"),
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        personId = intent.getStringExtra(EXTRA_PERSON_ID).orEmpty()
        date = intent.getStringExtra(EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        if (personId.isEmpty()) {
            finish()
            return
        }

        adapter = TwoLineAdapter(
            onClick = { item -> editItem(item) },
            onLongClick = { item ->
                autoItems.remove(item)
                manualItems.remove(item)
                refreshItems()
            },
            describe = { item ->
                val title = if (item.uncertain) {
                    "${item.name} · ${getString(R.string.uncertain_check)}"
                } else {
                    item.name
                }
                title to getString(
                    R.string.food_subtitle,
                    NutritionCalc.formatAmount(item.grams),
                    NutritionCalc.formatKcal(item.per100g.energyKcal * item.grams / 100.0),
                )
            },
        )
        binding.itemList.layoutManager = LinearLayoutManager(this)
        binding.itemList.adapter = adapter

        binding.modeGroup.check(R.id.modeMeal)
        binding.modeGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            binding.modeHint.setText(
                if (isLabelMode()) R.string.capture_hint_label else R.string.capture_hint_meal)
            // Moduswechsel bei vorhandenem Foto: gleich mit dem neuen Modus analysieren.
            if (photo != null && !analyzing) analyze()
        }

        binding.buttonTakePhoto.setOnClickListener {
            if (!requireKey()) return@setOnClickListener
            val file = Photos.newPhotoFile(this)
            pendingPhoto = file
            takePicture.launch(Photos.uriFor(this, file))
        }
        binding.buttonPickPhoto.setOnClickListener {
            if (!requireKey()) return@setOnClickListener
            pickPhoto.launch("image/*")
        }
        binding.buttonAddManual.setOnClickListener {
            Dialogs.showFood(
                this, R.string.add_food_title, "", null, Nutrients(),
            ) { name, grams, per100g ->
                manualItems.add(FoodApi.Item(name, grams, per100g, uncertain = false))
                refreshItems()
            }
        }
        binding.buttonRetry.setOnClickListener { analyze() }
        binding.buttonSave.setOnClickListener { save() }
    }

    private fun isLabelMode(): Boolean = binding.modeGroup.checkedButtonId == R.id.modeLabel

    private fun requireKey(): Boolean {
        if (ProviderSettings.isConfigured(this)) return true
        Snackbar.make(binding.root, R.string.needs_key, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .show()
        return false
    }

    private fun usePhoto(file: File) {
        Photos.downscale(file)
        // Ein früheres, noch ungespeichertes Foto wird ersetzt und gelöscht.
        photo?.takeIf { it != file }?.delete()
        photo = file
        binding.photoPreview.visibility = View.VISIBLE
        binding.photoPreview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        analyze()
    }

    private fun analyze() {
        val file = photo ?: return
        analyzing = true
        autoSource = if (isLabelMode()) FoodEntry.SOURCE_LABEL else FoodEntry.SOURCE_MEAL
        binding.progress.visibility = View.VISIBLE
        binding.statusText.setText(
            if (isLabelMode()) R.string.reading_label else R.string.analyzing_meal)
        binding.buttonRetry.visibility = View.GONE
        updateSaveEnabled()
        lifecycleScope.launch {
            try {
                val results = if (isLabelMode()) {
                    listOf(FoodApi.readLabel(this@CaptureActivity, file))
                } else {
                    FoodApi.analyzeMeal(this@CaptureActivity, file)
                }
                autoItems.clear()
                autoItems.addAll(results)
                refreshItems()
                val uncertain = autoItems.count { it.uncertain }
                binding.statusText.text = if (uncertain > 0) {
                    resources.getQuantityString(
                        R.plurals.capture_uncertain, autoItems.size, autoItems.size, uncertain)
                } else {
                    resources.getQuantityString(
                        R.plurals.capture_ok, autoItems.size, autoItems.size)
                }
                if (autoItems.isEmpty()) {
                    binding.statusText.setText(R.string.no_items)
                    binding.buttonRetry.visibility = View.VISIBLE
                } else if (isLabelMode()) {
                    // Die gegessene Menge fehlt noch — direkt zum Eintragen öffnen.
                    editItem(autoItems.first())
                }
            } catch (e: Exception) {
                binding.statusText.text = getString(R.string.analyze_error, e.message ?: "?")
                binding.buttonRetry.visibility = View.VISIBLE
            } finally {
                analyzing = false
                binding.progress.visibility = View.GONE
                updateSaveEnabled()
            }
        }
    }

    private fun editItem(item: FoodApi.Item) {
        Dialogs.showFood(
            this, R.string.edit_food_title, item.name,
            item.grams.takeIf { it > 0 }, item.per100g,
        ) { name, grams, per100g ->
            item.name = name
            item.grams = grams
            item.per100g.copyFrom(per100g)
            item.uncertain = false // vom Nutzer geprüft
            refreshItems()
        }
    }

    private fun allItems(): List<FoodApi.Item> = autoItems + manualItems

    private fun refreshItems() {
        adapter.submit(allItems())
        updateSaveEnabled()
    }

    private fun updateSaveEnabled() {
        binding.buttonSave.isEnabled = !analyzing && allItems().isNotEmpty()
    }

    private fun save() {
        if (analyzing) return
        if (allItems().isEmpty()) {
            Snackbar.make(binding.root, R.string.no_items, Snackbar.LENGTH_LONG).show()
            return
        }
        val record = DayStore.getOrCreate(this, personId, date)
        autoItems.forEach { item ->
            record.foods.add(
                FoodEntry(
                    name = item.name,
                    grams = item.grams,
                    per100g = item.per100g,
                    source = autoSource,
                    photoPath = photo?.absolutePath,
                )
            )
        }
        manualItems.forEach { item ->
            record.foods.add(
                FoodEntry(
                    name = item.name,
                    grams = item.grams,
                    per100g = item.per100g,
                    source = FoodEntry.SOURCE_MANUAL,
                )
            )
        }
        DayStore.save(this, personId)
        saved = true
        // Nur manuell erfasst? Dann referenziert kein Eintrag das Foto.
        if (autoItems.isEmpty()) photo?.delete()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Verworfene Erfassung: das Foto gehört noch niemandem — aufräumen.
        if (!saved) photo?.delete()
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
