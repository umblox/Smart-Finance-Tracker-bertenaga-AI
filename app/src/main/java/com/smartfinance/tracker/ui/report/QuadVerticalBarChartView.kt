package com.smartfinance.tracker.ui.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class QuadVerticalBarChartView(
    ctx: Context,
    private val incLast: Float,
    private val incThis: Float,
    private val expLast: Float,
    private val expThis: Float
) : View(ctx) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.parseColor("#64748B")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val rectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val maxVal = Math.max(Math.max(incLast, incThis), Math.max(expLast, expThis))
        
        val canvasWidth = width.toFloat()
        val canvasHeight = height.toFloat()
        val usableHeight = canvasHeight - 50f 

        val barWidth = canvasWidth / 6.5f
        val spacing = barWidth / 2.5f

        if (maxVal == 0f) {
            paint.color = Color.parseColor("#F1F5F9")
            canvas.drawLine(0f, usableHeight, canvasWidth, usableHeight, paint)
            canvas.drawText("Belum ada data bulan lalu & ini", canvasWidth / 2, usableHeight / 2, textPaint)
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
        
        canvas.drawText("Pemasukan", (xIncLast + xIncThis + barWidth) / 2f, canvasHeight - 10f, textPaint)

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

        canvas.drawText("Pengeluaran", (xExpLast + xExpThis + barWidth) / 2f, canvasHeight - 10f, textPaint)
    }
}

