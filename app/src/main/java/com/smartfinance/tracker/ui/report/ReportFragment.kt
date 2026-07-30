package com.smartfinance.tracker.ui.report

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.R
import com.smartfinance.tracker.databinding.FragmentReportBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    private fun getThemeColor(resId: Int): Int = ContextCompat.getColor(requireContext(), resId)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ReportViewModel::class.java]

        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val activeTimePrefs = prefs.getLong("active_report_time", System.currentTimeMillis())

        viewModel.setTimeFilter(TimeFilter.MONTHLY, activeTimePrefs)

        binding.toggleTimeFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnFilterDaily -> viewModel.setTimeFilter(TimeFilter.DAILY, activeTimePrefs)
                    R.id.btnFilterWeekly -> viewModel.setTimeFilter(TimeFilter.WEEKLY, activeTimePrefs)
                    R.id.btnFilterMonthly -> viewModel.setTimeFilter(TimeFilter.MONTHLY, activeTimePrefs)
                }
            }
        }
        
        setupNavigationListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state -> renderReportUi(state) }
        }
    }
    
    private fun setupNavigationListeners() {
        // [Fase 4] Menuju Detail Ringkasan (Pemasukan Bersih)
        binding.btnDetailPemasukanBersih.setOnClickListener {
            val fragment = NetIncomeDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong("EXTRA_BASE_TIME", viewModel.getBaseTime())
                }
            }
            (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
        }
        
        // [Fase 2] Menuju Rincian Pendapatan (Menuju Layar Dinamis Baru)
        binding.btnBoxIncome.setOnClickListener {
            val fragment = CategoryTrendReportFragment().apply {
                arguments = Bundle().apply {
                    putString("EXTRA_TARGET_MODE", "ALL_INCOME")
                    putLong("EXTRA_BASE_TIME", viewModel.getBaseTime())
                }
            }
            (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
        }
        
        // [Fase 2] Menuju Rincian Biaya (Menuju Layar Dinamis Baru)
        binding.btnBoxExpense.setOnClickListener {
            val fragment = CategoryTrendReportFragment().apply {
                arguments = Bundle().apply {
                    putString("EXTRA_TARGET_MODE", "ALL_EXPENSE")
                    putLong("EXTRA_BASE_TIME", viewModel.getBaseTime())
                }
            }
            (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
        }
        
        // [Fase 2] Tombol Lihat Kategori (Kita arahkan ke ALL_EXPENSE sebagai default)
        binding.btnLihatKategoriPenuh.setOnClickListener {
            binding.btnBoxExpense.performClick()
        }
    }

    private fun renderReportUi(state: ReportUiState) {
        // Render Card 1: Pemasukan Bersih
        val prefix = if (state.netBalance >= 0) "+" else ""
        binding.tvNetIncome.text = "$prefix${formatRupiah.format(state.netBalance)}"
        binding.tvNetIncome.setTextColor(if (state.netBalance >= 0) getThemeColor(R.color.income_green) else getThemeColor(R.color.expense_red))

        binding.tvTotalIncomeBar.text = "+${formatRupiah.format(state.incomeCurrent)}"
        binding.tvTotalExpenseBar.text = "-${formatRupiah.format(state.expenseCurrent)}"
        
        val totalFlow = state.incomeCurrent + state.expenseCurrent
        val incWeight = if (totalFlow > 0) ((state.incomeCurrent / totalFlow) * 100).toFloat() else 50f
        val expWeight = if (totalFlow > 0) ((state.expenseCurrent / totalFlow) * 100).toFloat() else 50f
        
        (binding.barIncomeFill.layoutParams as LinearLayout.LayoutParams).weight = incWeight
        (binding.barIncomeEmpty.layoutParams as LinearLayout.LayoutParams).weight = 100f - incWeight
        (binding.barExpenseFill.layoutParams as LinearLayout.LayoutParams).weight = expWeight
        (binding.barExpenseEmpty.layoutParams as LinearLayout.LayoutParams).weight = 100f - expWeight

        // Render Card 2: Laporan Kategori (Donat)
        binding.tvBoxIncomeVal.text = "+${formatRupiah.format(state.incomeCurrent)}"
        binding.tvBoxExpenseVal.text = "-${formatRupiah.format(state.expenseCurrent)}"
        
        binding.chartIncome.setChartData(state.topIncomeValues, state.topIncomeColors)
        binding.chartExpense.setChartData(state.topExpenseValues, state.topExpenseColors)

        // Render Card 3: Hutang Piutang
        binding.tvHutangVal.text = "+${formatRupiah.format(state.totalHutang)}"
        binding.tvPiutangVal.text = "-${formatRupiah.format(state.totalPiutang)}"
        binding.tvLainnyaVal.text = formatRupiah.format(state.totalLainnya)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
