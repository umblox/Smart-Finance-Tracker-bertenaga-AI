package com.smartfinance.tracker.ui.debt

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.databinding.FragmentAddDebtBinding
import com.smartfinance.tracker.data.model.Debt
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashMap
import java.util.Locale

class AddDebtFragment : Fragment() {

    private var _binding: FragmentAddDebtBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DebtViewModel

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val sdfDisplayPremium = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddDebtBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[DebtViewModel::class.java]

        // Set Navigasi
        binding.btnPrevMonth.setOnClickListener { viewModel.changeMonth(-1) }
        binding.btnNextMonth.setOnClickListener { viewModel.changeMonth(1) }

        // Set Tab Filter
        binding.btnTabDebt.setOnClickListener { viewModel.changeTab("DEBT") }
        binding.btnTabReceivable.setOnClickListener { viewModel.changeTab("RECEIVABLE") }

        // Set Fab Tambah
        binding.fabAddDebt.setOnClickListener {
            // Kita pass state currentTab agar ManualDialog tahu sedang di tab mana
            DebtManualDialog(viewModel.uiState.value.currentTab) {
                // Biarkan kosong, repository di viewModel mendengarkan otomatis
            }.show(parentFragmentManager, "DebtManualDialog")
        }

        // Pantau Perubahan Data
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                renderUi(state)
            }
        }
    }

    private fun renderUi(state: DebtUiState) {
        val density = requireContext().resources.displayMetrics.density

        // 1. Update Teks Navigasi & Saldo
        binding.tvMonthLabel.text = state.currentMonthLabel
        binding.tvTotalDebt.text = formatRupiah.format(state.totalActiveDebt)
        binding.tvTotalReceivable.text = formatRupiah.format(state.totalActiveReceivable)

        // Simpan waktu aktif untuk sinkronisasi dengan Dashboard (sesuai logika asli)
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("active_report_time", viewModel.getCurrentTimeInMillis()).apply()

        // 2. Update Style Tab
        if (state.currentTab == "DEBT") {
            binding.btnTabDebt.apply { setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 10 * density; setColor(Color.parseColor("#1E293B")) } }
            binding.btnTabReceivable.apply { setTextColor(Color.parseColor("#64748B")); setTypeface(null, Typeface.NORMAL); background = null }
        } else {
            binding.btnTabReceivable.apply { setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 10 * density; setColor(Color.parseColor("#1E293B")) } }
            binding.btnTabDebt.apply { setTextColor(Color.parseColor("#64748B")); setTypeface(null, Typeface.NORMAL); background = null }
        }

        // 3. Render List
        binding.listContainer.removeAllViews()

        if (state.displayedDebts.isEmpty()) {
            binding.listContainer.addView(TextView(requireContext()).apply {
                text = "\nTidak ada catatan pinjaman pada periode bulan ini."
                textSize = 13.5f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
                setTypeface(null, Typeface.ITALIC)
            })
            return
        }

        state.displayedDebts.forEach { debt ->
            val itemCard = MaterialCardView(requireContext()).apply {
                radius = 14f * density; cardElevation = 1.5f * density; strokeWidth = 0
                setCardBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (10f * density).toInt() }
                
                setOnClickListener {
                    // Menerjemahkan Object Debt kembali ke HashMap untuk kompabilitas DebtEditorDialog lama
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
                        // Kosong karena ViewModel otomatis update
                    }.show(parentFragmentManager, "DebtEditorDialog")
                }
            }

            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding((16f * density).toInt(), (16f * density).toInt(), (16f * density).toInt(), (16f * density).toInt())
            }

            // Kiri: Info Nama dan Tanggal
            val leftLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
            leftLayout.addView(TextView(requireContext()).apply { text = debt.contactName; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#1E293B")) })
            leftLayout.addView(TextView(requireContext()).apply { text = "📅 ${sdfDisplayPremium.format(Date(debt.timestamp))}"; textSize = 11.5f; setTextColor(Color.parseColor("#94A3B8")); setPadding(0, (4f * density).toInt(), 0, 0) })
            leftLayout.addView(TextView(requireContext()).apply { text = "Total Pinjaman: ${formatRupiah.format(debt.amount)}"; textSize = 12f; setTextColor(Color.parseColor("#64748B")); setPadding(0, (2f * density).toInt(), 0, 0) })
            rowLayout.addView(leftLayout)

            // Kanan: Status Tagihan
            val rightLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
            if (debt.isPaid) {
                rightLayout.addView(TextView(requireContext()).apply { text = "LUNAS ✅"; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#10B981")) })
            } else {
                rightLayout.addView(TextView(requireContext()).apply { text = "Sisa Tagihan"; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")) })
                rightLayout.addView(TextView(requireContext()).apply {
                    text = formatRupiah.format(debt.remainingAmount)
                    textSize = 15f; setTypeface(null, Typeface.BOLD)
                    setTextColor(if (state.currentTab == "DEBT") Color.parseColor("#D97706") else Color.parseColor("#0284C7"))
                })
            }
            rowLayout.addView(rightLayout)
            
            itemCard.addView(rowLayout)
            binding.listContainer.addView(itemCard)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
