package com.smartfinance.tracker.ui.debt

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.R
import com.smartfinance.tracker.databinding.FragmentAddDebtBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.HashMap
import java.util.Locale

class AddDebtFragment : Fragment() {

    private var _binding: FragmentAddDebtBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DebtViewModel
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    private var currentAdapterTab = ""
    private var debtAdapter: DebtAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddDebtBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[DebtViewModel::class.java]

        binding.btnPrevMonth.setOnClickListener { viewModel.changeMonth(-1) }
        binding.btnNextMonth.setOnClickListener { viewModel.changeMonth(1) }

        binding.btnTabDebt.setOnClickListener { viewModel.changeTab("DEBT") }
        binding.btnTabReceivable.setOnClickListener { viewModel.changeTab("RECEIVABLE") }

        binding.fabAddDebt.setOnClickListener {
            DebtManualDialog(viewModel.uiState.value.currentTab) {
            }.show(parentFragmentManager, "DebtManualDialog")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                renderUi(state)
            }
        }
    }

    private fun renderUi(state: DebtUiState) {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val colorSurface = ContextCompat.getColor(context, R.color.surface_white)
        val colorTextSecondary = ContextCompat.getColor(context, R.color.text_secondary)
        val colorPrimary = ContextCompat.getColor(context, R.color.primary)

        // 1. Update Label & Header
        binding.tvMonthLabel.text = state.currentMonthLabel
        binding.tvTotalDebt.text = formatRupiah.format(state.totalActiveDebt)
        binding.tvTotalReceivable.text = formatRupiah.format(state.totalActiveReceivable)

        // Simpan waktu aktif laporan
        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("active_report_time", viewModel.getCurrentTimeInMillis()).apply()

        // 2. Styling Tab
        if (state.currentTab == "DEBT") {
            binding.btnTabDebt.apply { setTextColor(colorSurface); setTypeface(null, Typeface.BOLD); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 10 * density; setColor(colorPrimary) } }
            binding.btnTabReceivable.apply { setTextColor(colorTextSecondary); setTypeface(null, Typeface.NORMAL); background = null }
        } else {
            binding.btnTabReceivable.apply { setTextColor(colorSurface); setTypeface(null, Typeface.BOLD); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 10 * density; setColor(colorPrimary) } }
            binding.btnTabDebt.apply { setTextColor(colorTextSecondary); setTypeface(null, Typeface.NORMAL); background = null }
        }

        // 3. Render RecyclerView (Performa Super Cepat & Ringan!)
        if (state.displayedDebts.isEmpty()) {
            binding.rvDebts.visibility = View.GONE
            binding.tvEmptyState.visibility = View.VISIBLE
        } else {
            binding.rvDebts.visibility = View.VISIBLE
            binding.tvEmptyState.visibility = View.GONE

            // Buat ulang adapter HANYA jika tab berubah (agar warnanya sesuai: Orange/Biru)
            if (debtAdapter == null || currentAdapterTab != state.currentTab) {
                currentAdapterTab = state.currentTab
                
                debtAdapter = DebtAdapter(state.currentTab) { debt ->
                    val passMap = HashMap<String, Any>().apply {
                        put("id", debt.id)
                        put("contactName", debt.contactName)
                        put("amount", debt.amount)
                        put("remainingAmount", debt.remainingAmount)
                        put("type", debt.type)
                        put("timestamp", debt.timestamp)
                        put("isPaid", debt.isPaid)
                        put("note", debt.note)
                    }
                    DebtEditorDialog(passMap) { 
                    }.show(parentFragmentManager, "DebtEditorDialog")
                }
                binding.rvDebts.adapter = debtAdapter
            }
            
            // Masukkan data ke mesin pendaur ulang
            debtAdapter?.submitList(state.displayedDebts)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
