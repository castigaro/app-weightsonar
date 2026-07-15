package com.castigaro.weightsonar

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.castigaro.common.llm.ProviderSettings
import com.castigaro.common.llm.ProviderSettingsController
import com.castigaro.weightsonar.analysis.NutritionCalc
import com.castigaro.weightsonar.api.ActivityEstimateApi
import com.castigaro.weightsonar.data.ActivityCatalogStore
import com.castigaro.weightsonar.data.CatalogEntry
import com.castigaro.weightsonar.databinding.ActivitySettingsBinding
import com.castigaro.weightsonar.databinding.DialogCatalogBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Einstellungen: KI-Anbindung aus der gemeinsamen Bibliothek
 * (provider_settings.xml + ProviderSettingsController) plus der
 * Aktivitäten-Katalog (Richtwerte ändern, eigene Einträge anlegen/löschen,
 * auf Wunsch mit KI-Schätzung).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var catalogAdapter: TwoLineAdapter<CatalogEntry>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ProviderSettingsController(this, binding.providerSettings)

        catalogAdapter = TwoLineAdapter(
            onClick = { entry -> showCatalogDialog(entry) },
            onLongClick = { entry -> confirmDeleteCatalog(entry) },
            describe = { entry ->
                entry.name to getString(
                    R.string.catalog_kcal_format,
                    NutritionCalc.formatAmount(entry.kcalPerHour),
                )
            },
        )
        binding.catalogList.layoutManager = LinearLayoutManager(this)
        binding.catalogList.adapter = catalogAdapter
        binding.buttonAddCatalog.setOnClickListener { showCatalogDialog(null) }

        refreshCatalog()
    }

    private fun refreshCatalog() {
        catalogAdapter.submit(ActivityCatalogStore.getAll(this))
    }

    /** Anlegen (entry == null) oder Bearbeiten eines Katalog-Eintrags. */
    private fun showCatalogDialog(entry: CatalogEntry?) {
        val dialogBinding = DialogCatalogBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (entry == null) R.string.add_catalog_entry else R.string.edit_catalog_entry)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null) // Handler unten: validiert, ohne zu schließen
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            entry?.let {
                dialogBinding.inputCatalogName.setText(it.name)
                dialogBinding.inputCatalogKcal.setText(Dialogs.editText(it.kcalPerHour))
            }

            dialogBinding.buttonEstimate.setOnClickListener {
                val description = dialogBinding.inputCatalogName.text.toString().trim()
                if (description.isEmpty()) {
                    dialogBinding.estimateStatus.text = getString(R.string.catalog_invalid)
                    return@setOnClickListener
                }
                if (!ProviderSettings.isConfigured(this)) {
                    dialogBinding.estimateStatus.text = getString(R.string.needs_key)
                    return@setOnClickListener
                }
                dialogBinding.estimateProgress.visibility = View.VISIBLE
                dialogBinding.estimateStatus.text = getString(R.string.estimating)
                lifecycleScope.launch {
                    try {
                        val estimated = ActivityEstimateApi.estimate(
                            this@SettingsActivity, description, weightKg = null)
                        dialogBinding.inputCatalogKcal.setText(Dialogs.editText(estimated))
                        dialogBinding.estimateStatus.text = ""
                    } catch (e: Exception) {
                        dialogBinding.estimateStatus.text =
                            getString(R.string.estimate_error, e.message ?: "?")
                    } finally {
                        dialogBinding.estimateProgress.visibility = View.GONE
                    }
                }
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogBinding.inputCatalogName.text.toString().trim()
                val kcal = dialogBinding.inputCatalogKcal.text.toString().trim()
                    .replace(',', '.').toDoubleOrNull()
                if (name.isEmpty() || kcal == null || kcal <= 0) {
                    dialogBinding.estimateStatus.text = getString(R.string.catalog_invalid)
                    return@setOnClickListener
                }
                if (entry == null) {
                    ActivityCatalogStore.add(
                        this, CatalogEntry(name = name, kcalPerHour = kcal, custom = true))
                } else {
                    entry.name = name
                    entry.kcalPerHour = kcal
                    ActivityCatalogStore.save(this)
                }
                dialog.dismiss()
                refreshCatalog()
            }
        }
        dialog.show()
    }

    private fun confirmDeleteCatalog(entry: CatalogEntry) {
        if (!entry.custom) {
            Snackbar.make(binding.root, R.string.catalog_default_undeletable, Snackbar.LENGTH_LONG)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_entry)
            .setMessage(getString(R.string.delete_entry_message, entry.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                ActivityCatalogStore.delete(this, entry)
                refreshCatalog()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
