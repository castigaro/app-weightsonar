package com.appsonar.weightsonar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.appsonar.weightsonar.R

/**
 * Linie mit Punkten (Gewichtsverlauf). Reines Canvas; Farben aus
 * values/values-night, dadurch Dark-Mode-fest.
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Point(val label: String, val value: Double)

    private var points: List<Point> = emptyList()

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_grid)
        strokeWidth = dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_text)
        textSize = dp(11f)
    }

    fun setPoints(newPoints: List<Point>) {
        points = newPoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return

        val values = points.map { it.value }
        var minValue = values.min()
        var maxValue = values.max()
        // Flache Kurven nicht auf Rauschen aufblasen: mindestens 2 kg Spanne.
        if (maxValue - minValue < 2.0) {
            val mid = (maxValue + minValue) / 2
            minValue = mid - 1.0
            maxValue = mid + 1.0
        }

        val left = dp(44f)
        val right = width - dp(12f)
        val top = dp(8f)
        val bottom = height - dp(20f)
        val chartWidth = right - left
        val chartHeight = bottom - top
        if (chartWidth <= 0 || chartHeight <= 0) return

        fun y(value: Double): Float =
            (bottom - chartHeight * ((value - minValue) / (maxValue - minValue))).toFloat()

        fun x(index: Int): Float =
            if (points.size == 1) left + chartWidth / 2
            else left + chartWidth * index / (points.size - 1)

        listOf(minValue, (minValue + maxValue) / 2, maxValue).forEach { value ->
            val lineY = y(value)
            canvas.drawLine(left, lineY, right, lineY, gridPaint)
            canvas.drawText(String.format("%.1f", value), dp(2f), lineY + dp(4f), textPaint)
        }

        val path = Path()
        points.forEachIndexed { index, point ->
            val px = x(index)
            val py = y(point.value)
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        if (points.size > 1) canvas.drawPath(path, linePaint)
        points.forEachIndexed { index, point ->
            canvas.drawCircle(x(index), y(point.value), dp(3f), dotPaint)
        }

        // Erstes und letztes Datum als Orientierung.
        canvas.drawText(points.first().label, left, height - dp(6f), textPaint)
        val lastLabel = points.last().label
        if (points.size > 1) {
            canvas.drawText(
                lastLabel,
                right - textPaint.measureText(lastLabel),
                height - dp(6f),
                textPaint,
            )
        }
    }
}
