package com.katiearose.sobriety.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.max

/**
 * A small, dependency-free bar chart for the Statistics screen (Sobriety Neo).
 *
 * Usage: `chart.setData(listOf("Jan" to 2f, "Feb" to 0f, ...))`. Every bar gets its label below
 * and its value above. Colors come from the current theme (colorPrimary / text color), so it
 * works in light, dark and AMOLED mode.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<Pair<String, Float>> = emptyList()
    private var valueFormatter: (Float) -> String = { if (it == it.toInt().toFloat()) it.toInt().toString() else "%.1f".format(it) }

    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@BarChartView, com.katiearose.sobriety.R.attr.colorPrimary)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@BarChartView, android.R.attr.textColorPrimary)
        textSize = sp(11f)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    /** Sets the bars (label, value) and redraws. [formatter] formats the value above a bar. */
    fun setData(bars: List<Pair<String, Float>>, formatter: ((Float) -> String)? = null) {
        data = bars
        if (formatter != null) valueFormatter = formatter
        contentDescription = bars.joinToString(", ") { "${it.first}: ${valueFormatter(it.second)}" }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = resolveSize(dp(160f).toInt(), heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return
        val labelHeight = sp(16f)
        val valueHeight = sp(14f)
        val chartTop = paddingTop + valueHeight
        val chartBottom = height - paddingBottom - labelHeight
        val chartHeight = max(chartBottom - chartTop, 1f)
        val slot = (width - paddingLeft - paddingRight).toFloat() / data.size
        val barWidth = slot * 0.6f
        val maxValue = max(data.maxOf { it.second }, 1f)
        // Labels are thinned out when there are too many bars for the width.
        val labelEvery = max(1, (data.size * sp(28f) / width).toInt() + 1)

        data.forEachIndexed { index, (label, value) ->
            val centerX = paddingLeft + slot * index + slot / 2
            val barHeight = chartHeight * (value / maxValue)
            rect.set(centerX - barWidth / 2, chartBottom - barHeight, centerX + barWidth / 2, chartBottom)
            canvas.drawRoundRect(rect, dp(3f), dp(3f), barPaint)
            if (value > 0f) canvas.drawText(valueFormatter(value), centerX, chartBottom - barHeight - dp(3f), textPaint)
            if (index % labelEvery == 0) canvas.drawText(label, centerX, height - paddingBottom - dp(3f), textPaint)
        }
    }
}
