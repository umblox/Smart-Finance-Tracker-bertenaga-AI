package com.smartfinance.tracker.ui.report

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
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
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    private fun getThemeColor(resId: Int): Int = ContextCompat.getColor(requireContext(), resId)

    // 🔥 Helper Navigasi Aman Anti-Crash
    private fun safeNavigate(targetFragment: Fragment) {
        try {
            (requireActivity() as com.smartfinance.tracker.MainActivity).navigateToSpecificFragment(targetFragment)
        } catch (e: Exception) {
            val container = requireView().parent as? ViewGroup
            container?.id?.let { containerId ->
                parentFragmentManager.beginTransaction()
                    .replace(containerId, targetFragment)
                    .addToBackStack(null)
                    .commitAllowingStateLoss()
            }
        }
    }

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                renderReportUi(state)
            }
        }
    }

    private fun renderReportUi(state: ReportUiState) {
        val density = requireContext().resources.displayMetrics.density

        binding.tvReportIncome.text = "Pemasukan (${state.filterLabel}): ${formatRupiah.format(state.incomeCurrent)}"
        binding.tvReportExpense.text = "Pengeluaran (${state.filterLabel}): ${formatRupiah.format(state.expenseCurrent)}"
        binding.tvReportNet.text = "Sisa Bersih: ${formatRupiah.format(state.netBalance)}"
        binding.tvReportNet.setTextColor(if (state.netBalance >= 0) getThemeColor(R.color.income_green) else getThemeColor(R.color.expense_red))

        binding.chartContainer.removeAllViews()
        val barView = QuadVerticalBarChartView(
            requireContext(),
            state.incomePrevious.toFloat(), state.incomeCurrent.toFloat(),
            state.expensePrevious.toFloat(), state.expenseCurrent.toFloat()
        )
        binding.chartContainer.addView(barView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (160 * density).toInt()))

        // 🔥 FIX Navigasi 1
        binding.btnSeeAllDetails.setOnClickListener {
            safeNavigate(DetailCategoryReportFragment())
        }

        binding.topBorosContainer.removeAllViews()
        if (!state.hasData || state.topExpenses.isEmpty()) {
            binding.topBorosContainer.addView(TextView(requireContext()).apply { 
                text = "Belum ada pengeluaran pada ${state.filterLabel.lowercase()}."
                setTextColor(getThemeColor(R.color.text_secondary)); textSize = 14f; textAlignment = View.TEXT_ALIGNMENT_CENTER 
            })
        } else {
            state.topExpenses.forEach { (catName, amt) ->
                val pct = if (state.topExpensesTotal > 0) ((amt / state.topExpensesTotal) * 100).toInt() else 0
                val rowLayout = LinearLayout(requireContext()).apply { 
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                    setBackgroundResource(android.R.attr.selectableItemBackground)
                    isClickable = true; isFocusable = true
                    
                    // 🔥 FIX Navigasi 2
                    setOnClickListener {
                        val fragmentBaru = CategoryAnalyticsFragment().apply {
                            arguments = Bundle().apply {
                                putString("EXTRA_CATEGORY_NAME", catName)
                                putString("EXTRA_TIME_FILTER", viewModel.getCurrentFilter().name)
                                putLong("EXTRA_BASE_TIME", viewModel.getBaseTime())
                            }
                        }
                        safeNavigate(fragmentBaru)
                    }
                }
                
                val centerInfo = LinearLayout(requireContext()).apply { 
                    orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                centerInfo.addView(TextView(requireContext()).apply { text = catName; setTextColor(getThemeColor(R.color.text_primary)); setTypeface(null, Typeface.BOLD); textSize = 14f })
                centerInfo.addView(TextView(requireContext()).apply { text = formatRupiah.format(amt); setTextColor(getThemeColor(R.color.text_secondary)); textSize = 12f; setPadding(0, 2, 0, 0) })
                
                rowLayout.addView(centerInfo)
                rowLayout.addView(TextView(requireContext()).apply { 
                    text = "$pct%"
                    setTextColor(getThemeColor(R.color.expense_red))
                    setTypeface(null, Typeface.BOLD); textSize = 14f
                })
                binding.topBorosContainer.addView(rowLayout)
                binding.topBorosContainer.addView(View(requireContext()).apply { 
                    setBackgroundColor(getThemeColor(R.color.divider_color))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()) 
                })
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
