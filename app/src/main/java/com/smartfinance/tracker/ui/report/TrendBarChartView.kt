package com.smartfinance.tracker.ui.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.smartfinance.tracker.R

class TrendBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val rectF = RectF()
    
    private var dataValues: List<Float> = emptyList()
    private var isExpense: Boolean = true

    fun setChartData(values: List<Float>, expenseMode: Boolean) {
        this.dataValues = values
        this.isExpense = expenseMode
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataValues.isEmpty()) return

        val canvasWidth = width.toFloat()
        val canvasHeight = height.toFloat()
        
        val maxVal = dataValues.maxOrNull() ?: 1f
        val actualMax = if (maxVal == 0f) 1f else maxVal

        val barCount = dataValues.size
        val spacing = canvasWidth * 0.05f
        val totalSpacing = spacing * (barCount + 1)
        val barWidth = (canvasWidth - totalSpacing) / barCount

        // Garis Nol (Zero Line) di atas untuk pengeluaran, di bawah untuk pemasukan
        val zeroY = if (isExpense) 10f else canvasHeight - 10f
        paint.color = ContextCompat.getColor(context, R.color.divider_color)
        canvas.drawLine(0f, zeroY, canvasWidth, zeroY, paint)

        paint.color = ContextCompat.getColor(context, if (isExpense) R.color.expense_red else R.color.income_green)
        val radius = 8f

        var currentX = spacing
        for (value in dataValues) {
            val barHeight = (value / actualMax) * (canvasHeight - 40f) // Sisakan ruang
            
            if (isExpense) {
                // Gambar ke bawah
                rectF.set(currentX, zeroY, currentX + barWidth, zeroY + barHeight)
            } else {
                // Gambar ke atas
                rectF.set(currentX, zeroY - barHeight, currentX + barWidth, zeroY)
            }
            canvas.drawRoundRect(rectF, radius, radius, paint)
            currentX += barWidth + spacing
        }
    }
}
