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
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smartfinance.tracker.MainActivity
import com.smartfinance.tracker.R
import com.smartfinance.tracker.ui.report.ReportFragment
import com.smartfinance.tracker.ui.transaction.HistoryTransactionFragment
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class DashboardFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private var transactionsListenerRegistration: ListenerRegistration? = null

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
        val density = context.resources.displayMetrics.density

        val root = RelativeLayout(context).apply { 
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#F8FAFC")) 
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
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, 4, 0, (16 * density).toInt())
        })

        // 1. Ambil Konstruksi Balutan Saldo Dari XML Terikat
        val cardBalance = MaterialCardView(context).apply { 
            radius = 20 * density
            cardElevation = 4 * density
            strokeWidth = 0
            setCardBackgroundColor(Color.parseColor("#0D9488")) 
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        val balanceLayout = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt()) 
        }
        balanceLayout.addView(TextView(context).apply { text = "TOTAL SALDO BERSIH"; setTextColor(Color.parseColor("#CCFBF1")); textSize = 11f; setTypeface(null, Typeface.BOLD); letterSpacing = 0.05f })
        tvBalance = TextView(context).apply { text = "Rp 0"; setTextColor(Color.WHITE); textSize = 30f; setTypeface(null, Typeface.BOLD); setPadding(0, (6 * density).toInt(), 0, 0) }
        balanceLayout.addView(tvBalance)
        cardBalance.addView(balanceLayout)
        mainLayout.addView(cardBalance)

        // 2. Baris Penggabung Kartu Kas Ringkasan Mini
        val statsRowContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (20 * density).toInt() }
            weightSum = 2f
        }

        val cardInc = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 2 * density; strokeWidth = (1 * density).toInt(); setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#F1F5F9")))
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = (6 * density).toInt() }
        }
        val incLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt()) }
        incLayout.addView(TextView(context).apply { text = "🟢 Pemasukan"; setTextColor(Color.parseColor("#64748B")); textSize = 12f })
        tvIncomeSummary = TextView(context).apply { text = "Rp 0"; setTextColor(Color.parseColor("#10B981")); textSize = 15f; setTypeface(null, Typeface.BOLD); setPadding(0, (4 * density).toInt(), 0, 0) }
        cardInc.addView(incLayout.apply { addView(tvIncomeSummary) })

        val cardExp = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 2 * density; strokeWidth = (1 * density).toInt(); setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#F1F5F9")))
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = (6 * density).toInt() }
        }
        val expLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt()) }
        expLayout.addView(TextView(context).apply { text = "🔴 Pengeluaran"; setTextColor(Color.parseColor("#64748B")); textSize = 12f })
        tvExpenseSummary = TextView(context).apply { text = "Rp 0"; setTextColor(Color.parseColor("#F43F5E")); textSize = 15f; setTypeface(null, Typeface.BOLD); setPadding(0, (4 * density).toInt(), 0, 0) }
        cardExp.addView(expLayout.apply { addView(tvExpenseSummary) })

        statsRowContainer.addView(cardInc)
        statsRowContainer.addView(cardExp)
        mainLayout.addView(statsRowContainer)

        // 3. Modul Interior Chart Infografis Infografis
        val headerReportRow = createHeaderSectionRow("Grafik Laporan Keuangan", "Detail Laporan") {
            (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment())
        }
        mainLayout.addView(headerReportRow)

        val cardChart = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 2 * density; strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (20 * density).toInt() }
        }
        
        val chartInsideVerticalLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        chartContainer = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        chartInsideVerticalLayout.addView(chartContainer)
        cardChart.addView(chartInsideVerticalLayout)
        mainLayout.addView(cardChart)

        // 4. Modul Pengeluaran Teratas
        val headerTopExpenseRow = createHeaderSectionRow("Pengeluaran Teratas", "Lihat Analisis") {
            (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment())
        }
        mainLayout.addView(headerTopExpenseRow)

        val cardTopExpense = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 2 * density; strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (20 * density).toInt() }
        }
        
        val topExpenseInsideLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        val filterTabsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (40 * density).toInt()).apply { bottomMargin = (12 * density).toInt() }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(Color.parseColor("#F1F5F9"))
            }
        }

        btnTabWeek = TextView(context).apply {
            text = "Minggu"
            textSize = 12.5f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#64748B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { switchTopFilter("PERMINGGU") }
        }
        btnTabMonth = TextView(context).apply {
            text = "Bulan"
            textSize = 12.5f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8 * density; setColor(Color.parseColor("#1E293B")) }
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

        // 5. Modul Transaksi Terkini
        val headerRecentRow = createHeaderSectionRow("Transaksi Terkini", "Lihat Semua") {
            (activity as? MainActivity)?.navigateToSpecificFragment(HistoryTransactionFragment(), R.id.menu_report)
        }
        mainLayout.addView(headerRecentRow)

        val cardRecent = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 2 * density; strokeWidth = 0
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
        
        observeCloudTransactionsLive()
        return root
    }

    private fun createHeaderSectionRow(title: String, actionText: String, clickAction: View.OnClickListener): LinearLayout {
        val density = requireContext().resources.displayMetrics.density
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { 
                bottomMargin = (10 * density).toInt() 
            }
            addView(TextView(context).apply {
                text = title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#475569"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = actionText
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#0D9488")) 
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
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8 * density; setColor(Color.parseColor("#1E293B")) }
            }
            btnTabMonth.apply {
                setTextColor(Color.parseColor("#64748B")); setTypeface(null, Typeface.NORMAL)
                background = null
            }
        } else {
            btnTabMonth.apply {
                setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8 * density; setColor(Color.parseColor("#1E293B")) }
            }
            btnTabWeek.apply {
                setTextColor(Color.parseColor("#64748B")); setTypeface(null, Typeface.NORMAL)
                background = null
            }
        }
        observeCloudTransactionsLive()
    }

    private fun observeCloudTransactionsLive() {
        if (!isAdded) return
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        transactionsListenerRegistration?.remove()

        transactionsListenerRegistration = firestore.collection("transactions")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                val sdf = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
                val calToday = Calendar.getInstance()
                val calLastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }

                var balanceTotal = 0.0
                var incomeThisMonth = 0.0
                var expenseThisMonth = 0.0
                var incomeLastMonth = 0.0
                var expenseLastMonth = 0.0

                val allTxList = ArrayList<HashMap<String, Any>>()

                for (doc in snapshots.documents) {
                    val data = doc.data ?: continue
                    val amount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                    val typeRaw = (data["type"] as? String ?: "EXPENSE").trim().uppercase(Locale.ROOT)
                    val timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    val categoryName = data["categoryName"] as? String ?: "Umum"
                    val note = data["note"] as? String ?: "Transaksi AI"

                    val txCal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val isThisMonth = txCal.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    val isLastMonth = txCal.get(Calendar.MONTH) == calLastMonth.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calLastMonth.get(Calendar.YEAR)

                    if (typeRaw == "INCOME" || typeRaw == "DEBT") {
                        balanceTotal += amount
                        if (isThisMonth) incomeThisMonth += amount
                        if (isLastMonth) incomeLastMonth += amount
                    } else if (typeRaw == "EXPENSE" || typeRaw == "RECEIVABLE") {
                        balanceTotal -= amount
                        if (isThisMonth) expenseThisMonth += amount
                        if (isLastMonth) expenseLastMonth += amount
                    }

                    val itemMap = HashMap<String, Any>().apply {
                        put("amount", amount)
                        put("type", typeRaw)
                        put("timestamp", timestamp)
                        put("categoryName", categoryName)
                        put("note", note)
                    }
                    allTxList.add(itemMap)
                }
                
                tvBalance.text = formatRupiah.format(balanceTotal)
                tvExpenseSummary.text = formatRupiah.format(expenseThisMonth)
                tvIncomeSummary.text = formatRupiah.format(incomeThisMonth)

                chartContainer.removeAllViews()
                val barView = QuadVerticalBarChartView(context, incomeLastMonth.toFloat(), incomeThisMonth.toFloat(), expenseLastMonth.toFloat(), expenseThisMonth.toFloat())
                chartContainer.addView(barView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (160 * density).toInt()))

                val summaryLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, (14 * density).toInt(), 0, 0)
                }

                val incDiffPercent = if (incomeLastMonth > 0) ((incomeThisMonth - incomeLastMonth) / incomeLastMonth * 100).toInt() else 0
                val expDiffPercent = if (expenseLastMonth > 0) ((expenseThisMonth - expenseLastMonth) / expenseLastMonth * 100).toInt() else 0

                summaryLayout.addView(TextView(context).apply {
                    text = "🔹 Bulan Lalu: Pemasukan ${formatRupiah.format(incomeLastMonth)} • Pengeluaran ${formatRupiah.format(expenseLastMonth)}"
                    textSize = 11.5f; setTextColor(Color.parseColor("#64748B")); setPadding(0, 0, 0, (2 * density).toInt())
                })

                summaryLayout.addView(TextView(context).apply {
                    val incText = if (incDiffPercent >= 0) " naik $incDiffPercent%" else " turun ${Math.abs(incDiffPercent)}%"
                    val expText = if (expDiffPercent >= 0) " naik $expDiffPercent%" else " turun ${Math.abs(expDiffPercent)}%"
                    text = "📈 Performa: Pemasukan$incText • Pengeluaran$expText (vs Bulan Lalu)"
                    textSize = 12f; setTextColor(Color.parseColor("#0D9488")); setTypeface(null, Typeface.BOLD)
                })
                chartContainer.addView(summaryLayout)

                // 3. PENGELUARAN TERATAS REAL-TIME CLOUD
                topExpenseContainer.removeAllViews()
                val nowTime = System.currentTimeMillis()
                val filteredExpenses = allTxList.filter { item -> 
                    val typeUpper = (item["type"] as? String) ?: "EXPENSE"
                    typeUpper == "EXPENSE" || typeUpper == "RECEIVABLE" 
                }.filter { item ->
                    val timestamp = (item["timestamp"] as? Long) ?: nowTime
                    if (selectedTopFilter == "PERMINGGU") {
                        (nowTime - timestamp) <= (7L * 24 * 60 * 60 * 1000)
                    } else {
                        val t = Calendar.getInstance().apply { timeInMillis = timestamp }
                        t.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && t.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    }
                }

                val totalFilteredExpenseAmount = filteredExpenses.sumOf { (it["amount"] as? Double) ?: 0.0 }

                val aggregatedExpenses = filteredExpenses.groupBy { (it["categoryName"] as? String) ?: "Umum" }
                    .mapValues { entry -> entry.value.sumOf { (it["amount"] as? Double) ?: 0.0 } }
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
                            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#F1F5F9")) }
                            val txt = TextView(context).apply { text = "💰"; textSize = 15f; gravity = Gravity.CENTER }
                            addView(txt)
                        }
                        rowLayout.addView(iconCircle)

                        val centerInfo = LinearLayout(context).apply { 
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        centerInfo.addView(TextView(context).apply { text = categoryName; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD); textSize = 14f })
                        centerInfo.addView(TextView(context).apply { text = formatRupiah.format(totalAmount); setTextColor(Color.parseColor("#64748B")); textSize = 12f })
                        rowLayout.addView(centerInfo)

                        rowLayout.addView(TextView(context).apply { 
                            text = "$percentage%"
                            setTextColor(Color.parseColor("#F43F5E"))
                            setTypeface(null, Typeface.BOLD)
                            textSize = 14f
                        })

                        topExpenseContainer.addView(rowLayout)
                    }
                }

                // 4. TRANSAKSI TERKINI REAL-TIME CLOUD
                recentTxContainer.removeAllViews()
                val recentTxList = allTxList.sortedByDescending { (it["timestamp"] as? Long) ?: 0L }.take(4)
                
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
                        }

                        val note = (item["note"] as? String) ?: "Transaksi AI"
                        val timestamp = (item["timestamp"] as? Long) ?: nowTime
                        val amount = (item["amount"] as? Double) ?: 0.0
                        val currentTypeUpper = ((item["type"] as? String) ?: "EXPENSE").trim().uppercase(Locale.ROOT)

                        val iconCircle = FrameLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#F1F5F9")) }
                            val txt = TextView(context).apply { 
                                text = if (currentTypeUpper == "INCOME" || currentTypeUpper == "DEBT") "📥" else "💸"
                                textSize = 15f
                                gravity = Gravity.CENTER 
                            }
                            addView(txt)
                        }
                        rowLayout.addView(iconCircle)

                        val centerInfo = LinearLayout(context).apply { 
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        centerInfo.addView(TextView(context).apply { text = note; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD); textSize = 14f })
                        centerInfo.addView(TextView(context).apply { text = sdf.format(Date(timestamp)); setTextColor(Color.parseColor("#94A3B8")); textSize = 11f })
                        rowLayout.addView(centerInfo)

                        val isInc = currentTypeUpper == "INCOME" || currentTypeUpper == "DEBT"
                        val colorHex = if (isInc) "#0284C7" else "#F43F5E"
                        rowLayout.addView(TextView(context).apply { 
                            text = formatRupiah.format(amount)
                            setTextColor(Color.parseColor(colorHex))
                            setTypeface(null, Typeface.BOLD)
                            textSize = 14f
                        })

                        recentTxContainer.addView(rowLayout)
                        
                        if (index < recentTxList.size - 1) {
                            recentTxContainer.addView(View(context).apply {
                                setBackgroundColor(Color.parseColor("#F1F5F9"))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { 
                                    leftMargin = (50 * density).toInt() 
                                }
                            })
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
        centerInfo.addView(TextView(context).apply { text = mainTitle; textSize = 14f; setTextColor(Color.parseColor("#94A3B8")); setTypeface(null, Typeface.ITALIC) })
        centerInfo.addView(TextView(context).apply { text = subTitle; textSize = 11f; setTextColor(Color.parseColor("#CBD5E0")) })
        layout.addView(centerInfo)
        layout.addView(TextView(context).apply { text = "Rp 0"; setTextColor(Color.parseColor("#CBD5E0")); textSize = 14f })
        return layout
    }

    override fun onDestroyView() {
        super.onDestroyView()
        transactionsListenerRegistration?.remove()
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

            // 🔥 SMOOTH ROUNDED COLUMN: Mengubah sudut radius tiang chart biar oval halus mewah
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
}
