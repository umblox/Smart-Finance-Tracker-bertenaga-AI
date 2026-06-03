package com.smartfinance.tracker.ui.dashboard

import android.app.AlertDialog
import android.content.Context // IMPORT UTAMA YANG TADI KETINGGALAN
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
import android.widget.*
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.MainActivity
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.ui.report.ReportFragment
import com.smartfinance.tracker.ui.transaction.HistoryTransactionFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private lateinit var db: AppDatabase
    private lateinit var chartContainer: LinearLayout
    private lateinit var topExpenseContainer: LinearLayout
    private lateinit var recentTxContainer: LinearLayout
    
    private lateinit var tvBalance: TextView
    private lateinit var tvIncomeSummary: TextView
    private lateinit var tvExpenseSummary: TextView
    private lateinit var btnTopExpenseFilter: Button

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private var selectedTopFilter = "BULAN INI"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        db = AppDatabase.getDatabase(context)
        val density = context.resources.displayMetrics.density

        val root = RelativeLayout(context).apply { 
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#F7FAFC")) 
        }
        
        val nsv = NestedScrollView(context).apply { 
            isFillViewport = true
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        }
        
        val mainLayout = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding((18 * density).toInt(), (18 * density).toInt(), (18 * density).toInt(), (18 * density).toInt()) 
        }

        // HEADER APLIKASI
        mainLayout.addView(TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "Smart Finance AI"
            textSize = 24f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.parseColor("#1A202C"))
            setPadding(0, 0, 0, (16 * density).toInt())
        })

        // 1. KARTU SALDO UTAMA
        val cardBalance = MaterialCardView(context).apply { 
            radius = 20 * density
            cardElevation = 0f
            strokeWidth = (1 * density).toInt()
            setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
            setCardBackgroundColor(Color.parseColor("#008080")) 
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val balanceLayout = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt()) 
        }
        balanceLayout.addView(TextView(context).apply { text = "TOTAL SALDO BERSIH"; setTextColor(Color.parseColor("#E6FFFA")); textSize = 11f; setTypeface(null, Typeface.BOLD) })
        tvBalance = TextView(context).apply { text = "Rp 0"; setTextColor(Color.WHITE); textSize = 30f; setTypeface(null, Typeface.BOLD); setPadding(0, (4 * density).toInt(), 0, 0) }
        balanceLayout.addView(tvBalance)
        cardBalance.addView(balanceLayout)
        mainLayout.addView(cardBalance)

        // LABEL AREA GRAFIK
        mainLayout.addView(TextView(context).apply { 
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "GRAFIK & ALIRAN KAS KEUANGAN"
            textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#A0AEC0"))
            setPadding(0, (20 * density).toInt(), 0, (8 * density).toInt()) 
        })
        
        // 2. KARTU ANALISIS VISUAL (MONEY LOVER STYLE)
        val cardChart = MaterialCardView(context).apply {
            radius = 16 * density; cardElevation = 0f
            strokeWidth = (1 * density).toInt(); setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment()) }
        }
        
        val chartInsideVerticalLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding((18 * density).toInt(), (18 * density).toInt(), (18 * density).toInt(), (18 * density).toInt())
        }

        // Pemasukan & Pengeluaran Bersebelahan
        val numbersHorizontalRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            weightSum = 2f
        }
        
        tvIncomeSummary = TextView(context).apply { 
            text = "Pemasukan\nRp 0"
            setTextColor(Color.parseColor("#38A169")); setTypeface(null, Typeface.BOLD); textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
        }
        tvExpenseSummary = TextView(context).apply { 
            text = "Pengeluaran\nRp 0"
            setTextColor(Color.parseColor("#E53E3E")); setTypeface(null, Typeface.BOLD); textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
        }
        numbersHorizontalRow.addView(tvIncomeSummary)
        numbersHorizontalRow.addView(tvExpenseSummary)
        chartInsideVerticalLayout.addView(numbersHorizontalRow)

        // Pie/Donut Chart di Bawah Angka Ringkasan
        chartContainer = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (120 * density).toInt()).apply { 
                topMargin = (16 * density).toInt()
                bottomMargin = (6 * density).toInt()
            }
        }
        chartInsideVerticalLayout.addView(chartContainer)

        chartInsideVerticalLayout.addView(TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "Lihat Analisis Detail Laporan Lengkap ▶"
            textSize = 11f; setTextColor(Color.parseColor("#008080")); setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, 0)
        })
        
        cardChart.addView(chartInsideVerticalLayout)
        mainLayout.addView(cardChart)

        // 3. SEKTOR 3 PENGELUARAN TERATAS
        val topHeaderRow = LinearLayout(context).apply { 
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, (20 * density).toInt(), 0, (8 * density).toInt()) 
        }
        topHeaderRow.addView(TextView(context).apply { text = "3 PENGELUARAN TERGEDE"; textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#A0AEC0")); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        btnTopExpenseFilter = Button(context).apply {
            text = "📅 $selectedTopFilter"
            textSize = 10f; setTypeface(null, Typeface.BOLD)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#EDF2F7"))
            setTextColor(Color.parseColor("#4A5568"))
            setOnClickListener { showTopFilterDialog() }
        }
        topHeaderRow.addView(btnTopExpenseFilter)
        mainLayout.addView(topHeaderRow)

        topExpenseContainer = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL 
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        mainLayout.addView(topExpenseContainer)

        // 4. SEKTOR TRANSAKSI TERKINI
        mainLayout.addView(TextView(context).apply { 
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "MUTASI TRANSAKSI TERKINI"
            textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#A0AEC0"))
            setPadding(0, (16 * density).toInt(), 0, (8 * density).toInt()) 
        })
        recentTxContainer = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL 
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        mainLayout.addView(recentTxContainer)

        nsv.addView(mainLayout)
        root.addView(nsv)
        
        loadDashboardDataCore()
        return root
    }

    private fun showTopFilterDialog() {
        val options = arrayOf("PERMINGGU", "BULAN INI")
        AlertDialog.Builder(requireContext()).apply {
            setTitle("Filter Jangka Waktu")
            setItems(options) { _, which ->
                selectedTopFilter = options[which]
                btnTopExpenseFilter.text = "📅 $selectedTopFilter"
                loadDashboardDataCore()
            }
            show()
        }
    }

    private fun loadDashboardDataCore() {
        if (!isAdded) return
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        lifecycleScope.launch {
            val allTx = db.transactionDao().getAllTransactions().first()
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))

            var balanceTotal = 0.0
            var incomeSum = 0.0
            var expenseSum = 0.0

            allTx.forEach { tx ->
                if (tx.type.trim().uppercase() == "INCOME") {
                    balanceTotal += tx.amount
                    incomeSum += tx.amount
                } else if (tx.type.trim().uppercase() == "EXPENSE") {
                    balanceTotal -= tx.amount
                    expenseSum += tx.amount
                }
            }
            
            tvBalance.text = formatRupiah.format(balanceTotal)
            tvIncomeSummary.text = "Pemasukan\n${formatRupiah.format(incomeSum)}"
            tvExpenseSummary.text = "Pengeluaran\n${formatRupiah.format(expenseSum)}"

            chartContainer.removeAllViews()
            if (expenseSum == 0.0 && incomeSum == 0.0) {
                chartContainer.addView(MiniDonutView(context, floatArrayOf(1f), intArrayOf(Color.parseColor("#E2E8F0"))))
            } else {
                chartContainer.addView(MiniDonutView(context, floatArrayOf(incomeSum.toFloat(), expenseSum.toFloat()), intArrayOf(Color.parseColor("#38A169"), Color.parseColor("#E53E3E"))))
            }

            // AREA 3 PENGELUARAN TERATAS
            topExpenseContainer.removeAllViews()
            val nowTime = System.currentTimeMillis()
            val filteredExpenses = allTx.filter { item -> item.type.trim().uppercase() == "EXPENSE" }.filter { tx ->
                if (selectedTopFilter == "PERMINGGU") {
                    (nowTime - tx.timestamp) <= (7L * 24 * 60 * 60 * 1000)
                } else {
                    val c = Calendar.getInstance(); val t = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    t.get(Calendar.MONTH) == c.get(Calendar.MONTH) && t.get(Calendar.YEAR) == c.get(Calendar.YEAR)
                }
            }.sortedByDescending { it.amount }.take(3)

            if (filteredExpenses.isEmpty()) {
                for (i in 1..3) {
                    topExpenseContainer.addView(createPlaceholderRow("Baris Data Kosong ${i}", "Belum ada alokasi pengeluaran"))
                }
            } else {
                filteredExpenses.forEach { item ->
                    val rowCard = createDataCardContainer()
                    val inner = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt()); gravity = Gravity.CENTER_VERTICAL }
                    val txts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                    txts.addView(TextView(context).apply { text = item.note; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#2D3748")); textSize = 14f })
                    txts.addView(TextView(context).apply { text = item.categoryName; textSize = 11f; setTextColor(Color.parseColor("#718096")) })
                    inner.addView(txts)
                    inner.addView(TextView(context).apply { text = "- " + formatRupiah.format(item.amount); setTextColor(Color.parseColor("#E53E3E")); setTypeface(null, Typeface.BOLD); textSize = 14f })
                    rowCard.addView(inner)
                    topExpenseContainer.addView(rowCard)
                }
            }

            // MUTASI TRANSAKSI TERKINI
            recentTxContainer.removeAllViews()
            val recentTxList = allTx.sortedByDescending { item -> item.timestamp }.take(5)
            if (recentTxList.isEmpty()) {
                for (i in 1..5) {
                    recentTxContainer.addView(createPlaceholderRow("Riwayat Kosong ${i}", "Menunggu catatan keuangan baru"))
                }
            } else {
                recentTxList.forEach { item ->
                    val rowCard = createDataCardContainer()
                    rowCard.setOnClickListener { (activity as? MainActivity)?.navigateToSpecificFragment(HistoryTransactionFragment(), R.id.menu_report) }
                    
                    val inner = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt()); gravity = Gravity.CENTER_VERTICAL }
                    val txts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                    txts.addView(TextView(context).apply { text = item.note; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#2D3748")); textSize = 14f })
                    txts.addView(TextView(context).apply { text = "${item.categoryName} • ${sdf.format(Date(item.timestamp))}"; textSize = 11f; setTextColor(Color.parseColor("#718096")) })
                    inner.addView(txts)
                    
                    val isInc = item.type.trim().uppercase() == "INCOME"
                    val prefix = if (isInc) "+" else "-"
                    val colorHex = if (isInc) "#38A169" else "#E53E3E"
                    inner.addView(TextView(context).apply { text = "$prefix " + formatRupiah.format(item.amount); setTextColor(Color.parseColor(colorHex)); setTypeface(null, Typeface.BOLD); textSize = 14f })
                    rowCard.addView(inner)
                    recentTxContainer.addView(rowCard)
                }
            }
        }
    }

    private fun createDataCardContainer(): MaterialCardView {
        val density = requireContext().resources.displayMetrics.density
        return MaterialCardView(requireContext()).apply {
            radius = 12 * density; cardElevation = 0f
            strokeWidth = (1 * density).toInt(); setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (8 * density).toInt() }
        }
    }

    private fun createPlaceholderRow(mainTitle: String, subTitle: String): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density
        val card = createDataCardContainer().apply { alpha = 0.5f }
        val layout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt()); gravity = Gravity.CENTER_VERTICAL }
        
        val textLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        textLayout.addView(TextView(context).apply { text = mainTitle; textSize = 14f; setTextColor(Color.parseColor("#A0AEC0")); setTypeface(null, Typeface.ITALIC) })
        textLayout.addView(TextView(context).apply { text = subTitle; textSize = 11f; setTextColor(Color.parseColor("#CBD5E0")) })
        
        layout.addView(textLayout)
        layout.addView(TextView(context).apply { text = "Rp -"; setTextColor(Color.parseColor("#CBD5E0")); textSize = 14f })
        card.addView(layout)
        return card
    }

    private class MiniDonutView(ctx: Context, private val values: FloatArray, private val colors: IntArray) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 24f }
        private val rectF = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val total = values.sum()
            if (total == 0f) return
            
            val size = Math.min(width, height).toFloat()
            val pad = 24f
            rectF.set(pad, pad, size - pad, size - pad)
            var startAngle = -90f

            for (i in values.indices) {
                val sweep = (values[i] / total) * 360f
                paint.color = colors[i % colors.size]
                canvas.drawArc(rectF, startAngle, sweep, false, paint)
                startAngle += sweep
            }
        }
    }
}
