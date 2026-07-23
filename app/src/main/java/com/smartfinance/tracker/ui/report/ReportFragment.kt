package com.smartfinance.tracker.ui.report

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.databinding.FragmentReportBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ReportViewModel::class.java]

        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val activeTimePrefs = prefs.getLong("active_report_time", System.currentTimeMillis())

        viewModel.calculateReport(activeTimePrefs)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                renderReportUi(state)
            }
        }
    }

    private fun renderReportUi(state: ReportUiState) {
        val density = requireContext().resources.displayMetrics.density

        // 1. Update Summary
        binding.tvReportIncome.text = "Pemasukan: ${formatRupiah.format(state.incomeThisMonth)}"
        binding.tvReportExpense.text = "Pengeluaran: ${formatRupiah.format(state.expenseThisMonth)}"
        binding.tvReportNet.text = "Sisa Bersih: ${formatRupiah.format(state.netBalance)}"
        binding.tvReportNet.setTextColor(if (state.netBalance >= 0) Color.parseColor("#0D9488") else Color.parseColor("#F43F5E"))

        // 2. Update Chart
        binding.chartContainer.removeAllViews()
        val barView = QuadVerticalBarChartView(
            requireContext(),
            state.incomeLastMonth.toFloat(), state.incomeThisMonth.toFloat(),
            state.expenseLastMonth.toFloat(), state.expenseThisMonth.toFloat()
        )
        binding.chartContainer.addView(barView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (160 * density).toInt()))

        // 3. Update Top Boros
        binding.topBorosContainer.removeAllViews()
        if (!state.hasData || state.topExpenses.isEmpty()) {
            binding.topBorosContainer.addView(TextView(requireContext()).apply { 
                text = "Belum ada pengeluaran bulan ini."
                setTextColor(Color.GRAY); textSize = 14f; textAlignment = View.TEXT_ALIGNMENT_CENTER 
            })
        } else {
            state.topExpenses.forEach { (catName, amt) ->
                val pct = if (state.topExpensesTotal > 0) ((amt / state.topExpensesTotal) * 100).toInt() else 0
                val rowLayout = LinearLayout(requireContext()).apply { 
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                }
                val centerInfo = LinearLayout(requireContext()).apply { 
                    orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                centerInfo.addView(TextView(requireContext()).apply { text = catName; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD); textSize = 14f })
                centerInfo.addView(TextView(requireContext()).apply { text = formatRupiah.format(amt); setTextColor(Color.parseColor("#64748B")); textSize = 12f; setPadding(0, 2, 0, 0) })
                
                rowLayout.addView(centerInfo)
                rowLayout.addView(TextView(requireContext()).apply { 
                    text = "$pct%"
                    setTextColor(Color.parseColor("#F43F5E"))
                    setTypeface(null, Typeface.BOLD); textSize = 14f
                })
                binding.topBorosContainer.addView(rowLayout)
                binding.topBorosContainer.addView(View(requireContext()).apply { 
                    setBackgroundColor(Color.parseColor("#F1F5F9"))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()) 
                })
            }
        }

        // 4. Update Insight Lokal
        if (state.expenseThisMonth > 0) {
            binding.tvInsightDailyAvg.text = "• Rata-rata pengeluaran harian: ${formatRupiah.format(state.dailyAvg)}"
            binding.tvInsightProjection.text = "• Proyeksi pengeluaran akhir bulan: ${formatRupiah.format(state.projectedTotal)}"
        } else {
            binding.tvInsightDailyAvg.text = "• Rata-rata pengeluaran harian: Rp 0"
            binding.tvInsightProjection.text = "• Proyeksi pengeluaran akhir bulan: Rp 0"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
