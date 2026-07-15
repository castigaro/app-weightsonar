package com.castigaro.weightsonar

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.castigaro.weightsonar.analysis.MarkdownBuilder
import com.castigaro.weightsonar.analysis.NutritionCalc
import com.castigaro.weightsonar.analysis.StatsAggregator
import com.castigaro.weightsonar.data.DayStore
import com.castigaro.weightsonar.data.Person
import com.castigaro.weightsonar.data.PersonStore
import com.castigaro.weightsonar.databinding.ActivityStatsBinding
import com.castigaro.weightsonar.databinding.RowTotalBinding
import com.castigaro.weightsonar.ui.BarChartView
import com.castigaro.weightsonar.ui.LineChartView
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Auswertung über einen wählbaren Zeitraum: Kalorien-Balken gegen das
 * Budget, Gewichtskurve, Summen/Durchschnitte — und der Markdown-Export.
 */
class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding
    private lateinit var person: Person
    private var from: LocalDate = LocalDate.now().minusDays(6)
    private var to: LocalDate = LocalDate.now()
    private var result: StatsAggregator.Result? = null

    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val shortFormat = DateTimeFormatter.ofPattern("d.M.")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val loaded = PersonStore.get(this, intent.getStringExtra(EXTRA_PERSON_ID).orEmpty())
        if (loaded == null) {
            finish()
            return
        }
        person = loaded
        supportActionBar?.subtitle = person.name

        binding.rangeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val today = LocalDate.now()
            when (checkedId) {
                R.id.rangeToday -> {
                    from = today
                    to = today
                    render()
                }
                R.id.rangeWeek -> {
                    from = today.minusDays(6)
                    to = today
                    render()
                }
                R.id.rangeMonth -> {
                    from = today.minusDays(29)
                    to = today
                    render()
                }
                R.id.rangeCustom -> pickCustomRange()
            }
        }
        binding.rangeGroup.check(R.id.rangeWeek)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun pickCustomRange() {
        fun picker(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
            DatePickerDialog(
                this,
                { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
                initial.year, initial.monthValue - 1, initial.dayOfMonth,
            ).show()
        }
        picker(from) { pickedFrom ->
            picker(to.coerceAtLeast(pickedFrom)) { pickedTo ->
                from = pickedFrom
                to = if (pickedTo.isBefore(pickedFrom)) pickedFrom else pickedTo
                render()
            }
        }
    }

    private fun render() {
        val records = DayStore.all(this, person.id)
        val aggregated = StatsAggregator.aggregate(person, records, from, to)
        result = aggregated

        binding.rangeLine.text =
            getString(R.string.range_line, dateFormat.format(from), dateFormat.format(to))
        binding.daysLine.text =
            getString(R.string.days_with_data, aggregated.daysWithFood, aggregated.dayCount)
        binding.ampelLine.text = getString(
            R.string.ampel_counts_line,
            aggregated.greenCount, aggregated.yellowCount,
            aggregated.redCount, aggregated.missingCount,
        )
        binding.saldoLine.visibility =
            if (aggregated.saldoTotal != null) View.VISIBLE else View.GONE
        aggregated.saldoTotal?.let {
            binding.saldoLine.text =
                getString(R.string.saldo_total_line, NutritionCalc.formatSignedKcal(it))
        }
        binding.activityLine.text = getString(
            R.string.activity_total_line, NutritionCalc.formatKcal(aggregated.activityKcalTotal))

        val hasFood = aggregated.daysWithFood > 0
        val hasWeight = aggregated.weightSeries.isNotEmpty()
        binding.noDataText.visibility =
            if (!hasFood && !hasWeight) View.VISIBLE else View.GONE

        // Kalorien-Balken je Tag, eingefärbt nach Ampel, Budget als Strich.
        binding.kcalChartTitle.visibility = if (hasFood) View.VISIBLE else View.GONE
        binding.kcalChart.visibility = if (hasFood) View.VISIBLE else View.GONE
        if (hasFood) {
            binding.kcalChart.setBars(aggregated.points.map { point ->
                BarChartView.Bar(
                    label = shortFormat.format(point.date),
                    value = point.intakeKcal,
                    color = ContextCompat.getColor(this, ampelColor(point.ampel)),
                    marker = point.budget,
                )
            })
        }

        binding.weightChartTitle.visibility = if (hasWeight) View.VISIBLE else View.GONE
        binding.weightChart.visibility = if (hasWeight) View.VISIBLE else View.GONE
        if (hasWeight) {
            binding.weightChart.setPoints(aggregated.weightSeries.map { (date, kg) ->
                LineChartView.Point(shortFormat.format(date), kg)
            })
        }

        renderTotals(aggregated)
    }

    private fun ampelColor(ampel: NutritionCalc.Ampel): Int = when (ampel) {
        NutritionCalc.Ampel.GREEN -> R.color.ampel_green
        NutritionCalc.Ampel.YELLOW -> R.color.ampel_yellow
        NutritionCalc.Ampel.RED -> R.color.ampel_red
        NutritionCalc.Ampel.NONE -> R.color.ampel_none
    }

    private fun renderTotals(aggregated: StatsAggregator.Result) {
        binding.totalsContainer.removeAllViews()
        val totals = aggregated.totals
        val averages = aggregated.averages()

        fun row(labelRes: Int, total: Double, average: Double?, kcal: Boolean = false) {
            val rowBinding = RowTotalBinding.inflate(layoutInflater, binding.totalsContainer, true)
            rowBinding.totalLabel.setText(labelRes)
            val sum = if (kcal) NutritionCalc.formatKcal(total) else NutritionCalc.formatAmount(total)
            val avg = average?.let {
                if (kcal) NutritionCalc.formatKcal(it) else NutritionCalc.formatAmount(it)
            } ?: "—"
            rowBinding.totalValue.text = getString(R.string.value_sum_avg, sum, avg)
        }

        row(R.string.nutrient_energy, totals.energyKcal, averages?.energyKcal, kcal = true)
        row(R.string.nutrient_fat, totals.fat, averages?.fat)
        row(R.string.nutrient_saturated, totals.saturatedFat, averages?.saturatedFat)
        row(R.string.nutrient_carbs, totals.carbs, averages?.carbs)
        row(R.string.nutrient_sugar, totals.sugar, averages?.sugar)
        row(R.string.nutrient_protein, totals.protein, averages?.protein)
        row(R.string.nutrient_salt, totals.salt, averages?.salt)
        row(R.string.nutrient_fiber, totals.fiber, averages?.fiber)
    }

    private fun export() {
        val aggregated = result ?: return
        val markdown = MarkdownBuilder.markdown(person, aggregated)
        val dir = File(filesDir, "reports").apply { mkdirs() }
        val file = File(dir, MarkdownBuilder.fileName(person, aggregated.from, aggregated.to))
        file.writeText(markdown)
        startActivity(
            Intent(this, MarkdownPreviewActivity::class.java)
                .putExtra(MarkdownPreviewActivity.EXTRA_FILE_PATH, file.absolutePath)
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_stats, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_export -> {
            export()
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
    }
}
