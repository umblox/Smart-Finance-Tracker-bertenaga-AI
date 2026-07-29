package com.smartfinance.tracker.ui.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.smartfinance.tracker.R

class DonutPieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        style = Paint.Style.STROKE 
    }
    private val rectF = RectF()
    
    private var dataValues: List<Float> = emptyList()
    private var dataColors: List<Int> = emptyList()

    fun setChartData(values: List<Float>, colors: List<Int>) {
        this.dataValues = values
        this.dataColors = colors
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Dinamis menyesuaikan ketebalan donat berdasarkan ukuran view
        val strokeW = width * 0.18f 
        paint.strokeWidth = strokeW
        
        val padding = strokeW / 2f
        rectF.set(padding, padding, width - padding, height - padding)

        if (dataValues.isEmpty() || dataValues.sum() == 0f) {
            paint.color = ContextCompat.getColor(context, R.color.divider_color)
            canvas.drawArc(rectF, 0f, 360f, false, paint)
            return
        }

        val total = dataValues.sum()
        var startAngle = -90f // Mulai dari jam 12 atas

        dataValues.forEachIndexed { index, value ->
            if (value > 0f) {
                val sweepAngle = (value / total) * 360f
                paint.color = dataColors.getOrElse(index) { ContextCompat.getColor(context, R.color.text_secondary) }
                canvas.drawArc(rectF, startAngle, sweepAngle, false, paint)
                
                // Tambahkan jarak antar potongan (opsional)
                startAngle += sweepAngle
            }
        }
    }
}

