package com.smartfinance.tracker.ui.report

import android.content.Context
import android.graphics.Color
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
import com.smartfinance.tracker.R
import java.text.NumberFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ReportFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private var reportListenerRegistration: ListenerRegistration? = null

    private lateinit var chartContainer: LinearLayout
    private lateinit var tvReportIncome: TextView
    private lateinit var tvReportExpense: TextView
    private lateinit var tvReportNet: TextView
    
    private lateinit var topBorosContainer: LinearLayout
    
    // UI Komponen Fitur Insight Lokal Baru
    private lateinit var tvInsightDailyAvg: TextView
    private lateinit var tvInsightProjection: TextView

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

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
            text = "Analisis Laporan Keuangan"
            textSize = 20f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, (8 * density).toInt(), 0, (16 * density).toInt())
        })

        // =====================================
        // 1. KARTU SUMMARY 
        // =====================================
        val cardSummary = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 2 * density; strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        val summaryInside = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        tvReportIncome = TextView(context).apply { text = "Pemasukan: Rp 0"; setTextColor(Color.parseColor("#10B981")); textSize = 14.5f; setPadding(0, 0, 0, (4 * density).toInt()); setTypeface(null, Typeface.BOLD) }
        tvReportExpense = TextView(context).apply { text = "Pengeluaran: Rp 0"; setTextColor(Color.parseColor("#F43F5E")); textSize = 14.5f; setPadding(0, 0, 0, (4 * density).toInt()); setTypeface(null, Typeface.BOLD) }
        tvReportNet = TextView(context).apply { text = "Sisa Bersih: Rp 0"; setTextColor(Color.parseColor("#0D9488")); textSize = 16f; setTypeface(null, Typeface.BOLD); setPadding(0, (8 * density).toInt(), 0, 0) }

        summaryInside.addView(tvReportIncome)
        summaryInside.addView(tvReportExpense)
        summaryInside.addView(View(context).apply { setBackgroundColor(Color.parseColor("#E2E8F0")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { topMargin = (8 * density).toInt(); bottomMargin = (8 * density).toInt() } })
        summaryInside.addView(tvReportNet)
        cardSummary.addView(summaryInside)
        mainLayout.addView(cardSummary)

        // =====================================
        // 2. KARTU GRAFIK 
        // =====================================
        mainLayout.addView(TextView(context).apply { 
            text = "Grafik Komparasi Keuangan"
            textSize = 13.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#64748B")); setPadding((4 * density).toInt(), 0, 0, (8 * density).toInt()) 
        })
        val cardChart = MaterialCardView(context).apply { 
            radius = 14 * density; cardElevation = 1.5f * density; strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        chartContainer = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt()) 
        }
        cardChart.addView(chartContainer)
        mainLayout.addView(cardChart)

        // =====================================
        // 3. KARTU TOP BOROS 
        // =====================================
        mainLayout.addView(TextView(context).apply { 
            text = "Alokasi Kategori Terboros Bulan Ini"
            textSize = 13.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#64748B")); setPadding((4 * density).toInt(), 0, 0, (8 * density).toInt()) 
        })
        val cardTopBoros = MaterialCardView(context).apply { 
            radius = 14 * density; cardElevation = 1.5f * density; strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        topBorosContainer = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt()) 
        }
        cardTopBoros.addView(topBorosContainer)
        mainLayout.addView(cardTopBoros)

        // =====================================
        // 4. 🔥 KARTU INSIGHT LOKAL (PENGGANTI AI)
        // =====================================
        mainLayout.addView(TextView(context).apply { 
            text = "Insight Cerdas Cepat"
            textSize = 13.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#64748B")); setPadding((4 * density).toInt(), 0, 0, (8 * density).toInt()) 
        })
        val cardInsight = MaterialCardView(context).apply {
            radius = 16 * density; cardElevation = 2 * density; strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (20 * density).toInt() }
        }
        
        val insightLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        
        insightLayout.addView(TextView(context).apply {
            text = "💡 Statistik Pengeluaran Bulan Ini"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F59E0B"))
            setPadding(0, 0, 0, (12 * density).toInt())
        })

        tvInsightDailyAvg = TextView(context).apply {
            text = "Rata-rata pengeluaran harian: Menghitung..."
            textSize = 14f
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, 0, 0, (8 * density).toInt())
        }
        insightLayout.addView(tvInsightDailyAvg)

        tvInsightProjection = TextView(context).apply {
            text = "Proyeksi total pengeluaran akhir bulan: Menghitung..."
            textSize = 14f
            setTextColor(Color.parseColor("#334155"))
        }
        insightLayout.addView(tvInsightProjection)

        cardInsight.addView(insightLayout)
        mainLayout.addView(cardInsight)

        nsv.addView(mainLayout)
        root.addView(nsv)

        observeReportData()

        return root
    }

    private fun observeReportData() {
        if (!isAdded) return
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        reportListenerRegistration?.remove()
        reportListenerRegistration = firestore.collection("transactions")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
                val activeTimePrefs = prefs.getLong("active_report_time", System.currentTimeMillis())

                val calToday = Calendar.getInstance().apply { timeInMillis = activeTimePrefs }
                val calLastMonth = Calendar.getInstance().apply { timeInMillis = activeTimePrefs; add(Calendar.MONTH, -1) }

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
                    val note = data["note"] as? String ?: "Transaksi"

                    val txCal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val isThisMonth = txCal.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    val isLastMonth = txCal.get(Calendar.MONTH) == calLastMonth.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calLastMonth.get(Calendar.YEAR)

                    if (isThisMonth) {
                        if (typeRaw == "INCOME" || typeRaw == "DEBT") incomeThisMonth += amount
                        if (typeRaw == "EXPENSE" || typeRaw == "RECEIVABLE") expenseThisMonth += amount
                    }
                    if (isLastMonth) {
                        if (typeRaw == "INCOME" || typeRaw == "DEBT") incomeLastMonth += amount
                        if (typeRaw == "EXPENSE" || typeRaw == "RECEIVABLE") expenseLastMonth += amount
                    }

                    if (isThisMonth) {
                        val itemMap = HashMap<String, Any>().apply {
                            put("amount", amount)
                            put("type", typeRaw)
                            put("timestamp", timestamp)
                            put("categoryName", categoryName)
                            put("note", note)
                        }
                        allTxList.add(itemMap)
                    }
                }

                // 1. UPDATE SUMMARY
                tvReportIncome.text = "Pemasukan: ${formatRupiah.format(incomeThisMonth)}"
                tvReportExpense.text = "Pengeluaran: ${formatRupiah.format(expenseThisMonth)}"
                val net = incomeThisMonth - expenseThisMonth
                tvReportNet.text = "Sisa Bersih: ${formatRupiah.format(net)}"
                tvReportNet.setTextColor(if (net >= 0) Color.parseColor("#0D9488") else Color.parseColor("#F43F5E"))

                // 2. UPDATE CHART
                chartContainer.removeAllViews()
                val barView = com.smartfinance.tracker.ui.report.QuadVerticalBarChartView(
                    context, incomeLastMonth.toFloat(), incomeThisMonth.toFloat(), expenseLastMonth.toFloat(), expenseThisMonth.toFloat()
                )
                chartContainer.addView(barView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (160 * density).toInt()))

                // 3. UPDATE TOP BOROS
                topBorosContainer.removeAllViews()
                val filteredExpenses = allTxList.filter { 
                    val type = it["type"] as? String ?: "EXPENSE"
                    type == "EXPENSE" || type == "RECEIVABLE" 
                }
                val totalFilteredExpense = filteredExpenses.sumOf { (it["amount"] as? Double) ?: 0.0 }
                
                val aggregated = filteredExpenses.groupBy { it["categoryName"] as? String ?: "Umum" }
                    .mapValues { entry -> entry.value.sumOf { (it["amount"] as? Double) ?: 0.0 } }
                    .toList()
                    .sortedByDescending { it.second }

                if (aggregated.isEmpty()) {
                    topBorosContainer.addView(TextView(context).apply { text = "Belum ada pengeluaran bulan ini."; setTextColor(Color.GRAY); textSize = 14f; textAlignment = View.TEXT_ALIGNMENT_CENTER })
                } else {
                    aggregated.forEach { (catName, amt) ->
                        val pct = if (totalFilteredExpense > 0) ((amt / totalFilteredExpense) * 100).toInt() else 0
                        val rowLayout = LinearLayout(context).apply { 
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                        }
                        val centerInfo = LinearLayout(context).apply { 
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        centerInfo.addView(TextView(context).apply { text = catName; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD); textSize = 14f })
                        centerInfo.addView(TextView(context).apply { text = formatRupiah.format(amt); setTextColor(Color.parseColor("#64748B")); textSize = 12f; setPadding(0, 2, 0, 0) })
                        rowLayout.addView(centerInfo)
                        rowLayout.addView(TextView(context).apply { 
                            text = "$pct%"
                            setTextColor(Color.parseColor("#F43F5E"))
                            setTypeface(null, Typeface.BOLD)
                            textSize = 14f
                        })
                        topBorosContainer.addView(rowLayout)
                        topBorosContainer.addView(View(context).apply { setBackgroundColor(Color.parseColor("#F1F5F9")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()) })
                    }
                }
                
                // 4. UPDATE INSIGHT LOKAL
                val dayOfMonth = calToday.get(Calendar.DAY_OF_MONTH)
                val daysInMonth = calToday.getActualMaximum(Calendar.DAY_OF_MONTH)
                
                if (expenseThisMonth > 0 && dayOfMonth > 0) {
                    val dailyAvg = expenseThisMonth / dayOfMonth
                    val projectedTotal = dailyAvg * daysInMonth
                    tvInsightDailyAvg.text = "• Rata-rata pengeluaran harian: ${formatRupiah.format(dailyAvg)}"
                    tvInsightProjection.text = "• Proyeksi pengeluaran akhir bulan: ${formatRupiah.format(projectedTotal)}"
                } else {
                    tvInsightDailyAvg.text = "• Rata-rata pengeluaran harian: Rp 0"
                    tvInsightProjection.text = "• Proyeksi pengeluaran akhir bulan: Rp 0"
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reportListenerRegistration?.remove()
    }
}
