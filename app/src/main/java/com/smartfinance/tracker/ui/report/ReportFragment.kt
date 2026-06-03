package com.smartfinance.tracker.ui.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class ReportFragment : Fragment() {

    private lateinit var db: AppDatabase
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

    private val chartColors = intArrayOf(
        Color.parseColor("#319795"), // Teal
        Color.parseColor("#3182CE"), // Biru
        Color.parseColor("#DD6B20"), // Oranye
        Color.parseColor("#805AD5"), // Ungu
        Color.parseColor("#E53E3E"), // Merah
        Color.parseColor("#38A169")  // Hijau
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        db = AppDatabase.getDatabase(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F7FAFC"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val tvTitle = TextView(context).apply {
            text = "📊 ANALISIS LAPORAN PREMIUM"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3748"))
            setPadding(48, 48, 48, 24)
        }
        root.addView(tvTitle)

        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 0, 48, 24)
        }
        val btnTabTx = Button(context).apply { text = "Distribusi Kategori"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        val btnTabDebt = Button(context).apply { text = "Struktur Pinjaman"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        tabLayout.addView(btnTabTx)
        tabLayout.addView(btnTabDebt)
        root.addView(tabLayout)

        val scrollView = ScrollView(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        val contentContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 0, 48, 48) }
        scrollView.addView(contentContainer)
        root.addView(scrollView)

        btnTabTx.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
        btnTabDebt.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#CBD5E0"))

        btnTabTx.setOnClickListener {
            btnTabTx.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
            btnTabDebt.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#CBD5E0"))
            loadTransactionReport(contentContainer)
        }

        btnTabDebt.setOnClickListener {
            btnTabDebt.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
            btnTabTx.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#CBD5E0"))
            loadDebtReport(contentContainer)
        }

        loadTransactionReport(contentContainer)
        return root
    }

    private fun loadTransactionReport(container: LinearLayout) {
        container.removeAllViews()
        val context = requireContext()

        lifecycleScope.launch {
            val transactions = db.transactionDao().getAllTransactions().first()
            var totalExpense = 0.0
            val categoryMap = HashMap<String, Double>()

            transactions.forEach { tx ->
                if (tx.type == "EXPENSE") {
                    totalExpense += tx.amount
                    categoryMap[tx.categoryName] = (categoryMap[tx.categoryName] ?: 0.0) + tx.amount
                }
            }

            if (categoryMap.isEmpty()) {
                container.addView(createTextView("\nBelum ada data pengeluaran ter-analisis.", 14f, "#A0AEC0"))
                return@launch
            }

            val chartWrapper = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val values = ArrayList<Float>()
            val labels = ArrayList<String>()
            categoryMap.forEach { (name, amt) ->
                labels.add(name)
                values.add(amt.toFloat())
            }

            val donutChartView = CustomDonutChartView(context, values.toFloatArray(), chartColors)
            chartWrapper.addView(donutChartView)
            
            val chartCard = createCardContainer()
            chartCard.addView(chartWrapper)
            container.addView(chartCard)

            container.addView(createTextView("\nLegenda Pembagian Kategori:", 13f, "#718096", true))

            var colorIdx = 0
            categoryMap.forEach { (catName, amount) ->
                val color = chartColors[colorIdx % chartColors.size]
                val percentage = (amount / totalExpense * 100).toInt()

                val itemCard = createCardContainer()
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                
                val colorIndicator = View(context).apply {
                    setBackgroundColor(color)
                    layoutParams = LinearLayout.LayoutParams(28, 28).apply { rightMargin = 20 }
                }
                row.addView(colorIndicator)

                row.addView(createTextView(catName, 14f, "#2D3748", true).apply { 
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) 
                })
                row.addView(createTextView("${formatRupiah.format(amount)} ($percentage%)", 13f, "#4A5568"))
                
                itemCard.addView(row)
                container.addView(itemCard)
                colorIdx++
            }
        }
    }

    private fun loadDebtReport(container: LinearLayout) {
        container.removeAllViews()
        lifecycleScope.launch {
            val debts = db.debtDao().getAllDebts().first()
            var totalHutang = 0.0
            var totalPiutang = 0.0

            debts.filter { !it.isPaid }.forEach {
                if (it.type == "DEBT") totalHutang += it.remainingAmount else totalPiutang += it.remainingAmount
            }

            val itemCard = createCardContainer()
            itemCard.addView(createTextView("STRUKTUR FINANSIAL UTANG PIUTANG", 12f, "#718096", true))
            itemCard.addView(createTextView("\n💰 Piutang (Uang Anda di Orang):", 14f, "#2B6CB0"))
            itemCard.addView(createTextView(formatRupiah.format(totalPiutang), 20f, "#2B6CB0", true))
            itemCard.addView(createTextView("\n⚠️ Hutang (Kewajiban Anda):", 14f, "#D69E2E"))
            itemCard.addView(createTextView(formatRupiah.format(totalHutang), 20f, "#D69E2E", true))
            container.addView(itemCard)
        }
    }

    private fun createCardContainer(): MaterialCardView {
        return MaterialCardView(requireContext()).apply {
            radius = 24f
            cardElevation = 2f
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
            setPadding(32, 32, 32, 32)
        }
    }

    private fun createTextView(textStr: String, size: Float, colorHex: String, isBold: Boolean = false): TextView {
        return TextView(requireContext()).apply {
            text = textStr
            textSize = size
            setTextColor(Color.parseColor(colorHex))
            if (isBold) setTypeface(null, Typeface.BOLD)
        }
    }

    // TETAP DIKUNCI MENGGUNAKAN KONTEKS HURUF KECIL DAN IMPORT EKSTERNAL YANG VALID
    private class CustomDonutChartView(ctx: Context, private val dataValues: FloatArray, private val colors: IntArray) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 50f }
        private val rectF = RectF()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(400, 400)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val total = dataValues.sum()
            if (total == 0f) return

            rectF.set(50f, 50f, width.toFloat() - 50f, height.toFloat() - 50f)
            var startAngle = 0f

            for (i in dataValues.indices) {
                val sweepAngle = (dataValues[i] / total) * 360f
                paint.color = colors[i % colors.size]
                canvas.drawArc(rectF, startAngle, sweepAngle, false, paint)
                startAngle += sweepAngle
            }
        }
    }
}
