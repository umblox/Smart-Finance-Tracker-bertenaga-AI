package com.smartfinance.tracker.ui.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.smartfinance.tracker.R

class NetIncomeBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = ContextCompat.getColor(context, R.color.divider_color)
    }
    private val rectF = RectF()

    private var incomes: List<Float> = emptyList()
    private var expenses: List<Float> = emptyList()

    fun setChartData(incomes: List<Float>, expenses: List<Float>) {
        this.incomes = incomes
        this.expenses = expenses
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (incomes.isEmpty() || expenses.isEmpty()) return

        val canvasWidth = width.toFloat()
        val canvasHeight = height.toFloat()
        
        // Garis Nol (Zero Line) tepat di tengah
        val zeroY = canvasHeight / 2f
        canvas.drawLine(0f, zeroY, canvasWidth, zeroY, linePaint)

        val maxIncome = incomes.maxOrNull() ?: 0f
        val maxExpense = expenses.maxOrNull() ?: 0f
        val maxVal = maxOf(maxIncome, maxExpense).coerceAtLeast(1f)

        val barCount = incomes.size
        val spacing = canvasWidth * 0.08f
        val totalSpacing = spacing * (barCount + 1)
        val barWidth = (canvasWidth - totalSpacing) / barCount

        // Sisakan ruang sedikit di atas dan bawah agar tidak terpotong
        val maxBarHeight = (canvasHeight / 2f) - 10f 

        var currentX = spacing
        val colorIncome = Color.parseColor("#38BDF8") // Biru terang ala ML
        val colorExpense = Color.parseColor("#F43F5E") // Merah ala ML

        for (i in 0 until barCount) {
            val incVal = incomes[i]
            val expVal = expenses[i]

            // Gambar Batang Pemasukan (Biru - Ke Atas)
            val incHeight = (incVal / maxVal) * maxBarHeight
            if (incHeight > 0) {
                paint.color = colorIncome
                rectF.set(currentX, zeroY - incHeight, currentX + barWidth, zeroY)
                canvas.drawRect(rectF, paint)
            }

            // Gambar Batang Pengeluaran (Merah - Ke Bawah)
            val expHeight = (expVal / maxVal) * maxBarHeight
            if (expHeight > 0) {
                paint.color = colorExpense
                rectF.set(currentX, zeroY, currentX + barWidth, zeroY + expHeight)
                canvas.drawRect(rectF, paint)
            }

            currentX += barWidth + spacing
        }
    }
}
