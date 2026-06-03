package com.smartfinance.tracker.ui.dashboard

import android.app.AlertDialog
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

        // CONTAINER UTAMA (BACKGROUND ABU-ABU SUPER LEMBUT)
        val root = RelativeLayout(context).apply { setBackgroundColor(Color.parseColor("#F7FAFC")) }
        val nsv = NestedScrollView(context).apply { isFillViewport = true }
        
        val mainLayout = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt()) 
        }

        // TITLE BAR
        val tvMainTitle = TextView(context).apply {
            text = "Smart Finance AI"
            textSize = 22f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.parseColor("#1A202C"))
            setPadding(0, 0, 0, (16 * density).toInt())
        }
        mainLayout.addView(tvMainTitle)

        // 1. KARTU SALDO UTAMA MEWAH (SUDUT MELENGKUNG HALUS)
        val cardBalance = MaterialCardView(context).apply { 
            radius = 24 * density
            cardElevation = 0f
            strokeWidth = (1 * density).toInt()
            setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
            setCardBackgroundColor(Color.parseColor("#008080")) 
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val balanceLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt()) }
        balanceLayout.addView(TextView(context).apply { text = "TOTAL SALDO BERSIH DOMPET"; setTextColor(Color.parseColor("#E6FFFA")); textSize = 11f; setTypeface(null, Typeface.BOLD) })
        tvBalance = TextView(context).apply { text = "Rp 0"; setTextColor(Color.WHITE); textSize = 28f; setTypeface(null, Typeface.BOLD); setPadding(0, (6 * density).toInt(), 0, 0) }
        balanceLayout.addView(tvBalance)
        cardBalance.addView(balanceLayout)
        mainLayout.addView(cardBalance)

        // 2. KARTU GRAFIK RINGKASAN DATA (KLIK MELOMPAT KE REPORT DETAIL)
        mainLayout.addView(TextView(context).apply { text = "RINGKASAN GRAFIK KAS PENGGUNA"; textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#A0AEC0")); setPadding(0, (20 * density).toInt(), 0, (8 * density).toInt()) })
        
        val cardChartClickable = MaterialCardView(context).apply {
            radius = 16 * density
            cardElevation = 0f
            strokeWidth = (1 * density).toInt()
            setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
            setCardBackgroundColor(Color.WHITE)
            setOnClickListener { (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment()) }
        }
        val chartSummaryLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt()); gravity = Gravity.CENTER_VERTICAL }
        val textSummaryLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        tvIncomeSummary = TextView(context).apply { text = "Pemasukan: Rp 0"; setTextColor(Color.parseColor("#38A169")); setTypeface(null, Typeface.BOLD); textSize = 14f }
        tvExpenseSummary = TextView(context).apply { text = "Pengeluaran: Rp 0"; setTextColor(Color.parseColor("#E53E3E")); setTypeface(null, Typeface.BOLD); textSize = 14f; setPadding(0, (4 * density).toInt(), 0, 0) }
        textSummaryLayout.addView(tvIncomeSummary)
        textSummaryLayout.addView(tvExpenseSummary)
        chartSummaryLayout.addView(textSummaryLayout)
        chartSummaryLayout.addView(TextView(context).apply { text = "ANALISIS ▶"; textSize = 12f; setTextColor(Color.parseColor("#008080")); setTypeface(null, Typeface.BOLD) })
        cardChartClickable.addView(chartSummaryLayout)
        mainLayout.addView(cardChartClickable)

        // 3. SEKTOR 3 PENGELUARAN TERATAS
        val topHeaderRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, (20 * density).toInt(), 0, (8 * density).toInt()) }
        topHeaderRow.addView(TextView(context).apply { text = "3 PENGELUARAN TERATAS"; textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#A0AEC0")); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        btnTopExpenseFilter = Button(context).apply {
            text = "📅 $selectedTopFilter"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#EDF2F7"))
            setTextColor(Color.parseColor("#4A5568"))
            setOnClickListener { showTopFilterDialog() }
        }
        topHeaderRow.addView(btnTopExpenseFilter)
        mainLayout.addView(topHeaderRow)

        topExpenseContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        mainLayout.addView(topExpenseContainer)

        // 4. SEKTOR TRANSAKSI TERKINI
        mainLayout.addView(TextView(context).apply { text = "TRANSAKSI TERKINI"; textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#A0AEC0")); setPadding(0, (16 * density).toInt(), 0, (8 * density).toInt()) })
        recentTxContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        mainLayout.addView(recentTxContainer)

        nsv.addView(mainLayout)
        root.addView(nsv)
        
        loadDashboardDataCore()
        return root
    }

    private fun showTopFilterDialog() {
        val options = arrayOf("PERMINGGU", "BULAN INI")
        AlertDialog.Builder(requireContext()).apply {
            setTitle("Filter Rentang Waktu")
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
            allTx.forEach {
                if (it.type == "INCOME") { balanceTotal += it.amount; incomeSum += it.amount }
                else { balanceTotal -= it.amount; expenseSum += it.amount }
            }
            tvBalance.text = formatRupiah.format(balanceTotal)
            tvIncomeSummary.text = "🟢 Pemasukan: ${formatRupiah.format(incomeSum)}"
            tvExpenseSummary.text = "🔴 Pengeluaran: ${formatRupiah.format(expenseSum)}"

            // Render Pengeluaran Teratas
            topExpenseContainer.removeAllViews()
            val nowTime = System.currentTimeMillis()
            val filteredExpenses = allTx.filter { it.type == "EXPENSE" }.filter { tx ->
                if (selectedTopFilter == "PERMINGGU") {
                    (nowTime - tx.timestamp) <= (7L * 24 * 60 * 60 * 1000)
                } else {
                    val c = Calendar.getInstance(); val t = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    t.get(Calendar.MONTH) == c.get(Calendar.MONTH) && t.get(Calendar.YEAR) == c.get(Calendar.YEAR)
                }
            }.sortedByDescending { it.amount }.take(3)

            if (filteredExpenses.isEmpty()) {
                topExpenseContainer.addView(TextView(context).apply { text = "Tidak ada alokasi pengeluaran."; textSize = 13f; setTextColor(Color.GRAY); setPadding((12 * density).toInt(), (12 * density).toInt(), 0, (12 * density).toInt()) })
            } else {
                filteredExpenses.forEach { item ->
                    val rowCard = MaterialCardView(context).apply {
                        radius = 12 * density; cardElevation = 0f
                        strokeWidth = (1 * density).toInt(); setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
                        setCardBackgroundColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (8 * density).toInt() }
                        setOnClickListener { (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment()) }
                    }
                    val inner = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt()); gravity = Gravity.CENTER_VERTICAL }
                    val txts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                    txts.addView(TextView(context).apply { text = item.note; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#2D3748")); textSize = 14f })
                    txts.addView(TextView(context).apply { text = item.categoryName; textSize = 11f; setTextColor(Color.parseColor("#718096")); setPadding(0, 2, 0, 0) })
                    inner.addView(txts)
                    inner.addView(TextView(context).apply { text = "- " + formatRupiah.format(item.amount); setTextColor(Color.parseColor("#E53E3E")); setTypeface(null, Typeface.BOLD); textSize = 14f })
                    rowCard.addView(inner)
                    topExpenseContainer.addView(rowCard)
                }
            }

            // Render Transaksi Terkini
            recentTxContainer.removeAllViews()
            val recentTxList = allTx.sortedByDescending { it.timestamp }.take(5)
            if (recentTxList.isEmpty()) {
                recentTxContainer.addView(TextView(context).apply { text = "Belum ada riwayat mutasi kas."; textSize = 13f; setTextColor(Color.GRAY); setPadding((12 * density).toInt(), (12 * density).toInt(), 0, (12 * density).toInt()) })
            } else {
                recentTxList.forEach { item ->
                    val rowCard = MaterialCardView(context).apply {
                        radius = 12 * density; cardElevation = 0f
                        strokeWidth = (1 * density).toInt(); setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
                        setCardBackgroundColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (8 * density).toInt() }
                        setOnClickListener { (activity as? MainActivity)?.navigateToSpecificFragment(HistoryTransactionFragment(), R.id.menu_report) }
                    }
                    val inner = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt()); gravity = Gravity.CENTER_VERTICAL }
                    val txts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                    txts.addView(TextView(context).apply { text = item.note; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#2D3748")); textSize = 14f })
                    txts.addView(TextView(context).apply { text = "${item.categoryName} • ${sdf.format(Date(item.timestamp))}"; textSize = 11f; setTextColor(Color.parseColor("#718096")); setPadding(0, 2, 0, 0) })
                    inner.addView(txts)
                    val prefix = if (item.type == "INCOME") "+" else "-"
                    val colorHex = if (item.type == "INCOME") "#38A169" else "#E53E3E"
                    inner.addView(TextView(context).apply { text = "$prefix " + formatRupiah.format(item.amount); setTextColor(Color.parseColor(colorHex)); setTypeface(null, Typeface.BOLD); textSize = 14f })
                    rowCard.addView(inner)
                    recentTxContainer.addView(rowCard)
                }
            }
        }
    }
}
