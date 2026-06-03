package com.smartfinance.tracker.ui.dashboard

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

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    private var selectedTopFilter = "BULAN INI" // Saringan default top expense pengeluaran teratas

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        db = AppDatabase.getDatabase(context)

        val root = RelativeLayout(context).apply { setBackgroundColor(Color.parseColor("#F7FAFC")) }
        val nsv = NestedScrollView(context).apply { isFillViewport = true }
        val mainLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }

        // 1. SALDO UTAMA CARD
        val cardBalance = MaterialCardView(context).apply { radius = 32f; cardElevation = 6f; setCardBackgroundColor(Color.parseColor("#008080")) }
        val balanceLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(44, 44, 44, 44) }
        balanceLayout.addView(TextView(context).apply { text = "TOTAL SALDO BERSIH"; setTextColor(Color.parseColor("#B2F5EA")); textSize = 11f; setTypeface(null, Typeface.BOLD) })
        tvBalance = TextView(context).apply { text = "Rp 0"; setTextColor(Color.WHITE); textSize = 26f; setTypeface(null, Typeface.BOLD) }
        balanceLayout.addView(tvBalance)
        cardBalance.addView(balanceLayout)
        mainLayout.addView(cardBalance)

        // 2. CARD RINGKASAN GRAFIK (KLIK UNTUK BUKA MENU LAPORAN)
        mainLayout.addView(TextView(context).apply { text = "\n📊 RINGKASAN GRAFIK (KLIK UNTUK LAPORAN)"; textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#718096")) })
        val cardChartClickable = MaterialCardView(context).apply {
            radius = 24f; cardElevation = 4f; setCardBackgroundColor(Color.WHITE)
            setOnClickListener { (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment()) }
        }
        val chartSummaryLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32, 32, 32, 32); gravity = Gravity.CENTER_VERTICAL }
        val textSummaryLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        tvIncomeSummary = TextView(context).apply { text = "Pemasukan: Rp 0"; setTextColor(Color.parseColor("#38A169")); setTypeface(null, Typeface.BOLD) }
        tvExpenseSummary = TextView(context).apply { text = "Pengeluaran: Rp 0"; setTextColor(Color.parseColor("#E53E3E")); setTypeface(null, Typeface.BOLD) }
        textSummaryLayout.addView(tvIncomeSummary)
        textSummaryLayout.addView(tvExpenseSummary)
        chartSummaryLayout.addView(textSummaryLayout)
        chartSummaryLayout.addView(TextView(context).apply { text = "LIHAT LAPORAN ▶"; textSize = 11f; setTextColor(Color.parseColor("#008080")); setTypeface(null, Typeface.BOLD) })
        cardChartClickable.addView(chartSummaryLayout)
        mainLayout.addView(cardChartClickable)

        // 3. SEKTOR PENGELUARAN TERATAS (TOP 3 ACCORDING TO MONEY LOVER)
        val topHeaderRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 32, 0, 12) }
        topHeaderRow.addView(TextView(context).apply { text = "🔥 3 PENGELUARAN TERATAS"; textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#718096")); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        btnTopExpenseFilter = Button(context).apply {
            text = "📅 $selectedTopFilter"
            textSize = 10f
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4A5568"))
            setTextColor(Color.WHITE)
            setOnClickListener { showTopFilterDialog() }
        }
        topHeaderRow.addView(btnTopExpenseFilter)
        mainLayout.addView(topHeaderRow)

        topExpenseContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        mainLayout.addView(topExpenseContainer)

        // 4. SEKTOR TRANSAKSI TERKINI
        mainLayout.addView(TextView(context).apply { text = "\n⏱️ TRANSAKSI TERKINI (KLIK UNTUK TRANSAKSI)"; textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#718096")); setPadding(0, 16, 0, 12) })
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
            setTitle("Filter Pengeluaran Teratas")
            setItems(options) { _, which ->
                selectedTopFilter = options[which]
                btnTopExpenseFilter.text = "📅 $selectedTopFilter"
                loadDashboardDataCore()
            }
            show()
        }
    }

    private fun loadDashboardDataCore() {
        lifecycleScope.launch {
            val allTx = db.transactionDao().getAllTransactions().first()
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))

            // 1. Hitung Saldo Total Keseluruhan
            var balanceTotal = 0.0
            var incomeSum = 0.0
            var expenseSum = 0.0
            allTx.forEach {
                if (it.type == "INCOME") { balanceTotal += it.amount; incomeSum += it.amount }
                else { balanceTotal -= it.amount; expenseSum += it.amount }
            }
            tvBalance.text = formatRupiah.format(balanceTotal)
            tvIncomeSummary.text = "🟢 Total Pemasukan: ${formatRupiah.format(incomeSum)}"
            tvExpenseSummary.text = "🔴 Total Pengeluaran: ${formatRupiah.format(expenseSum)}"

            // 2. Render Pengeluaran Teratas (Maksimal 3 Data Terbesar Terfilter Rentang Waktu)
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
                topExpenseContainer.addView(TextView(context).apply { text = "Tidak ada pengeluaran di rentang ini."; textSize = 13f; setTextColor(Color.GRAY); setPadding(0, 12, 0, 12) })
            } else {
                filteredExpenses.forEach { item ->
                    val rowCard = MaterialCardView(requireContext()).apply {
                        radius = 20f; cardElevation = 2f; setCardBackgroundColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
                        setOnClickListener { (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment()) }
                    }
                    val inner = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(24, 24, 24, 24); gravity = Gravity.CENTER_VERTICAL }
                    val txts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                    txts.addView(TextView(context).apply { text = item.note; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#2D3748")) })
                    txts.addView(TextView(context).apply { text = item.categoryName; textSize = 11f; setTextColor(Color.GRAY) })
                    inner.addView(txts)
                    inner.addView(TextView(context).apply { text = "- " + formatRupiah.format(item.amount); setTextColor(Color.parseColor("#C53030")); setTypeface(null, Typeface.BOLD) })
                    rowCard.addView(inner)
                    topExpenseContainer.addView(rowCard)
                }
            }

            // 3. Render Transaksi Terkini (5 Mutasi Terbaru - Klik Lompat Ke Tab Transaksi Navigasi Bawah)
            recentTxContainer.removeAllViews()
            val recentTxList = allTx.sortedByDescending { it.timestamp }.take(5)
            if (recentTxList.isEmpty()) {
                recentTxContainer.addView(TextView(context).apply { text = "Belum ada riwayat aliran kas."; textSize = 13f; setTextColor(Color.GRAY) })
            } else {
                recentTxList.forEach { item ->
                    val rowCard = MaterialCardView(requireContext()).apply {
                        radius = 20f; cardElevation = 2f; setCardBackgroundColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
                        setOnClickListener { (activity as? MainActivity)?.navigateToSpecificFragment(HistoryTransactionFragment(), R.id.menu_report) }
                    }
                    val inner = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(24, 24, 24, 24); gravity = Gravity.CENTER_VERTICAL }
                    val txts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                    txts.addView(TextView(context).apply { text = item.note; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#2D3748")) })
                    txts.addView(TextView(context).apply { text = "${item.categoryName} • ${sdf.format(Date(item.timestamp))}"; textSize = 11f; setTextColor(Color.GRAY) })
                    inner.addView(txts)
                    val prefix = if (item.type == "INCOME") "+" else "-"
                    val colorHex = if (item.type == "INCOME") "#2F855A" else "#C53030"
                    inner.addView(TextView(context).apply { text = "$prefix " + formatRupiah.format(item.amount); setTextColor(Color.parseColor(colorHex)); setTypeface(null, Typeface.BOLD) })
                    rowCard.addView(inner)
                    recentTxContainer.addView(rowCard)
                }
            }
        }
    }
}
