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
    private var isBreakdownTabActive = true 

    private val chartColors = listOf(
        Color.parseColor("#14B8A6"), Color.parseColor("#F59E0B"), Color.parseColor("#3B82F6"),
        Color.parseColor("#EC4899"), Color.parseColor("#8B5CF6"), Color.parseColor("#10B981"),
        Color.parseColor("#EF4444"), Color.parseColor("#84CC16"), Color.parseColor("#06B6D4")
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return try {
            _binding = FragmentCategoryTrendBinding.inflate(inflater, container, false)
            binding.root
        } catch (e: Throwable) {
            ScrollView(requireContext()).apply { addView(TextView(requireContext()).apply { text = "🔥 CRASH XML: \n${e.stackTraceToString()}"; setTextColor(Color.RED) }) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (_binding == null) return

        viewModel = ViewModelProvider(this)[CategoryTrendViewModel::class.java]

        val targetMode = arguments?.getString("EXTRA_TARGET_MODE") ?: "ALL_EXPENSE" 
        val targetType = arguments?.getString("EXTRA_TARGET_TYPE") ?: "GLOBAL"
        val parentCat = arguments?.getString("EXTRA_PARENT_CATEGORY") ?: ""
        val baseTime = arguments?.getLong("EXTRA_BASE_TIME") ?: System.currentTimeMillis()

        if (targetType == "NOTE") isBreakdownTabActive = false

        viewModel.loadData(targetMode, targetType, baseTime, parentCat)

        binding.btnBack.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }
        binding.btnBackReport.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }
        
        binding.btnDropdownCategory.setOnClickListener { showCategoryDropdown(viewModel.uiState.value) }
        binding.btnDropdownCategoryReport.setOnClickListener { showCategoryDropdown(viewModel.uiState.value) }

        binding.btnToggleAvgVisibility.setOnClickListener { viewModel.toggleAvgVisibility() }
        
        binding.btnTabBreakdown.setOnClickListener { isBreakdownTabActive = true; updatePillUI(); renderVisualMode() }
        binding.btnTabTrend.setOnClickListener { isBreakdownTabActive = false; updatePillUI(); renderVisualMode() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state -> renderUi(state) }
        }
    }
    
    private fun showCategoryDropdown(state: CategoryTrendUiState?) {
        if (state == null) return
        val options = state.availableCategories.toTypedArray()
        
        if (options.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.trend_no_other_category), Toast.LENGTH_SHORT).show()
            return
        }
        android.app.AlertDialog.Builder(requireContext())
            .setItems(options) { _, which ->
                viewModel.loadData(options[which], "CATEGORY", state.selectedTimeMillis) 
            }
            .show()
    }

    private fun updatePillUI() {
        if (isBreakdownTabActive) {
            binding.btnTabBreakdown.setBackgroundResource(R.drawable.bg_pill_active)
            binding.btnTabBreakdown.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            binding.btnTabTrend.setBackgroundColor(Color.TRANSPARENT)
            binding.btnTabTrend.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        } else {
            binding.btnTabTrend.setBackgroundResource(R.drawable.bg_pill_active)
            binding.btnTabTrend.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            binding.btnTabBreakdown.setBackgroundColor(Color.TRANSPARENT)
            binding.btnTabBreakdown.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }
    }

    private fun renderUi(state: CategoryTrendUiState) {
        val prefix = if (state.isExpenseMode) "-" else "+"
        val themeColor = ContextCompat.getColor(requireContext(), if (state.isExpenseMode) R.color.expense_red else R.color.income_green)
        
        val isReportMenu = arguments?.getBoolean("EXTRA_FROM_REPORT_MENU", false) ?: false
        val allowDropdown = arguments?.getBoolean("EXTRA_SHOW_DROPDOWN", false) ?: false

        if (isReportMenu) {
            binding.headerModeGlobal.visibility = View.GONE
            binding.headerModeCategoryReport.visibility = View.VISIBLE
            binding.tvCategoryReportPillName.text = state.targetName
            binding.btnDropdownCategoryReport.isClickable = allowDropdown
        } else {
            binding.headerModeGlobal.visibility = View.VISIBLE
            binding.headerModeCategoryReport.visibility = View.GONE
            binding.tvHeaderTitle.text = state.targetName
            
            if (allowDropdown) {
                binding.iconDropdown.visibility = View.VISIBLE
                binding.btnDropdownCategory.isClickable = true
            } else {
                binding.iconDropdown.visibility = View.GONE
                binding.btnDropdownCategory.isClickable = false
            }
        }

        binding.tvTotalAmount.text = "$prefix${formatRupiah.format(state.totalAmount)}"
        binding.tvTotalAmount.setTextColor(themeColor)
        binding.tvDailyAvg.text = "$prefix${formatRupiah.format(state.dailyAverage)}"
        binding.tvDailyAvg.setTextColor(themeColor)

        renderTimeNav(state)

        if (state.isAvgVisible) {
            binding.btnToggleAvgVisibility.text = getString(R.string.trend_hide_avg)
            val diffPrefix = if (state.diffFromAvg > 0) "+" else ""
            binding.tv3MonthAvgDiff.text = "$diffPrefix${formatRupiah.format(state.diffFromAvg)}"
            binding.tv3MonthAvgDiff.setTextColor(if (state.diffFromAvg > 0) ContextCompat.getColor(requireContext(), R.color.expense_red) else ContextCompat.getColor(requireContext(), R.color.income_green))
        } else {
            binding.btnToggleAvgVisibility.text = getString(R.string.trend_show_avg)
            binding.tv3MonthAvgDiff.text = "******"
            binding.tv3MonthAvgDiff.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }

        binding.chartBreakdownDonut.setChartData(state.donutValues, chartColors)
        binding.chartTrendBar.setChartData(state.trendBarValues, state.isExpenseMode)

        renderVisualMode()
    }

    private fun renderTimeNav(state: CategoryTrendUiState) {
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
                
                setOnClickListener { viewModel.loadData(state.targetMode, state.targetType, navItem.timeMillis, state.parentCategory) }
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

    private fun renderVisualMode() {
        val state = viewModel.uiState.value 
        
        if (state.targetType == "NOTE") {
            binding.cardPillToggle.visibility = View.GONE
            binding.chartBreakdownDonut.visibility = View.GONE
            binding.chartTrendBar.visibility = View.VISIBLE
            renderList(state.trendItems, state.isExpenseMode, showPercentage = false, isBreakdown = false)
        } else {
            binding.cardPillToggle.visibility = View.VISIBLE
            if (isBreakdownTabActive) {
                binding.chartBreakdownDonut.visibility = View.VISIBLE
                binding.chartTrendBar.visibility = View.GONE
                renderList(state.breakdownItems, state.isExpenseMode, showPercentage = true, isBreakdown = true)
            } else {
                binding.chartBreakdownDonut.visibility = View.GONE
                binding.chartTrendBar.visibility = View.VISIBLE
                renderList(state.trendItems, state.isExpenseMode, showPercentage = false, isBreakdown = false)
            }
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
                setBackgroundResource(typedValue.resourceId); isClickable = true
                
                setOnClickListener {
                    val state = viewModel.uiState.value
                    
                    val isReportMenu = arguments?.getBoolean("EXTRA_FROM_REPORT_MENU", false) ?: false
                    val allowDropdown = arguments?.getBoolean("EXTRA_SHOW_DROPDOWN", false) ?: false
                    
                    if (isBreakdown) {
                        if (state.targetType == "GLOBAL") {
                            val fragment = CategoryTrendReportFragment().apply {
                                arguments = Bundle().apply { 
                                    putString("EXTRA_TARGET_MODE", item.label)
                                    putString("EXTRA_TARGET_TYPE", "CATEGORY")
                                    putLong("EXTRA_BASE_TIME", state.selectedTimeMillis) 
                                    putBoolean("EXTRA_SHOW_DROPDOWN", allowDropdown)
                                    putBoolean("EXTRA_FROM_REPORT_MENU", isReportMenu)
                                }
                            }
                            (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
                        } else if (state.targetType == "CATEGORY") {
                            val fragment = CategoryTrendReportFragment().apply {
                                arguments = Bundle().apply { 
                                    putString("EXTRA_TARGET_MODE", item.label)
                                    putString("EXTRA_TARGET_TYPE", "NOTE")
                                    putString("EXTRA_PARENT_CATEGORY", state.targetMode)
                                    putLong("EXTRA_BASE_TIME", state.selectedTimeMillis) 
                                    putBoolean("EXTRA_SHOW_DROPDOWN", false) 
                                    putBoolean("EXTRA_FROM_REPORT_MENU", isReportMenu)
                                }
                            }
                            (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
                        }
                    } else {
                        val fragment = CategoryAnalyticsFragment().apply {
                            arguments = Bundle().apply {
                                putString("EXTRA_CATEGORY_NAME", if (state.targetType == "CATEGORY") state.targetMode else state.parentCategory)
                                putString("EXTRA_NOTE_FILTER", if (state.targetType == "NOTE") state.targetMode else null) 
                                putLong("EXTRA_BASE_TIME", state.selectedTimeMillis)
                                putString("EXTRA_DAY_RANGE", item.label)
                            }
                        }
                        (activity as? com.smartfinance.tracker.MainActivity)?.navigateToSpecificFragment(fragment)
                    }
                }
            }
            
            if (showPercentage) {
                val colorDot = chartColors.getOrElse(index) { ContextCompat.getColor(requireContext(), R.color.text_secondary) }
                val dot = View(requireContext()).apply { layoutParams = LinearLayout.LayoutParams((12 * density).toInt(), (12 * density).toInt()).apply { rightMargin = (16 * density).toInt() }; background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(colorDot) } }
                row.addView(dot)
            }

            val tvTitle = TextView(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); text = item.label; setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary)); textSize = 15f }
            row.addView(tvTitle)

            if (showPercentage) {
                val tvPct = TextView(requireContext()).apply { text = "${item.percentage}%"; setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary)); textSize = 14f; setPadding(0, 0, (12 * density).toInt(), 0) }
                row.addView(tvPct)
            }

            val tvAmt = TextView(requireContext()).apply { text = "$prefix${formatRupiah.format(item.amount)}"; setTextColor(colorMain); setTypeface(null, Typeface.BOLD); textSize = 15f }
            row.addView(tvAmt)

            val tvArrow = TextView(requireContext()).apply { text = " >"; setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary)); textSize = 16f }
            row.addView(tvArrow)

            binding.listContainer.addView(row)
            binding.listContainer.addView(View(requireContext()).apply { setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider_color)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()) })
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
