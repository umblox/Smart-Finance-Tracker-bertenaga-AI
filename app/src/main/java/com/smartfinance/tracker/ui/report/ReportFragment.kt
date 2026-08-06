package com.smartfinance.tracker.ui.report

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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
    // Uang tetap dikunci pada Format IDR agar muncul Rp
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

        viewModel.setTimeMillis(activeTimePrefs)
        
        setupNavigationListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state -> 
                renderTimeNav(state)
                renderReportUi(state) 
            }
        }
    }
    
    private fun renderTimeNav(state: ReportUiState) {
        binding.layoutTimeNavigation.removeAllViews()
        val density = requireContext().resources.displayMetrics.density
        var selectedTabView: View? = null
        
        state.timeNavItems.forEach { navItem ->
            val tab = TextView(requireContext()).apply {
                text = navItem.label; textSize = 13f; isAllCaps = true
                setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
                
                if (navItem.isSelected) {
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary)); setTypeface(null, Typeface.BOLD)
                    background = android.graphics.drawable.LayerDrawable(arrayOf(
                        android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),
                        android.graphics.drawable.ColorDrawable(ContextCompat.getColor(requireContext(), R.color.text_primary))
                    )).apply { setLayerInset(1, 0, (40 * density).toInt(), 0, 0) }
                    selectedTabView = this 
                } else {
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary)); setTypeface(null, Typeface.NORMAL); background = null
                }
                
                setOnClickListener { 
                    val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putLong("active_report_time", navItem.timeMillis).apply()
                    viewModel.setTimeMillis(navItem.timeMillis) 
                }
            }
            binding.layoutTimeNavigation.addView(tab)
        }
        
        selectedTabView?.let { tab ->
            binding.scrollTimeNav.post {
                val scrollX = tab.left - (binding.scrollTimeNav.width / 2) + (tab.width / 2)
                binding.scrollTimeNav.scrollTo(scrollX, 0)
            }
        }
    }

    private fun setupNavigationListeners() {
        binding.btnDetailPemasukanBersih.setOnClickListener {
            val fragment = NetIncomeDetailFragment().apply {
                arguments = Bundle().apply { putLong("EXTRA_BASE_TIME", viewModel.getBaseTime()) }
            }
            (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
        }
        
        binding.btnBoxIncome.setOnClickListener {
            val fragment = CategoryTrendReportFragment().apply {
                arguments = Bundle().apply {
                    putString("EXTRA_TARGET_MODE", "ALL_INCOME")
                    putString("EXTRA_TARGET_TYPE", "GLOBAL")
                    putLong("EXTRA_BASE_TIME", viewModel.getBaseTime())
                    putBoolean("EXTRA_SHOW_DROPDOWN", false) 
                    putBoolean("EXTRA_FROM_REPORT_MENU", false) 
                }
            }
            (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
        }
        
        binding.btnBoxExpense.setOnClickListener {
            val fragment = CategoryTrendReportFragment().apply {
                arguments = Bundle().apply {
                    putString("EXTRA_TARGET_MODE", "ALL_EXPENSE")
                    putString("EXTRA_TARGET_TYPE", "GLOBAL")
                    putLong("EXTRA_BASE_TIME", viewModel.getBaseTime())
                    putBoolean("EXTRA_SHOW_DROPDOWN", false) 
                    putBoolean("EXTRA_FROM_REPORT_MENU", false) 
                }
            }
            (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
        }
        
        binding.btnLihatKategoriPenuh.setOnClickListener {
            val fragment = CategoryTrendReportFragment().apply {
                arguments = Bundle().apply {
                    putString("EXTRA_TARGET_MODE", "AUTO_TOP_EXPENSE")
                    putString("EXTRA_TARGET_TYPE", "CATEGORY")
                    putLong("EXTRA_BASE_TIME", viewModel.getBaseTime())
                    putBoolean("EXTRA_SHOW_DROPDOWN", true) 
                    putBoolean("EXTRA_FROM_REPORT_MENU", true) 
                }
            }
            (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
        }

        (binding.tvHutangVal.parent as? View)?.setOnClickListener {
            val fragment = CategoryAnalyticsFragment().apply {
                arguments = Bundle().apply {
                    putString("EXTRA_CATEGORY_NAME", "FILTER_HUTANG")
                    putLong("EXTRA_BASE_TIME", viewModel.getBaseTime())
                    putString("EXTRA_TIME_FILTER", "MONTHLY") 
                }
            }
            (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
        }

        (binding.tvPiutangVal.parent as? View)?.setOnClickListener {
            val fragment = CategoryAnalyticsFragment().apply {
                arguments = Bundle().apply {
                    putString("EXTRA_CATEGORY_NAME", "FILTER_PIUTANG")
                    putLong("EXTRA_BASE_TIME", viewModel.getBaseTime())
                    putString("EXTRA_TIME_FILTER", "MONTHLY") 
                }
            }
            (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
        }

        (binding.tvLainnyaVal.parent as? View)?.setOnClickListener {
            val fragment = CategoryAnalyticsFragment().apply {
                arguments = Bundle().apply {
                    putString("EXTRA_CATEGORY_NAME", "FILTER_LAINNYA")
                    putLong("EXTRA_BASE_TIME", viewModel.getBaseTime())
                    putString("EXTRA_TIME_FILTER", "MONTHLY") 
                }
            }
            (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
        }
    }

    private fun renderReportUi(state: ReportUiState) {
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

        binding.tvBoxIncomeVal.text = "+${formatRupiah.format(state.incomeCurrent)}"
        binding.tvBoxExpenseVal.text = "-${formatRupiah.format(state.expenseCurrent)}"
        
        binding.chartIncome.setChartData(state.topIncomeValues, state.topIncomeColors)
        binding.chartExpense.setChartData(state.topExpenseValues, state.topExpenseColors)

        binding.tvHutangVal.text = "+${formatRupiah.format(state.totalHutang)}"
        binding.tvPiutangVal.text = "-${formatRupiah.format(state.totalPiutang)}"
        binding.tvLainnyaVal.text = formatRupiah.format(state.totalLainnya)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
