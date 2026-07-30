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
import com.smartfinance.tracker.databinding.FragmentNetIncomeDetailBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class NetIncomeDetailFragment : Fragment() {

    private var _binding: FragmentNetIncomeDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: NetIncomeDetailViewModel
    
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return try {
            _binding = FragmentNetIncomeDetailBinding.inflate(inflater, container, false)
            binding.root
        } catch (e: Throwable) {
            ScrollView(requireContext()).apply {
                addView(TextView(requireContext()).apply {
                    text = "🔥 CRASH XML NET INCOME:\n\n${e.stackTraceToString()}"
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

            viewModel = ViewModelProvider(this)[NetIncomeDetailViewModel::class.java]

            val baseTime = arguments?.getLong("EXTRA_BASE_TIME") ?: System.currentTimeMillis()
            viewModel.loadData(baseTime)

            binding.btnBack.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }

            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.uiState.collect { state -> 
                    try {
                        renderUi(state) 
                    } catch (e: Throwable) {
                        Toast.makeText(requireContext(), "Crash Render Data: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Throwable) {
            Toast.makeText(requireContext(), "Crash Logika Net Income: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderUi(state: NetIncomeDetailUiState) {
        val density = requireContext().resources.displayMetrics.density
        
        binding.tvNetIncomeTotal.text = formatRupiah.format(state.totalNetIncome)
        
        val incomes = state.chunks.map { it.income.toFloat() }
        val expenses = state.chunks.map { it.expense.toFloat() }
        binding.chartNetIncome.setChartData(incomes, expenses)

        binding.layoutChartLabels.removeAllViews()
        state.chunks.forEach { chunk ->
            val tvLabel = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = chunk.label
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            }
            binding.layoutChartLabels.addView(tvLabel)
        }

        binding.listRangesContainer.removeAllViews()
        
        // Memuat efek Ripple dengan cara yang BENAR
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)

        state.chunks.forEach { chunk ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (16 * density).toInt(), 0, (16 * density).toInt())
                
                // 🔥 FIX: Menggunakan setBackgroundResource untuk efek Ripple
                setBackgroundResource(typedValue.resourceId)
                
                isClickable = true
                setOnClickListener {
                    Toast.makeText(context, "Membuka riwayat: ${chunk.label}", Toast.LENGTH_SHORT).show()
                }
            }

            val tvDateRange = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = chunk.label
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                textSize = 14f
            }
            row.addView(tvDateRange)

            val rightLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }

            val tvInc = TextView(requireContext()).apply { text = "+${formatRupiah.format(chunk.income)}"; setTextColor(Color.parseColor("#38BDF8")); textSize = 13f; setTypeface(null, Typeface.BOLD) }
            val tvExp = TextView(requireContext()).apply { text = "-${formatRupiah.format(chunk.expense)}"; setTextColor(Color.parseColor("#F43F5E")); textSize = 13f; setTypeface(null, Typeface.BOLD) }
            val tvNet = TextView(requireContext()).apply { text = formatRupiah.format(chunk.net); setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary)); textSize = 14f; setTypeface(null, Typeface.BOLD) }
            
            rightLayout.addView(tvInc)
            rightLayout.addView(tvExp)
            rightLayout.addView(tvNet)
            
            row.addView(rightLayout)

            binding.listRangesContainer.addView(row)
            binding.listRangesContainer.addView(View(requireContext()).apply { setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider_color)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()) })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
