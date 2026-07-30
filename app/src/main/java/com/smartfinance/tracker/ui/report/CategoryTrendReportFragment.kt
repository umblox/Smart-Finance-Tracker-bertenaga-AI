package com.smartfinance.tracker.ui.report

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.R
import com.smartfinance.tracker.databinding.FragmentCategoryTrendBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class CategoryTrendReportFragment : Fragment() {

    private var _binding: FragmentCategoryTrendBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: CategoryTrendViewModel
    
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    private val chartColors = listOf(
        Color.parseColor("#14B8A6"), Color.parseColor("#F59E0B"), Color.parseColor("#3B82F6"),
        Color.parseColor("#EC4899"), Color.parseColor("#8B5CF6"), Color.parseColor("#10B981")
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return try {
            _binding = FragmentCategoryTrendBinding.inflate(inflater, container, false)
            binding.root
        } catch (e: Throwable) {
            ScrollView(requireContext()).apply {
                addView(TextView(requireContext()).apply {
                    text = "🔥 CRASH XML CATEGORY TREND:\n\n${e.stackTraceToString()}"
                    setTextColor(Color.RED)
                    setPadding(40, 40, 40, 40)
                })
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        try {
            super.onViewCreated(view, savedInstanceState)
            if (_binding == null) return

            viewModel = ViewModelProvider(this)[CategoryTrendViewModel::class.java]

            val targetMode = arguments?.getString("EXTRA_TARGET_MODE") ?: "ALL_EXPENSE" 
            val baseTime = arguments?.getLong("EXTRA_BASE_TIME") ?: System.currentTimeMillis()

            viewModel.loadData(targetMode, baseTime)

            binding.btnBack.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }
            
            binding.btnDropdownCategory.setOnClickListener {
                val state = viewModel.uiState.value
                val options = mutableListOf<String>()
                
                options.add(if (state.isExpenseMode) "ALL_EXPENSE" else "ALL_INCOME")
                val categoryNames = state.breakdownItems.map { it.label }
                options.addAll(categoryNames)

                val displayOptions = options.map { 
                    when (it) {
                        "ALL_EXPENSE" -> "Semua Pengeluaran (Utama)"
                        "ALL_INCOME" -> "Semua Pendapatan (Utama)"
                        else -> it
                    }
                }.toTypedArray()

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Pilih Kategori Laporan")
                    .setItems(displayOptions) { _, which ->
                        val selectedTarget = options[which]
                        viewModel.loadData(selectedTarget, baseTime) 
                    }
                    .show()
            }

            binding.toggleReportMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) renderVisualMode(checkedId == R.id.btnModeBreakdown)
            }

            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.uiState.collect { state -> 
                    try {
                        renderUi(state) 
                    } catch (e: Throwable) {
                        Toast.makeText(requireContext(), "Crash Render Trend: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Throwable) {
            Toast.makeText(requireContext(), "Crash Logika Trend: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderUi(state: CategoryTrendUiState) {
        val prefix = if (state.isExpenseMode) "-" else "+"
        val themeColor = ContextCompat.getColor(requireContext(), if (state.isExpenseMode) R.color.expense_red else R.color.income_green)
        
        binding.tvHeaderTitle.text = state.targetName
        binding.tvTotalAmount.text = "$prefix${formatRupiah.format(state.totalAmount)}"
        binding.tvTotalAmount.setTextColor(themeColor)
        
        binding.tvDailyAvg.text = "$prefix${formatRupiah.format(state.dailyAverage)}"
        binding.tvDailyAvg.setTextColor(themeColor)

        binding.chartBreakdownDonut.setChartData(state.donutValues, chartColors)
        binding.chartTrendBar.setChartData(state.trendBarValues, state.isExpenseMode)

        val isBreakdown = binding.toggleReportMode.checkedButtonId == R.id.btnModeBreakdown
        renderVisualMode(isBreakdown)
    }

    private fun renderVisualMode(isBreakdown: Boolean) {
        val state = viewModel.uiState.value
        if (isBreakdown) {
            binding.chartBreakdownDonut.visibility = View.VISIBLE
            binding.chartTrendBar.visibility = View.GONE
            renderList(state.breakdownItems, state.isExpenseMode, showPercentage = true, isBreakdown = true)
        } else {
            binding.chartBreakdownDonut.visibility = View.GONE
            binding.chartTrendBar.visibility = View.VISIBLE
            renderList(state.trendItems, state.isExpenseMode, showPercentage = false, isBreakdown = false)
        }
    }

    private fun renderList(items: List<TrendItem>, isExpense: Boolean, showPercentage: Boolean, isBreakdown: Boolean) {
        binding.listContainer.removeAllViews()
        val density = requireContext().resources.displayMetrics.density
        val prefix = if (isExpense) "-" else "+"
        val colorMain = ContextCompat.getColor(requireContext(), if (isExpense) R.color.expense_red else R.color.income_green)
        
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
        
        items.forEachIndexed { index, item ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (16 * density).toInt(), 0, (16 * density).toInt())
                setBackgroundResource(typedValue.resourceId)
                isClickable = true
                
                setOnClickListener {
                    val state = viewModel.uiState.value
                    val baseTime = arguments?.getLong("EXTRA_BASE_TIME") ?: System.currentTimeMillis()
                    
                    if (isBreakdown) {
                        val fragment = CategoryTrendReportFragment().apply {
                            arguments = Bundle().apply {
                                putString("EXTRA_TARGET_MODE", item.label)
                                putLong("EXTRA_BASE_TIME", baseTime)
                            }
                        }
                        (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
                        
                    } else {
                        // 🔥 FIX: Arahkan ke CategoryAnalyticsFragment dengan parameter rentang tanggal (EXTRA_DAY_RANGE)
                        if (state.targetName == "Rincian Biaya" || state.targetName == "Rincian Pendapatan") {
                            Toast.makeText(context, "Silakan pilih spesifik Kategori di tab Breakdown terlebih dahulu.", Toast.LENGTH_SHORT).show()
                        } else {
                            val fragment = CategoryAnalyticsFragment().apply {
                                arguments = Bundle().apply {
                                    putString("EXTRA_CATEGORY_NAME", state.targetName)
                                    putLong("EXTRA_BASE_TIME", baseTime)
                                    putString("EXTRA_DAY_RANGE", item.label)
                                }
                            }
                            (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
                        }
                    }
                }
            }
            
            if (showPercentage) {
                val colorDot = chartColors.getOrElse(index) { ContextCompat.getColor(requireContext(), R.color.text_secondary) }
                val dot = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams((12 * density).toInt(), (12 * density).toInt()).apply { rightMargin = (16 * density).toInt() }
                    background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(colorDot) }
                }
                row.addView(dot)
            }

            val tvTitle = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = item.label
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                textSize = 15f
            }
            row.addView(tvTitle)

            if (showPercentage) {
                val tvPct = TextView(requireContext()).apply {
                    text = "${item.percentage}%"
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    textSize = 14f; setPadding(0, 0, (12 * density).toInt(), 0)
                }
                row.addView(tvPct)
            }

            val tvAmt = TextView(requireContext()).apply {
                text = "$prefix${formatRupiah.format(item.amount)}"
                setTextColor(colorMain)
                setTypeface(null, Typeface.BOLD); textSize = 15f
            }
            row.addView(tvAmt)

            val tvArrow = TextView(requireContext()).apply {
                text = " >"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                textSize = 16f
            }
            row.addView(tvArrow)

            binding.listContainer.addView(row)
            binding.listContainer.addView(View(requireContext()).apply { setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider_color)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()) })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
