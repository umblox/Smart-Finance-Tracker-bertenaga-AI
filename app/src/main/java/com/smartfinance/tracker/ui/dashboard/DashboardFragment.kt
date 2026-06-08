package com.smartfinance.tracker.ui.dashboard

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
import com.smartfinance.tracker.ui.transaction.TransactionEditorDialog
import kotlinx.coroutines.flow.collectLatest
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
    
    private lateinit var btnTabWeek: TextView
    private lateinit var btnTabMonth: TextView

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
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt()) 
        }

        mainLayout.addView(TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "Smart Finance AI"
            textSize = 22f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.parseColor("#1A202C"))
            setPadding(0, 0, 0, (12 * density).toInt())
        })

        val cardBalance = MaterialCardView(context).apply { 
            radius = 16 * density
            cardElevation = 0f
            strokeWidth = (1 * density).toInt()
            setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
            setCardBackgroundColor(Color.parseColor("#008080")) 
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        val balanceLayout = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt()) 
        }
        balanceLayout.addView(TextView(context).apply { text = "TOTAL SALDO BERSIH"; setTextColor(Color.parseColor("#E6FFFA")); textSize = 11f; setTypeface(null, Typeface.BOLD) })
        tvBalance = TextView(context).apply { text = "Rp 0"; setTextColor(Color.WHITE); textSize = 28f; setTypeface(null, Typeface.BOLD); setPadding(0, (4 * density).toInt(), 0, 0) }
        balanceLayout.addView(tvBalance)
        cardBalance.addView(balanceLayout)
        mainLayout.addView(cardBalance)

        val headerReportRow = createHeaderSectionRow("Laporan bulan ini", "Melihat laporan-laporan") {
            (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment())
        }
        mainLayout.addView(headerReportRow)

        val cardChart = MaterialCardView(context).apply {
            radius = 12 * density; cardElevation = 0f
            strokeWidth = (1 * density).toInt(); setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (20 * density).toInt() }
        }
        
        val chartInsideVerticalLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        val numbersHorizontalRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            weightSum = 2f
        }
        tvExpenseSummary = TextView(context).apply { 
            text = "Bulan Ini\nRp 0"
            setTextColor(Color.parseColor("#E53E3E")); textSize = 12f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTypeface(null, Typeface.BOLD)
        }
        tvIncomeSummary = TextView(context).apply { 
            text = "Bulan Ini\nRp 0"
            setTextColor(Color.parseColor("#2B6CB0")); textSize = 12f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTypeface(null, Typeface.BOLD)
        }
        numbersHorizontalRow.addView(tvExpenseSummary)
        numbersHorizontalRow.addView(tvIncomeSummary)
        chartInsideVerticalLayout.addView(numbersHorizontalRow)

        chartInsideVerticalLayout.addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#EDF2F7"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { topMargin = (12 * density).toInt() }
        })

        chartContainer = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * density).toInt() }
        }
        chartInsideVerticalLayout.addView(chartContainer)
        cardChart.addView(chartInsideVerticalLayout)
        mainLayout.addView(cardChart)

        val headerTopExpenseRow = createHeaderSectionRow("Pengeluaran teratas", "Lihat detailnya") {
            (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment())
        }
        mainLayout.addView(headerTopExpenseRow)

        val cardTopExpense = MaterialCardView(context).apply {
            radius = 12 * density; cardElevation = 0f
            strokeWidth = (1 * density).toInt(); setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (20 * density).toInt() }
        }
        
        val topExpenseInsideLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        val filterTabsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#EDF2F7"))
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (40 * density).toInt()).apply { bottomMargin = (16 * density).toInt() }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(Color.parseColor("#EDF2F7"))
            }
        }

        btnTabWeek = TextView(context).apply {
            text = "Minggu"
            textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#718096"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { switchTopFilter("PERMINGGU") }
        }
        btnTabMonth = TextView(context).apply {
            text = "Bulan"
            textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 6 * density; setColor(Color.parseColor("#4A5568")) }
            setOnClickListener { switchTopFilter("BULAN INI") }
        }
        filterTabsContainer.addView(btnTabWeek)
        filterTabsContainer.addView(btnTabMonth)
        topExpenseInsideLayout.addView(filterTabsContainer)

        topExpenseContainer = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        topExpenseInsideLayout.addView(topExpenseContainer)
        cardTopExpense.addView(topExpenseInsideLayout)
        mainLayout.addView(cardTopExpense)

        val headerRecentRow = createHeaderSectionRow("Transaksi terkini", "Lihat semua") {
            (activity as? MainActivity)?.navigateToSpecificFragment(HistoryTransactionFragment(), R.id.menu_report)
        }
        mainLayout.addView(headerRecentRow)

        val cardRecent = MaterialCardView(context).apply {
            radius = 12 * density; cardElevation = 0f
            strokeWidth = (1 * density).toInt(); setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        recentTxContainer = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
        }
        cardRecent.addView(recentTxContainer)
        mainLayout.addView(cardRecent)

        nsv.addView(mainLayout)
        root.addView(nsv)
        
        observeDatabaseTransactions()
        return root
    }

    private fun createHeaderSectionRow(title: String, actionText: String, clickAction: View.OnClickListener): LinearLayout {
        val density = requireContext().resources.displayMetrics.density
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { 
                bottomMargin = (6 * density).toInt() 
            }
            addView(TextView(context).apply {
                text = title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#718096"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = actionText
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#008080")) 
                setPadding((8 * density).toInt(), (4 * density).toInt(), 0, (4 * density).toInt())
                setOnClickListener(clickAction)
            })
        }
    }

    private fun switchTopFilter(filter: String) {
        val density = requireContext().resources.displayMetrics.density
        selectedTopFilter = filter
        
        if (filter == "PERMINGGU") {
            btnTabWeek.apply {
                setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 6 * density; setColor(Color.parseColor("#4A5568")) }
            }
            btnTabMonth.apply {
                setTextColor(Color.parseColor("#718096")); setTypeface(null, Typeface.NORMAL)
                background = null
            }
        } else {
            btnTabMonth.apply {
                setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 6 * density; setColor(Color.parseColor("#4A5568")) }
            }
            btnTabWeek.apply {
                setTextColor(Color.parseColor("#718096")); setTypeface(null, Typeface.NORMAL)
                background = null
            }
        }
        observeDatabaseTransactions()
    }

    private fun observeDatabaseTransactions() {
        if (!isAdded) return
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        lifecycleScope.launch {
            db.transactionDao().getAllTransactions().collectLatest { allTx ->
                val sdf = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
                val calToday = Calendar.getInstance()
                val calLastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }

                var balanceTotal = 0.0
                var incomeThisMonth = 0.0
                var expenseThisMonth = 0.0
                var incomeLastMonth = 0.0
                var expenseLastMonth = 0.0

                allTx.forEach { tx ->
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    val isThisMonth = txCal.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    val isLastMonth = txCal.get(Calendar.MONTH) == calLastMonth.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calLastMonth.get(Calendar.YEAR)
                    
                    val currentTypeRaw = tx.type.trim().uppercase()

                    // 🔥 FIX SINKRONISASI AKUNTANSI: Mendeteksi "DEBT" sebagai INCOME (Uang masuk dompet) 
                    // dan "RECEIVABLE" sebagai EXPENSE (Uang keluar dompet dipinjamkan) agar Dashboard klop 100% dengan AI
                    if (currentTypeRaw == "INCOME" || currentTypeRaw == "DEBT") {
                        balanceTotal += tx.amount
                        if (isThisMonth) incomeThisMonth += tx.amount
                        if (isLastMonth) incomeLastMonth += tx.amount
                    } else if (currentTypeRaw == "EXPENSE" || currentTypeRaw == "RECEIVABLE") {
                        balanceTotal -= tx.amount
                        if (isThisMonth) expenseThisMonth += tx.amount
                        if (isLastMonth) expenseLastMonth += tx.amount
                    }
                }
                
                tvBalance.text = formatRupiah.format(balanceTotal)
                tvExpenseSummary.text = "Pengeluaran\n${formatRupiah.format(expenseThisMonth)}"
                tvIncomeSummary.text = "Pemasukan\n${formatRupiah.format(incomeThisMonth)}"

                chartContainer.removeAllViews()
                val barView = QuadVerticalBarChartView(context, incomeLastMonth.toFloat(), incomeThisMonth.toFloat(), expenseLastMonth.toFloat(), expenseThisMonth.toFloat())
                chartContainer.addView(barView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (160 * density).toInt()))

                val summaryLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, (12 * density).toInt(), 0, 0)
                }

                val incDiffPercent = if (incomeLastMonth > 0) ((incomeThisMonth - incomeLastMonth) / incomeLastMonth * 100).toInt() else 0
                val expDiffPercent = if (expenseLastMonth > 0) ((expenseThisMonth - expenseLastMonth) / expenseLastMonth * 100).toInt() else 0

                summaryLayout.addView(TextView(context).apply {
                    text = "🔹 Bulan Lalu: Pemasukan ${formatRupiah.format(incomeLastMonth)} • Pengeluaran ${formatRupiah.format(expenseLastMonth)}"
                    textSize = 11f; setTextColor(Color.parseColor("#4A5568"))
                })

                summaryLayout.addView(TextView(context).apply {
                    val incText = if (incDiffPercent >= 0) " naik $incDiffPercent%" else " turun ${Math.abs(incDiffPercent)}%"
                    val expText = if (expDiffPercent >= 0) " naik $expDiffPercent%" else " turun ${Math.abs(expDiffPercent)}%"
                    text = "📈 Performa: Pemasukan$incText • Pengeluaran$expText (vs Bulan Lalu)"
                    textSize = 11f; setTextColor(Color.parseColor("#008080")); setTypeface(null, Typeface.BOLD)
                })
                chartContainer.addView(summaryLayout)

                // PENGELUARAN TERATAS
                topExpenseContainer.removeAllViews()
                val nowTime = System.currentTimeMillis()
                val filteredExpenses = allTx.filter { item -> 
                    val typeUpper = item.type.trim().uppercase()
                    typeUpper == "EXPENSE" || typeUpper == "RECEIVABLE" 
                }.filter { tx ->
                    if (selectedTopFilter == "PERMINGGU") {
                        (nowTime - tx.timestamp) <= (7L * 24 * 60 * 60 * 1000)
                    } else {
                        val t = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                        t.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && t.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    }
                }

                val totalFilteredExpenseAmount = filteredExpenses.sumOf { it.amount }

                val aggregatedExpenses = filteredExpenses.groupBy { it.categoryName }
                    .mapValues { entry -> entry.value.sumOf { it.amount } }
                    .toList()
                    .sortedByDescending { it.second }
                    .take(3)

                if (aggregatedExpenses.isEmpty()) {
                    for (i in 1..3) {
                        topExpenseContainer.addView(createPlaceholderRow("Kategori Kosong ${i}", "Belum ada alokasi dana."))
                    }
                } else {
                    aggregatedExpenses.forEach { (categoryName, totalAmount) ->
                        val percentage = if (totalFilteredExpenseAmount > 0) {
                            ((totalAmount / totalFilteredExpenseAmount) * 100).toInt()
                        } else 0

                        val rowLayout = LinearLayout(context).apply { 
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
                        }

                        val iconCircle = FrameLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#E2E8F0")) }
                            val txt = TextView(context).apply { text = "💰"; textSize = 16f; gravity = Gravity.CENTER }
                            addView(txt)
                        }
                        rowLayout.addView(iconCircle)

                        val centerInfo = LinearLayout(context).apply { 
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        centerInfo.addView(TextView(context).apply { text = categoryName; setTextColor(Color.parseColor("#2D3748")); setTypeface(null, Typeface.BOLD); textSize = 14f })
                        centerInfo.addView(TextView(context).apply { text = formatRupiah.format(totalAmount); setTextColor(Color.parseColor("#718096")); textSize = 12f })
                        rowLayout.addView(centerInfo)

                        rowLayout.addView(TextView(context).apply { 
                            text = "$percentage%"
                            setTextColor(Color.parseColor("#E53E3E"))
                            setTypeface(null, Typeface.BOLD)
                            textSize = 14f
                        })

                        topExpenseContainer.addView(rowLayout)
                    }
                }

                // TRANSAKSI TERKINI
                recentTxContainer.removeAllViews()
                val recentTxList = allTx.sortedByDescending { item -> item.timestamp }.take(4)
                
                if (recentTxList.isEmpty()) {
                    for (i in 1..3) {
                        recentTxContainer.addView(createPlaceholderRow("Mutasi Kosong ${i}", "Menunggu transaksi dicatat."))
                    }
                } else {
                    recentTxList.forEachIndexed { index, item ->
                        val rowLayout = LinearLayout(context).apply { 
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding((8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt())
                            setOnClickListener { 
                                TransactionEditorDialog(item) { observeDatabaseTransactions() }.show(parentFragmentManager, "TransactionEditorDialog")
                            }
                        }

                        val iconCircle = FrameLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#EDF2F7")) }
                            val currentTypeUpper = item.type.trim().uppercase()
                            val txt = TextView(context).apply { 
                                text = if (currentTypeUpper == "INCOME" || currentTypeUpper == "DEBT") "📥" else "💸"
                                textSize = 16f
                                gravity = Gravity.CENTER 
                            }
                            addView(txt)
                        }
                        rowLayout.addView(iconCircle)

                        val centerInfo = LinearLayout(context).apply { 
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        centerInfo.addView(TextView(context).apply { text = item.note; setTextColor(Color.parseColor("#2D3748")); setTypeface(null, Typeface.BOLD); textSize = 14f })
                        centerInfo.addView(TextView(context).apply { text = sdf.format(Date(item.timestamp)); setTextColor(Color.parseColor("#A0AEC0")); textSize = 11f })
                        rowLayout.addView(centerInfo)

                        val currentTxTypeUpper = item.type.trim().uppercase()
                        val isInc = currentTxTypeUpper == "INCOME" || currentTxTypeUpper == "DEBT"
                        val colorHex = if (isInc) "#2B6CB0" else "#E53E3E"
                        rowLayout.addView(TextView(context).apply { 
                            text = formatRupiah.format(item.amount)
                            setTextColor(Color.parseColor(colorHex))
                            setTypeface(null, Typeface.BOLD)
                            textSize = 14f
                        })

                        recentTxContainer.addView(rowLayout)
                        
                        if (index < recentTxList.size - 1) {
                            recentTxContainer.addView(View(context).apply {
                                setBackgroundColor(Color.parseColor("#F7FAFC"))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { 
                                    leftMargin = (50 * density).toInt() 
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    private fun createPlaceholderRow(mainTitle: String, subTitle: String): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density
        val layout = LinearLayout(context).apply { 
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
            alpha = 0.5f
        }
        val centerInfo = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        centerInfo.addView(TextView(context).apply { text = mainTitle; textSize = 14f; setTextColor(Color.parseColor("#A0AEC0")); setTypeface(null, Typeface.ITALIC) })
        centerInfo.addView(TextView(context).apply { text = subTitle; textSize = 11f; setTextColor(Color.parseColor("#CBD5E0")) })
        layout.addView(centerInfo)
        layout.addView(TextView(context).apply { text = "Rp 0"; setTextColor(Color.parseColor("#CBD5E0")); textSize = 14f })
        return layout
    }

    private class QuadVerticalBarChartView(
        ctx: Context,
        private val incLast: Float,
        private val incThis: Float,
        private val expLast: Float,
        private val expThis: Float
    ) : View(ctx) {
        
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
            color = Color.parseColor("#718096")
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        private val rectF = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val maxVal = Math.max(Math.max(incLast, incThis), Math.max(expLast, expThis))
            
            val canvasWidth = width.toFloat()
            val canvasHeight = height.toFloat()
            val usableHeight = canvasHeight - 40f 

            val barWidth = canvasWidth / 6f
            val spacing = barWidth / 3f

            if (maxVal == 0f) {
                paint.color = Color.parseColor("#E2E8F0")
                canvas.drawLine(0f, usableHeight, canvasWidth, usableHeight, paint)
                canvas.drawText("Belum ada data bulan lalu & ini", canvasWidth / 2, usableHeight / 2, textPaint)
                return
            }

            val xIncLast = spacing
            val hIncLast = (incLast / maxVal) * usableHeight
            paint.color = Color.parseColor("#63B3ED")
            rectF.set(xIncLast, usableHeight - hIncLast, xIncLast + barWidth, usableHeight)
            canvas.drawRoundRect(rectF, 8f, 8f, paint)

            val xIncThis = xIncLast + barWidth + (spacing / 2)
            val hIncThis = (incThis / maxVal) * usableHeight
            paint.color = Color.parseColor("#2B6CB0")
            rectF.set(xIncThis, usableHeight - hIncThis, xIncThis + barWidth, usableHeight)
            canvas.drawRoundRect(rectF, 8f, 8f, paint)
            
            canvas.drawText("Pemasukan", (xIncLast + xIncThis + barWidth) / 2f, canvasHeight - 10f, textPaint)

            val xExpLast = xIncThis + barWidth + (spacing * 2)
            val hExpLast = (expLast / maxVal) * usableHeight
            paint.color = Color.parseColor("#FEB2B2")
            rectF.set(xExpLast, usableHeight - hExpLast, xExpLast + barWidth, usableHeight)
            canvas.drawRoundRect(rectF, 8f, 8f, paint)

            val xExpThis = xExpLast + barWidth + (spacing / 2)
            val hExpThis = (expThis / maxVal) * usableHeight
            paint.color = Color.parseColor("#E53E3E")
            rectF.set(xExpThis, usableHeight - hExpThis, xExpThis + barWidth, usableHeight)
            canvas.drawRoundRect(rectF, 8f, 8f, paint)

            canvas.drawText("Pengeluaran", (xExpLast + xExpThis + barWidth) / 2f, canvasHeight - 10f, textPaint)
        }
    }
}
