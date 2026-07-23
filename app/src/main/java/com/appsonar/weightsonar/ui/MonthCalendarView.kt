package com.appsonar.weightsonar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.appsonar.weightsonar.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Monatsraster mit Ampel-Quadraten: 🟢/🟡/🔴 für bewertete Tage, Grau für
 * erfasste Tage ohne Budget, rotes ✕ für vergangene Tage ohne Daten.
 * Tap auf einen Tag meldet das Datum an [onDayClick].
 */
class MonthCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class CellStatus { GREEN, YELLOW, RED, GRAY, MISSING, NONE }

    var onDayClick: ((LocalDate) -> Unit)? = null

    private var month: YearMonth = YearMonth.now()
    private var statuses: Map<LocalDate, CellStatus> = emptyMap()
    private var today: LocalDate = LocalDate.now()

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val missingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ampel_red)
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val dayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_text)
        textSize = dp(13f)
        textAlign = Paint.Align.CENTER
    }
    private val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_text)
        textSize = dp(12f)
        textAlign = Paint.Align.CENTER
    }

    private val statusColors = mapOf(
        CellStatus.GREEN to ContextCompat.getColor(context, R.color.ampel_green),
        CellStatus.YELLOW to ContextCompat.getColor(context, R.color.ampel_yellow),
        CellStatus.RED to ContextCompat.getColor(context, R.color.ampel_red),
        CellStatus.GRAY to ContextCompat.getColor(context, R.color.ampel_none),
    )

    fun setMonth(month: YearMonth, statuses: Map<LocalDate, CellStatus>, today: LocalDate = LocalDate.now()) {
        this.month = month
        this.statuses = statuses
        this.today = today
        requestLayout()
        invalidate()
    }

    // Montag = Spalte 0.
    private fun offset(): Int = month.atDay(1).dayOfWeek.value - 1

    private fun rows(): Int = (offset() + month.lengthOfMonth() + 6) / 7

    private fun headerHeight(): Float = dp(24f)

    private fun cellSize(): Float = width / 7f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (headerHeight() + measuredWidth / 7f * rows()).toInt()
        setMeasuredDimension(measuredWidth, desiredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cell = cellSize()
        val header = headerHeight()

        // Wochentagszeile Mo–So.
        for (column in 0 until 7) {
            val day = java.time.DayOfWeek.of(column + 1)
            canvas.drawText(
                day.getDisplayName(TextStyle.SHORT, Locale.GERMAN),
                cell * column + cell / 2,
                header - dp(8f),
                headerTextPaint,
            )
        }

        for (dayOfMonth in 1..month.lengthOfMonth()) {
            val date = month.atDay(dayOfMonth)
            val index = offset() + dayOfMonth - 1
            val column = index % 7
            val row = index / 7
            val leftEdge = cell * column
            val topEdge = header + cell * row
            val inset = dp(3f)
            val rectLeft = leftEdge + inset
            val rectTop = topEdge + inset
            val rectRight = leftEdge + cell - inset
            val rectBottom = topEdge + cell - inset

            val status = statuses[date] ?: CellStatus.NONE
            statusColors[status]?.let { color ->
                fillPaint.color = ColorUtils.setAlphaComponent(color, 70)
                canvas.drawRoundRect(
                    rectLeft, rectTop, rectRight, rectBottom, dp(6f), dp(6f), fillPaint)
            }
            if (status == CellStatus.MISSING) {
                val crossInset = cell * 0.32f
                canvas.drawLine(
                    leftEdge + crossInset, topEdge + crossInset,
                    leftEdge + cell - crossInset, topEdge + cell - crossInset, missingPaint)
                canvas.drawLine(
                    leftEdge + cell - crossInset, topEdge + crossInset,
                    leftEdge + crossInset, topEdge + cell - crossInset, missingPaint)
            }
            if (date == today) {
                canvas.drawRoundRect(
                    rectLeft, rectTop, rectRight, rectBottom, dp(6f), dp(6f), todayPaint)
            }

            canvas.drawText(
                dayOfMonth.toString(),
                leftEdge + cell / 2,
                topEdge + cell / 2 - (dayTextPaint.ascent() + dayTextPaint.descent()) / 2,
                dayTextPaint,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) return true
        if (event.action == MotionEvent.ACTION_UP) {
            val cell = cellSize()
            val column = (event.x / cell).toInt().coerceIn(0, 6)
            val row = ((event.y - headerHeight()) / cell).toInt()
            val dayOfMonth = row * 7 + column - offset() + 1
            if (row >= 0 && dayOfMonth in 1..month.lengthOfMonth()) {
                performClick()
                onDayClick?.invoke(month.atDay(dayOfMonth))
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
