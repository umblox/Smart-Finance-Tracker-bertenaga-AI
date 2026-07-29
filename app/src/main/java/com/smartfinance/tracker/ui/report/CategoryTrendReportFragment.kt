package com.smartfinance.tracker.ui.report

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
        _binding = FragmentCategoryTrendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[CategoryTrendViewModel::class.java]

        val targetMode = arguments?.getString("EXTRA_TARGET_MODE") ?: "ALL_EXPENSE" // ALL_EXPENSE, ALL_INCOME, atau "Nama Kategori"
        val baseTime = arguments?.getLong("EXTRA_BASE_TIME") ?: System.currentTimeMillis()

        viewModel.loadData(targetMode, baseTime)

        binding.btnBack.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }
        
        binding.btnDropdownCategory.setOnClickListener {
            // [Fase 2.5] Panggil CategoryPickerDialog di sini!
            Toast.makeText(context, "Membuka Pemilih Kategori...", Toast.LENGTH_SHORT).show()
        }

        binding.toggleReportMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) renderVisualMode(checkedId == R.id.btnModeBreakdown)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state -> renderUi(state) }
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

        // Injeksi Data ke Grafik
        binding.chartBreakdownDonut.setChartData(state.donutValues, chartColors)
        binding.chartTrendBar.setChartData(state.trendBarValues, state.isExpenseMode)

        // Render List secara default (mengikuti tab aktif)
        val isBreakdown = binding.toggleReportMode.checkedButtonId == R.id.btnModeBreakdown
        renderVisualMode(isBreakdown)
    }

    private fun renderVisualMode(isBreakdown: Boolean) {
        val state = viewModel.uiState.value
        val density = requireContext().resources.displayMetrics.density
        
        if (isBreakdown) {
            binding.chartBreakdownDonut.visibility = View.VISIBLE
            binding.chartTrendBar.visibility = View.GONE
            renderList(state.breakdownItems, state.isExpenseMode, showPercentage = true)
        } else {
            binding.chartBreakdownDonut.visibility = View.GONE
            binding.chartTrendBar.visibility = View.VISIBLE
            renderList(state.trendItems, state.isExpenseMode, showPercentage = false)
        }
    }

    private fun renderList(items: List<TrendItem>, isExpense: Boolean, showPercentage: Boolean) {
        binding.listContainer.removeAllViews()
        val density = requireContext().resources.displayMetrics.density
        val prefix = if (isExpense) "-" else "+"
        val colorMain = ContextCompat.getColor(requireContext(), if (isExpense) R.color.expense_red else R.color.income_green)
        
        items.forEachIndexed { index, item ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (16 * density).toInt(), 0, (16 * density).toInt())
                background = ContextCompat.getDrawable(requireContext(), android.R.attr.selectableItemBackground)
                isClickable = true
                setOnClickListener {
                    // [Fase 3] Lanjut ke HistoryTransactionFragment saat diklik!
                    Toast.makeText(context, "Membedah: ${item.label}", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Warna Lingkaran (Jika Breakdown)
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
