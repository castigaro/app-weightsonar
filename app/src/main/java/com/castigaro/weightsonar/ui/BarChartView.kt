package com.castigaro.weightsonar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.castigaro.weightsonar.R
import kotlin.math.ceil
import kotlin.math.max

/**
 * Gruppenlose Balken (z. B. kcal je Tag), optional mit einem Marker-Strich
 * je Balken (Budget). Reines Canvas — keine Fremdbibliothek; Farben kommen
 * aus values/values-night und sind damit Dark-Mode-fest.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Bar(
        val label: String,
        val value: Double,
        val color: Int,
        val marker: Double? = null,
    )

    private var bars: List<Bar> = emptyList()

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_budget)
        strokeWidth = dp(2f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_grid)
        strokeWidth = dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_text)
        textSize = dp(11f)
    }

    fun setBars(newBars: List<Bar>) {
        bars = newBars
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return

        val maxValue = max(
            bars.maxOf { max(it.value, it.marker ?: 0.0) },
            1.0,
        ) * 1.1

        val left = dp(44f)
        val right = width - dp(8f)
        val top = dp(8f)
        val bottom = height - dp(20f)
        val chartWidth = right - left
        val chartHeight = bottom - top
        if (chartWidth <= 0 || chartHeight <= 0) return

        fun y(value: Double): Float = (bottom - chartHeight * (value / maxValue)).toFloat()

        // Gitterlinien mit Beschriftung: 0, halbes und volles Maximum.
        listOf(0.0, maxValue / 2, maxValue).forEach { value ->
            val lineY = y(value)
            canvas.drawLine(left, lineY, right, lineY, gridPaint)
            canvas.drawText(
                String.format("%.0f", value),
                dp(2f),
                lineY + dp(4f),
                textPaint,
            )
        }

        val slot = chartWidth / bars.size
        val barWidth = slot * 0.6f
        val labelEvery = max(1, ceil(bars.size / 6.0).toInt())

        bars.forEachIndexed { index, bar ->
            val centerX = left + slot * index + slot / 2
            val barLeft = centerX - barWidth / 2
            val barRight = centerX + barWidth / 2

            if (bar.value > 0) {
                barPaint.color = bar.color
                canvas.drawRoundRect(
                    barLeft, y(bar.value), barRight, bottom, dp(2f), dp(2f), barPaint)
            }
            bar.marker?.let { marker ->
                val markerY = y(marker)
                canvas.drawLine(
                    barLeft - slot * 0.12f, markerY, barRight + slot * 0.12f, markerY, markerPaint)
            }
            if (index % labelEvery == 0 || index == bars.lastIndex) {
                val labelWidth = textPaint.measureText(bar.label)
                canvas.drawText(
                    bar.label,
                    (centerX - labelWidth / 2).coerceIn(left, right - labelWidth),
                    height - dp(6f),
                    textPaint,
                )
            }
        }
    }
}
