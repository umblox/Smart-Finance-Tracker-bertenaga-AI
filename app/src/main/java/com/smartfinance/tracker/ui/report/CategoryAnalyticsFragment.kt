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

        val targetCategory = arguments?.getString("EXTRA_CATEGORY_NAME") ?: "Kategori"
        val timeFilterString = arguments?.getString("EXTRA_TIME_FILTER") ?: "MONTHLY"
        val baseTime = arguments?.getLong("EXTRA_BASE_TIME") ?: System.currentTimeMillis()
        
        // 🔥 FIX: Tangkap string rentang waktu yang dilempar dari Grafik (Misal: "27/07 - 31/07")
        val dayRange = arguments?.getString("EXTRA_DAY_RANGE")

        binding.tvCategoryTitle.text = if (targetCategory == "ALL_NET_INCOME") "Riwayat Transaksi" else targetCategory
        binding.btnBack.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }

        // Lempar semua parameter ke otak aplikasi
        viewModel.loadCategoryData(targetCategory, timeFilterString, baseTime, dayRange)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state -> renderUi(state) }
        }
    }

    private fun renderUi(state: CategoryAnalyticsUiState) {
        val density = requireContext().resources.displayMetrics.density
        
        binding.tvTimePeriod.text = "Periode: ${state.timeLabel}"
        
        // 🔥 FIX: Pewarnaan dinamis untuk Pemasukan (Hijau) & Pengeluaran (Merah) di Header
        val isIncomeTarget = state.categoryName == "Rincian Pendapatan" || (!state.isEmpty && state.transactions.first().type in listOf("INCOME", "DEBT"))
        
        if (state.categoryName == "ALL_NET_INCOME") {
            val netPrefix = if (state.totalSpent < 0) "-" else ""
            binding.tvTotalAmount.text = "$netPrefix${formatRupiah.format(Math.abs(state.totalSpent))}"
            binding.tvTotalAmount.setTextColor(getThemeColor(if (state.totalSpent < 0) R.color.expense_red else R.color.text_primary))
        } else {
            val typePrefix = if (isIncomeTarget) "+" else "-"
            val colorRes = if (isIncomeTarget) R.color.income_green else R.color.expense_red
            binding.tvTotalAmount.text = "$typePrefix${formatRupiah.format(Math.abs(state.totalSpent))}"
            binding.tvTotalAmount.setTextColor(getThemeColor(colorRes))
        }

        binding.transactionListContainer.removeAllViews()

        if (state.isEmpty) {
            binding.transactionListContainer.addView(TextView(requireContext()).apply {
                text = "Tidak ada transaksi untuk periode ini."
                setTextColor(getThemeColor(R.color.text_secondary)); textSize = 14f; textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(0, (16*density).toInt(), 0, (16*density).toInt())
            })
            return
        }

        // Render List Transaksi
        state.transactions.forEach { tx ->
            val isInc = tx.type == "INCOME" || tx.type == "DEBT"
            
            val txRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
            }

            val txInfo = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
            // Tampilkan Note, jika kosong tampilkan Kategori
            val titleText = if (tx.note.isEmpty()) tx.categoryName else tx.note
            txInfo.addView(TextView(requireContext()).apply { text = titleText; setTextColor(getThemeColor(R.color.text_primary)); textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD) })
            txInfo.addView(TextView(requireContext()).apply { text = sdfDateTime.format(java.util.Date(tx.timestamp)); setTextColor(getThemeColor(R.color.text_secondary)); textSize = 12f; setPadding(0, 4, 0, 0) })
            txRow.addView(txInfo)

            // 🔥 FIX: Tanda minus dan warna merah untuk pengeluaran, Tanda plus dan hijau untuk pemasukan
            txRow.addView(TextView(requireContext()).apply { 
                text = (if (isInc) "+" else "-") + formatRupiah.format(tx.amount)
                setTextColor(getThemeColor(if (isInc) R.color.income_green else R.color.expense_red))
                textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD) 
            })

            binding.transactionListContainer.addView(txRow)
            binding.transactionListContainer.addView(View(requireContext()).apply { setBackgroundColor(getThemeColor(R.color.divider_color)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()) })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
