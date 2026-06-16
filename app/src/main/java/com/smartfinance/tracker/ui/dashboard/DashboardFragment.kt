package com.smartfinance.tracker.ui.dashboard

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
import com.smartfinance.tracker.MainActivity
import com.smartfinance.tracker.R
import com.smartfinance.tracker.ui.report.DetailCategoryReportFragment
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
    
    private val sdfPremiumDateTime = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            text = "Smart Finance AI"
            textSize = 22f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, 4, 0, (16 * density).toInt())
        })

        val cardBalance = MaterialCardView(context).apply { 
            radius = 16 * density
            cardElevation = 2 * density
            strokeWidth = 0
            setCardBackgroundColor(Color.parseColor("#0D9488")) 
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        val balanceLayout = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt()) 
        }
        balanceLayout.addView(TextView(context).apply { text = "TOTAL SALDO BERSIH"; setTextColor(Color.parseColor("#CCFBF1")); textSize = 11f; setTypeface(null, Typeface.BOLD); letterSpacing = 0.05f })
        tvBalance = TextView(context).apply { text = "Rp 0"; setTextColor(Color.WHITE); textSize = 28f; setTypeface(null, Typeface.BOLD); setPadding(0, (6 * density).toInt(), 0, 0) }
        balanceLayout.addView(tvBalance)
        cardBalance.addView(balanceLayout)
        mainLayout.addView(cardBalance)

        val statsRowContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (20 * density).toInt() }
            weightSum = 2f
        }

        val cardInc = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 1.5f * density; strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = (6 * density).toInt() }
        }
        val incLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt()) }
        incLayout.addView(TextView(context).apply { text = "🟢 Pemasukan"; setTextColor(Color.parseColor("#64748B")); textSize = 12f })
        tvIncomeSummary = TextView(context).apply { text = "Rp 0"; setTextColor(Color.parseColor("#10B981")); textSize = 15f; setTypeface(null, Typeface.BOLD); setPadding(0, (4 * density).toInt(), 0, 0) }
        cardInc.addView(incLayout.apply { addView(tvIncomeSummary) })

        val cardExp = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 1.5f * density; strokeWidth = 0
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

        val headerReportRow = createHeaderSectionRow("Grafik Laporan Keuangan", "Detail Laporan") {
            (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment())
        }
        mainLayout.addView(headerReportRow)

        val cardChart = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 1.5f * density; strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (20 * density).toInt() }
            
            // 🔥 FIX BLANK LAYAR: Klik diarahkan sempurna ke ReportFragment
            setOnClickListener {
                (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment())
            }
        }
        
        val chartInsideVerticalLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = false // Supaya klik diteruskan ke Card
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        chartContainer = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = false // Supaya klik diteruskan ke Card
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        chartInsideVerticalLayout.addView(chartContainer)
        cardChart.addView(chartInsideVerticalLayout)
        mainLayout.addView(cardChart)

        val headerTopExpenseRow = createHeaderSectionRow("Pengeluaran Teratas", "Lihat Analisis") {
            (activity as? MainActivity)?.navigateToSpecificFragment(DetailCategoryReportFragment())
        }
        mainLayout.addView(headerTopExpenseRow)

        val cardTopExpense = MaterialCardView(context).apply {
            radius = 14 * density; cardElevation = 1.5f * density; strokeWidth = 0
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

        val headerRecentRow = createHeaderSectionRow("Transaksi Terkini", "Lihat Semua") {
            (activity as? MainActivity)?.navigateToSpecificFragment(HistoryTransactionFragment(), R.id.menu_report)
        }
        mainLayout.addView(headerRecentRow)

        recentTxContainer = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        mainLayout.addView(recentTxContainer)

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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (10 * density).toInt() }
            addView(TextView(context).apply {
                text = title
                textSize = 14f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
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

                val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
                val activeTimePrefs = prefs.getLong("active_report_time", System.currentTimeMillis())

                val calToday = Calendar.getInstance().apply { timeInMillis = activeTimePrefs }
                val calLastMonth = Calendar.getInstance().apply { timeInMillis = activeTimePrefs; add(Calendar.MONTH, -1) }

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

                    if (typeRaw == "INCOME" || typeRaw == "DEBT") {
                        balanceTotal += amount
                    } else if (typeRaw == "EXPENSE" || typeRaw == "RECEIVABLE") {
                        balanceTotal -= amount
                    }

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
                val barView = com.smartfinance.tracker.ui.report.QuadVerticalBarChartView(
                    context, incomeLastMonth.toFloat(), incomeThisMonth.toFloat(), expenseLastMonth.toFloat(), expenseThisMonth.toFloat()
                )
                chartContainer.addView(barView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (160 * density).toInt()))

                val summaryLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, (14 * density).toInt(), 0, 0)
                }

                val incDiffPercent = if (incomeLastMonth > 0) ((incomeThisMonth - incomeLastMonth) / incomeLastMonth * 100).toInt() else 0
                val expDiffPercent = if (expenseLastMonth > 0) ((expenseThisMonth - expenseLastMonth) / expenseLastMonth * 100).toInt() else 0

                val sdfMonthLabel = SimpleDateFormat("MMMM", Locale("id", "ID"))
                val labelBulanIni = sdfMonthLabel.format(calToday.time)

                summaryLayout.addView(TextView(context).apply {
                    text = "🔹 Ringkasan Kas Bulan $labelBulanIni Terpusat"
                    textSize = 11.5f; setTextColor(Color.parseColor("#64748B")); setPadding(0, 0, 0, (2 * density).toInt())
                })

                summaryLayout.addView(TextView(context).apply {
                    val incText = if (incDiffPercent >= 0) " naik $incDiffPercent%" else " turun ${Math.abs(incDiffPercent)}%"
                    val expText = if (expDiffPercent >= 0) " naik $expDiffPercent%" else " turun ${Math.abs(expDiffPercent)}%"
                    text = "📈 Performa: Pemasukan$incText • Pengeluaran$expText (vs Bulan Lalu)"
                    textSize = 12f; setTextColor(Color.parseColor("#0D9488")); setTypeface(null, Typeface.BOLD)
                })
                
                chartContainer.addView(summaryLayout)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        transactionsListenerRegistration?.remove()
    }
}
