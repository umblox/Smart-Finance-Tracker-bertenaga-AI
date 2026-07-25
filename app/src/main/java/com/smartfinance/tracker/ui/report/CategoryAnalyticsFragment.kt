package com.smartfinance.tracker.ui.report

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
import com.smartfinance.tracker.databinding.FragmentCategoryAnalyticsBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class CategoryAnalyticsFragment : Fragment() {

    private var _binding: FragmentCategoryAnalyticsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: CategoryAnalyticsViewModel
    
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val sdfDateTime = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

    private fun getThemeColor(resId: Int): Int = ContextCompat.getColor(requireContext(), resId)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[CategoryAnalyticsViewModel::class.java]

        // 1. Ambil Paket Data dari Layar Sebelumnya
        val targetCategory = arguments?.getString("EXTRA_CATEGORY_NAME") ?: "Kategori"
        val timeFilterString = arguments?.getString("EXTRA_TIME_FILTER") ?: "MONTHLY"
        val baseTime = arguments?.getLong("EXTRA_BASE_TIME") ?: System.currentTimeMillis()

        binding.tvCategoryTitle.text = targetCategory
        binding.btnBack.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }

        // 2. Perintahkan Otak untuk Mulai Memfilter
        viewModel.loadCategoryData(targetCategory, timeFilterString, baseTime)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state -> renderUi(state) }
        }
    }

    private fun renderUi(state: CategoryAnalyticsUiState) {
        val density = requireContext().resources.displayMetrics.density
        
        binding.tvTimePeriod.text = "Periode Analitik: ${state.timeLabel}"
        binding.tvTotalAmount.text = formatRupiah.format(state.totalSpent)
        binding.transactionListContainer.removeAllViews()

        if (state.isEmpty) {
            binding.transactionListContainer.addView(TextView(requireContext()).apply {
                text = "Tidak ada transaksi untuk kategori ini."
                setTextColor(getThemeColor(R.color.text_secondary)); textSize = 14f; textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(0, (16*density).toInt(), 0, (16*density).toInt())
            })
            return
        }

        state.transactions.forEach { tx ->
            val txRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
            }

            val txInfo = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
            txInfo.addView(TextView(requireContext()).apply { text = tx.note.ifEmpty { "Tanpa Catatan" }; setTextColor(getThemeColor(R.color.text_primary)); textSize = 14f })
            txInfo.addView(TextView(requireContext()).apply { text = sdfDateTime.format(java.util.Date(tx.timestamp)); setTextColor(getThemeColor(R.color.text_secondary)); textSize = 12f; setPadding(0, 4, 0, 0) })
            txRow.addView(txInfo)

            txRow.addView(TextView(requireContext()).apply { text = "-" + formatRupiah.format(tx.amount); setTextColor(getThemeColor(R.color.expense_red)); textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD) })

            binding.transactionListContainer.addView(txRow)
            binding.transactionListContainer.addView(View(requireContext()).apply { setBackgroundColor(getThemeColor(R.color.divider_color)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()) })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
