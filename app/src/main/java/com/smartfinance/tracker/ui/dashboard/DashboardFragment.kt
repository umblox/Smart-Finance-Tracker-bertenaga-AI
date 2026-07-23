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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.MainActivity
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.databinding.FragmentDashboardBinding
import com.smartfinance.tracker.ui.report.DetailCategoryReportFragment
import com.smartfinance.tracker.ui.report.QuadVerticalBarChartView
import com.smartfinance.tracker.ui.report.ReportFragment
import com.smartfinance.tracker.ui.transaction.HistoryTransactionFragment
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    // Inisialisasi ViewModel
    private lateinit var viewModel: DashboardViewModel

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val sdfPremiumDateTime = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind ViewModel secara aman (tanpa library tambahan)
        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]

        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val activeTimePrefs = prefs.getLong("active_report_time", System.currentTimeMillis())

        // Memicu sinkronisasi data awal
        viewModel.updatePreferences(activeTimePrefs, "BULAN INI")
        updateTabUi("BULAN INI")

        // Setup aksi klik navigasi
        binding.btnDetailLaporan.setOnClickListener { (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment()) }
        binding.btnLihatAnalisis.setOnClickListener { (activity as? MainActivity)?.navigateToSpecificFragment(DetailCategoryReportFragment()) }
        binding.btnLihatSemua.setOnClickListener { (activity as? MainActivity)?.navigateToSpecificFragment(HistoryTransactionFragment(), R.id.menu_report) }

        // Setup filter
        binding.btnTabWeek.setOnClickListener {
            viewModel.updatePreferences(activeTimePrefs, "PERMINGGU")
            updateTabUi("PERMINGGU")
        }
        binding.btnTabMonth.setOnClickListener {
            viewModel.updatePreferences(activeTimePrefs, "BULAN INI")
            updateTabUi("BULAN INI")
        }

        // Pantau Data State dari ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                renderDashboardUi(state)
            }
        }
    }

    private fun renderDashboardUi(state: DashboardUiState) {
        val density = requireContext().resources.displayMetrics.density

        // 1. Update Teks Saldo
        binding.tvTotalBalance.text = formatRupiah.format(state.totalBalance)
        binding.tvIncomeSummary.text = formatRupiah.format(state.incomeThisMonth)
        binding.tvExpenseSummary.text = formatRupiah.format(state.expenseThisMonth)

        // 2. Render Chart
        binding.chartContainer.removeAllViews()
        val chartVerticalLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val barView = QuadVerticalBarChartView(
            requireContext(), state.incomeLastMonth.toFloat(), state.incomeThisMonth.toFloat(),
            state.expenseLastMonth.toFloat(), state.expenseThisMonth.toFloat()
        )
        chartVerticalLayout.addView(barView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (160 * density).toInt()))

        val summaryLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(0, (14 * density).toInt(), 0, 0) }
        val incDiffPercent = if (state.incomeLastMonth > 0) ((state.incomeThisMonth - state.incomeLastMonth) / state.incomeLastMonth * 100).toInt() else 0
        val expDiffPercent = if (state.expenseLastMonth > 0) ((state.expenseThisMonth - state.expenseLastMonth) / state.expenseLastMonth * 100).toInt() else 0

        summaryLayout.addView(TextView(requireContext()).apply {
            text = "🔹 Ringkasan Kas Bulan ${state.activeTimeLabel} Terpusat"
            textSize = 11.5f; setTextColor(Color.parseColor("#64748B")); setPadding(0, 0, 0, (2 * density).toInt())
        })
        summaryLayout.addView(TextView(requireContext()).apply {
            val incText = if (incDiffPercent >= 0) " naik $incDiffPercent%" else " turun ${Math.abs(incDiffPercent)}%"
            val expText = if (expDiffPercent >= 0) " naik $expDiffPercent%" else " turun ${Math.abs(expDiffPercent)}%"
            text = "📈 Performa: Pemasukan$incText • Pengeluaran$expText (vs Bulan Lalu)"
            textSize = 12f; setTextColor(Color.parseColor("#0D9488")); setTypeface(null, Typeface.BOLD)
        })
        
        chartVerticalLayout.addView(summaryLayout)
        binding.chartContainer.addView(chartVerticalLayout)

        // 3. Render Pengeluaran Teratas
        binding.topExpenseContainer.removeAllViews()
        if (state.topExpenses.isEmpty()) {
            for (i in 1..3) binding.topExpenseContainer.addView(createPlaceholderRow("Kategori Kosong $i", "Belum ada alokasi dana.", density))
        } else {
            state.topExpenses.forEach { (categoryName, totalAmount) ->
                val percentage = if (state.topExpensesTotal > 0) ((totalAmount / state.topExpensesTotal) * 100).toInt() else 0
                val rowCard = MaterialCardView(requireContext()).apply {
                    radius = 12 * density; cardElevation = 1 * density; strokeWidth = 0
                    setCardBackgroundColor(Color.parseColor("#F8FAFC"))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (8 * density).toInt() }
                }
                val rowLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt()) }
                val iconCircle = FrameLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                    background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.WHITE) }
                    addView(TextView(requireContext()).apply { text = "💰"; textSize = 14f; gravity = Gravity.CENTER })
                }
                val centerInfo = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                centerInfo.addView(TextView(requireContext()).apply { text = categoryName; setTextColor(Color.parseColor("#1E293B")); setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); textSize = 14f })
                centerInfo.addView(TextView(requireContext()).apply { text = formatRupiah.format(totalAmount); setTextColor(Color.parseColor("#64748B")); textSize = 11.5f; setPadding(0, 2, 0, 0) })
                
                rowLayout.addView(iconCircle); rowLayout.addView(centerInfo)
                rowLayout.addView(TextView(requireContext()).apply { text = "$percentage%"; setTextColor(Color.parseColor("#F43F5E")); setTypeface(null, Typeface.BOLD); textSize = 14f })
                rowCard.addView(rowLayout)
                binding.topExpenseContainer.addView(rowCard)
            }
        }

        // 4. Render Transaksi Terkini
        binding.recentTxContainer.removeAllViews()
        if (state.recentTransactions.isEmpty()) {
            for (i in 1..3) binding.recentTxContainer.addView(createPlaceholderRow("Mutasi Kosong $i", "Menunggu transaksi dicatat.", density))
        } else {
            state.recentTransactions.forEach { tx ->
                val mutasiCard = MaterialCardView(requireContext()).apply {
                    radius = 14 * density; cardElevation = 1.5f * density; strokeWidth = 0
                    setCardBackgroundColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (10 * density).toInt() }
                }
                val rowLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt()) }
                val isInc = tx.type == "INCOME" || tx.type == "DEBT"
                val iconCircle = FrameLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                    background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#F1F5F9")) }
                    addView(TextView(requireContext()).apply { text = if (isInc) "📥" else "💸"; textSize = 15f; gravity = Gravity.CENTER })
                }
                val centerInfo = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                centerInfo.addView(TextView(requireContext()).apply { text = tx.note; setTextColor(Color.parseColor("#1E293B")); setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); textSize = 14.5f })
                centerInfo.addView(TextView(requireContext()).apply { text = sdfPremiumDateTime.format(Date(tx.timestamp)); setTextColor(Color.parseColor("#94A3B8")); textSize = 11.5f; setPadding(0, 2, 0, 0) })
                
                rowLayout.addView(iconCircle); rowLayout.addView(centerInfo)
                rowLayout.addView(TextView(requireContext()).apply { 
                    text = (if (isInc) "+" else "-") + formatRupiah.format(tx.amount)
                    setTextColor(Color.parseColor(if (isInc) "#0284C7" else "#F43F5E"))
                    setTypeface(null, Typeface.BOLD); textSize = 14.5f
                })
                mutasiCard.addView(rowLayout)
                binding.recentTxContainer.addView(mutasiCard)
            }
        }
    }

    private fun updateTabUi(activeFilter: String) {
        val density = requireContext().resources.displayMetrics.density
        if (activeFilter == "PERMINGGU") {
            binding.btnTabWeek.apply { setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8 * density; setColor(Color.parseColor("#1E293B")) } }
            binding.btnTabMonth.apply { setTextColor(Color.parseColor("#64748B")); setTypeface(null, Typeface.NORMAL); background = null }
        } else {
            binding.btnTabMonth.apply { setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8 * density; setColor(Color.parseColor("#1E293B")) } }
            binding.btnTabWeek.apply { setTextColor(Color.parseColor("#64748B")); setTypeface(null, Typeface.NORMAL); background = null }
        }
    }

    private fun createPlaceholderRow(mainTitle: String, subTitle: String, density: Float): View {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt()); alpha = 0.5f }
        val centerInfo = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        centerInfo.addView(TextView(requireContext()).apply { text = mainTitle; textSize = 14f; setTextColor(Color.parseColor("#94A3B8")); setTypeface(null, Typeface.ITALIC) })
        centerInfo.addView(TextView(requireContext()).apply { text = subTitle; textSize = 11f; setTextColor(Color.parseColor("#CBD5E0")) })
        layout.addView(centerInfo)
        layout.addView(TextView(requireContext()).apply { text = "Rp 0"; setTextColor(Color.parseColor("#CBD5E0")); textSize = 14f })
        return layout
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
