package com.smartfinance.tracker.ui.report

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.databinding.FragmentDetailCategoryReportBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetailCategoryReportFragment : Fragment() {

    private var _binding: FragmentDetailCategoryReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DetailCategoryViewModel
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val sdfDateTime = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailCategoryReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[DetailCategoryViewModel::class.java]

        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val activeTimePrefs = prefs.getLong("active_report_time", System.currentTimeMillis())

        // Inisialisasi bulan dari SharedPreferences agar sinkron dengan Dashboard
        viewModel.initializeTime(activeTimePrefs)

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnPrevMonth.setOnClickListener { viewModel.changeMonth(-1) }
        binding.btnNextMonth.setOnClickListener { viewModel.changeMonth(1) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                renderUi(state)
            }
        }
    }

    private fun renderUi(state: DetailCategoryUiState) {
        val density = requireContext().resources.displayMetrics.density

        binding.tvMonthLabel.text = state.currentMonthLabel
        
        // Simpan state waktu terakhir agar saat pindah tab/halaman, bulannya tidak reset
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("active_report_time", state.activeTimeMillis).apply()

        binding.containerHierarchy.removeAllViews()

        if (state.isEmpty) {
            binding.containerHierarchy.addView(TextView(requireContext()).apply {
                text = "Tidak ada catatan pengeluaran pada bulan ini."
                textSize = 14f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
                setPadding(0, (40 * density).toInt(), 0, 0)
            })
            return
        }

        state.groupedTransactions.forEach { (categoryName, txList) ->
            val totalCategoryAmount = txList.sumOf { it.amount }
            val percentage = if (state.totalExpense > 0) ((totalCategoryAmount / state.totalExpense) * 100).toInt() else 0

            val card = MaterialCardView(requireContext()).apply {
                radius = 14 * density; cardElevation = 1f * density; strokeWidth = 0
                setCardBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() }
            }

            val masterLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            
            // Baris Induk Kategori (Bisa di-klik)
            val rowHeader = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
                setBackgroundColor(Color.parseColor("#FFFFFF"))
            }

            val iconFrame = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#F1F5F9")) }
                addView(TextView(requireContext()).apply { text = "📁"; textSize = 15f; gravity = Gravity.CENTER })
            }
            rowHeader.addView(iconFrame)

            val infoLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
            infoLayout.addView(TextView(requireContext()).apply { text = categoryName; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD); textSize = 14.5f })
            infoLayout.addView(TextView(requireContext()).apply { text = "${txList.size} Transaksi • ${formatRupiah.format(totalCategoryAmount)}"; setTextColor(Color.parseColor("#64748B")); textSize = 11.5f; setPadding(0, 2, 0, 0) })
            rowHeader.addView(infoLayout)

            val tvPercent = TextView(requireContext()).apply { text = "$percentage%"; setTextColor(Color.parseColor("#F43F5E")); setTypeface(null, Typeface.BOLD); textSize = 14.5f }
            rowHeader.addView(tvPercent)
            masterLayout.addView(rowHeader)

            // Container Anak (Detail Transaksi) - Awalnya Tersembunyi
            val childContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL; visibility = View.GONE
                setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (12 * density).toInt())
                setBackgroundColor(Color.parseColor("#FAFAFA"))
            }

            val sortedTxList = txList.sortedByDescending { it.timestamp }
            sortedTxList.forEach { tx ->
                val txRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                }

                val txInfo = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                txInfo.addView(TextView(requireContext()).apply { text = tx.note.ifEmpty { "Tanpa Catatan" }; setTextColor(Color.parseColor("#334155")); textSize = 13.5f })
                txInfo.addView(TextView(requireContext()).apply { text = sdfDateTime.format(Date(tx.timestamp)); setTextColor(Color.parseColor("#94A3B8")); textSize = 10.5f; setPadding(0, 2, 0, 0) })
                txRow.addView(txInfo)

                val tvAmt = TextView(requireContext()).apply { text = "-" + formatRupiah.format(tx.amount); setTextColor(Color.parseColor("#64748B")); textSize = 13.5f }
                txRow.addView(tvAmt)

                childContainer.addView(txRow)
                childContainer.addView(View(requireContext()).apply { setBackgroundColor(Color.parseColor("#F1F5F9")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (0.5f * density).toInt()) })
            }
            masterLayout.addView(childContainer)

            // Logika Expand/Collapse (Accordion)
            rowHeader.setOnClickListener {
                if (childContainer.visibility == View.GONE) {
                    childContainer.visibility = View.VISIBLE
                } else {
                    childContainer.visibility = View.GONE
                }
            }

            card.addView(masterLayout)
            binding.containerHierarchy.addView(card)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
