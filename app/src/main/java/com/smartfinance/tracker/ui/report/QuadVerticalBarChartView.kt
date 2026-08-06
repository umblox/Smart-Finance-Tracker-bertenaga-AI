package com.smartfinance.tracker.ui.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import com.smartfinance.tracker.R

class QuadVerticalBarChartView(
    ctx: Context,
    private val incLast: Float,
    private val incThis: Float,
    private val expLast: Float,
    private val expThis: Float
) : View(ctx) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = ContextCompat.getColor(ctx, R.color.text_secondary)
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val rectF = RectF()

    // 🔥 FIX: Tarik string dwibahasa di luar onDraw() untuk menjaga performa rendering tetap 60fps
    private val textNoData = context.getString(R.string.chart_no_data)
    private val textIncome = context.getString(R.string.chart_income)
    private val textExpense = context.getString(R.string.chart_expense)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val maxVal = Math.max(Math.max(incLast, incThis), Math.max(expLast, expThis))
        
        val canvasWidth = width.toFloat()
        val canvasHeight = height.toFloat()
        val usableHeight = canvasHeight - 50f 

        val barWidth = canvasWidth / 6.5f
        val spacing = barWidth / 2.5f

        if (maxVal == 0f) {
            paint.color = ContextCompat.getColor(context, R.color.divider_color)
            canvas.drawLine(0f, usableHeight, canvasWidth, usableHeight, paint)
            canvas.drawText(textNoData, canvasWidth / 2, usableHeight / 2, textPaint)
            return
        }

        val r = 12f

        val xIncLast = spacing
        val hIncLast = (incLast / maxVal) * usableHeight
        paint.color = Color.parseColor("#38BDF8") 
        rectF.set(xIncLast, usableHeight - hIncLast, xIncLast + barWidth, usableHeight)
        canvas.drawRoundRect(rectF, r, r, paint)

        val xIncThis = xIncLast + barWidth + (spacing / 2)
        val hIncThis = (incThis / maxVal) * usableHeight
        paint.color = Color.parseColor("#0284C7") 
        rectF.set(xIncThis, usableHeight - hIncThis, xIncThis + barWidth, usableHeight)
        canvas.drawRoundRect(rectF, r, r, paint)
        
        canvas.drawText(textIncome, (xIncLast + xIncThis + barWidth) / 2f, canvasHeight - 10f, textPaint)

        val xExpLast = xIncThis + barWidth + (spacing * 2.2f)
        val hExpLast = (expLast / maxVal) * usableHeight
        paint.color = Color.parseColor("#FDA4AF") 
        rectF.set(xExpLast, usableHeight - hExpLast, xExpLast + barWidth, usableHeight)
        canvas.drawRoundRect(rectF, r, r, paint)

        val xExpThis = xExpLast + barWidth + (spacing / 2)
        val hExpThis = (expThis / maxVal) * usableHeight
        paint.color = Color.parseColor("#F43F5E") 
        rectF.set(xExpThis, usableHeight - hExpThis, xExpThis + barWidth, usableHeight)
        canvas.drawRoundRect(rectF, r, r, paint)

        canvas.drawText(textExpense, (xExpLast + xExpThis + barWidth) / 2f, canvasHeight - 10f, textPaint)
    }
}
