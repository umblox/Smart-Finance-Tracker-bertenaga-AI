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
        _binding = FragmentNetIncomeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[NetIncomeDetailViewModel::class.java]

        val baseTime = arguments?.getLong("EXTRA_BASE_TIME") ?: System.currentTimeMillis()
        viewModel.loadData(baseTime)

        binding.btnBack.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state -> renderUi(state) }
        }
    }

    private fun renderUi(state: NetIncomeDetailUiState) {
        val density = requireContext().resources.displayMetrics.density
        
        binding.tvNetIncomeTotal.text = formatRupiah.format(state.totalNetIncome)
        
        // Injeksi data ke grafik batang
        val incomes = state.chunks.map { it.income.toFloat() }
        val expenses = state.chunks.map { it.expense.toFloat() }
        binding.chartNetIncome.setChartData(incomes, expenses)

        // Render Label di bawah grafik
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

        // Render List Rincian (List ke bawah)
        binding.listRangesContainer.removeAllViews()
        state.chunks.forEach { chunk ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (16 * density).toInt(), 0, (16 * density).toInt())
                background = ContextCompat.getDrawable(requireContext(), android.R.attr.selectableItemBackground)
                isClickable = true
                setOnClickListener {
                    // [Tindak Lanjut Fase 5] Navigasi ke HistoryTransactionFragment
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
