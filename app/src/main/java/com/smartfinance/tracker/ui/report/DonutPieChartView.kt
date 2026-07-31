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
import kotlin.math.cos
import kotlin.math.sin

class DonutPieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        style = Paint.Style.STROKE 
    }
    // 🔥 Tambahan: Kuas untuk menggambar garis duri dan teks
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        style = Paint.Style.STROKE; strokeWidth = 2.5f 
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        textSize = 28f; textAlign = Paint.Align.CENTER; isFakeBoldText = true 
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
        
        val cx = width / 2f
        val cy = height / 2f
        
        // Ukuran donat sedikit dikecilkan dari batas layar agar duri & teks punya ruang dan tidak terpotong
        val radius = (minOf(width, height) / 2f) * 0.45f
        val strokeW = radius * 0.45f 
        
        paint.strokeWidth = strokeW
        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)

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
                val color = dataColors.getOrElse(index) { ContextCompat.getColor(context, R.color.text_secondary) }
                
                // 1. Gambar kue donatnya
                paint.color = color
                canvas.drawArc(rectF, startAngle, sweepAngle, false, paint)
                
                // 2. 🔥 LOGIKA DURI & TEKS PERSENTASE
                // Hanya gambar duri jika porsi kue cukup besar (> 7 derajat) agar tidak bertabrakan
                if (sweepAngle > 7f) {
                    // Cari titik tengah sudut potongan kue
                    val middleAngle = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
                    
                    // Titik pangkal garis (menempel di kulit luar donat)
                    val innerX = cx + (radius + strokeW / 2) * cos(middleAngle).toFloat()
                    val innerY = cy + (radius + strokeW / 2) * sin(middleAngle).toFloat()
                    
                    // Titik ujung garis
                    val outerX = cx + (radius * 1.6f) * cos(middleAngle).toFloat()
                    val outerY = cy + (radius * 1.6f) * sin(middleAngle).toFloat()
                    
                    linePaint.color = color
                    canvas.drawLine(innerX, innerY, outerX, outerY, linePaint)
                    
                    // Gambar teks persentase di ujung garis
                    textPaint.color = Color.parseColor("#757575")
                    val percent = ((value / total) * 100).toInt()
                    val textX = cx + (radius * 1.9f) * cos(middleAngle).toFloat()
                    val textY = cy + (radius * 1.9f) * sin(middleAngle).toFloat() + 10f 
                    canvas.drawText("$percent%", textX, textY, textPaint)
                }

                startAngle += sweepAngle
            }
        }
    }
}
