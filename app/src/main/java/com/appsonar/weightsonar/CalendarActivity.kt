package com.appsonar.weightsonar

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.appsonar.weightsonar.analysis.NutritionCalc
import com.appsonar.weightsonar.analysis.StatsAggregator
import com.appsonar.weightsonar.data.DayStore
import com.appsonar.weightsonar.data.Person
import com.appsonar.weightsonar.data.PersonStore
import com.appsonar.weightsonar.databinding.ActivityCalendarBinding
import com.appsonar.weightsonar.ui.MonthCalendarView
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Monatskalender mit Ampel-Markierung; Tap auf einen Tag öffnet ihn im
 * Tages-Hub — so wird die Vergangenheit nachgepflegt.
 */
class CalendarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalendarBinding
    private lateinit var person: Person
    private var month: YearMonth = YearMonth.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarBinding.inflate(layoutInflater)
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

        binding.buttonPrevMonth.setOnClickListener {
            month = month.minusMonths(1)
            render()
        }
        binding.buttonNextMonth.setOnClickListener {
            month = month.plusMonths(1)
            render()
        }
        binding.calendarView.onDayClick = { date ->
            startActivity(
                Intent(this, DayActivity::class.java)
                    .putExtra(DayActivity.EXTRA_PERSON_ID, person.id)
                    .putExtra(DayActivity.EXTRA_DATE, date.toString())
            )
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        binding.monthTitle.text =
            month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN))

        val today = LocalDate.now()
        val records = DayStore.all(this, person.id)
        val result = StatsAggregator.aggregate(
            person, records, month.atDay(1), month.atEndOfMonth(), today)

        val statuses = result.points.associate { point ->
            point.date to when {
                point.hasFood -> when (point.ampel) {
                    NutritionCalc.Ampel.GREEN -> MonthCalendarView.CellStatus.GREEN
                    NutritionCalc.Ampel.YELLOW -> MonthCalendarView.CellStatus.YELLOW
                    NutritionCalc.Ampel.RED -> MonthCalendarView.CellStatus.RED
                    // Essen erfasst, aber kein Budget (Gewicht fehlt) → grau.
                    NutritionCalc.Ampel.NONE -> MonthCalendarView.CellStatus.GRAY
                }
                point.hasData -> MonthCalendarView.CellStatus.GRAY
                point.date.isBefore(today) -> MonthCalendarView.CellStatus.MISSING
                else -> MonthCalendarView.CellStatus.NONE
            }
        }
        binding.calendarView.setMonth(month, statuses, today)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_PERSON_ID = "personId"
    }
}
