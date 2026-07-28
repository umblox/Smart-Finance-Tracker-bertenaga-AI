package com.smartfinance.tracker.ui.debt

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.R
import com.smartfinance.tracker.databinding.FragmentAddDebtBinding
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
    private val sdfDisplayPremium = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale("id", "ID"))

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

        // 🔥 FIX: Ambil warna dari tema (Bebas dari Kutukan Hardcode Hexadecimal)
        val colorSurface = ContextCompat.getColor(context, R.color.surface_white)
        val colorBackground = ContextCompat.getColor(context, R.color.background_color)
        val colorTextPrimary = ContextCompat.getColor(context, R.color.text_primary)
        val colorTextSecondary = ContextCompat.getColor(context, R.color.text_secondary)
        val colorDivider = ContextCompat.getColor(context, R.color.divider_color)
        val colorPrimary = ContextCompat.getColor(context, R.color.primary)
        
        // Peringatan: Pastikan R.color.debt_orange, receivable_blue, income_green ada di colors.xml Anda
        val colorDebt = ContextCompat.getColor(context, R.color.debt_orange)
        val colorReceivable = ContextCompat.getColor(context, R.color.receivable_blue)
        val colorIncome = ContextCompat.getColor(context, R.color.income_green)

        // Update Label
        binding.tvMonthLabel.text = state.currentMonthLabel
        binding.tvTotalDebt.text = formatRupiah.format(state.totalActiveDebt)
        binding.tvTotalReceivable.text = formatRupiah.format(state.totalActiveReceivable)

        // Simpan waktu aktif
        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("active_report_time", viewModel.getCurrentTimeInMillis()).apply()

        // Tab Styling (Aman untuk Dark Mode)
        if (state.currentTab == "DEBT") {
            binding.btnTabDebt.apply { setTextColor(colorSurface); setTypeface(null, Typeface.BOLD); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 10 * density; setColor(colorPrimary) } }
            binding.btnTabReceivable.apply { setTextColor(colorTextSecondary); setTypeface(null, Typeface.NORMAL); background = null }
        } else {
            binding.btnTabReceivable.apply { setTextColor(colorSurface); setTypeface(null, Typeface.BOLD); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 10 * density; setColor(colorPrimary) } }
            binding.btnTabDebt.apply { setTextColor(colorTextSecondary); setTypeface(null, Typeface.NORMAL); background = null }
        }

        binding.listContainer.removeAllViews()

        if (state.displayedDebts.isEmpty()) {
            binding.listContainer.addView(TextView(context).apply {
                text = "\nBelum ada catatan riwayat pinjaman/utang yang dibuat pada periode bulan ini."
                textSize = 13.5f; setTextColor(colorTextSecondary); gravity = Gravity.CENTER
                setTypeface(null, Typeface.ITALIC)
            })
            return
        }

        // Render List Dinamis
        state.displayedDebts.forEach { debt ->
            val paidAmount = debt.amount - debt.remainingAmount
            val progressPercent = if (debt.amount > 0) ((paidAmount / debt.amount) * 100).toInt() else 0
            
            val itemCard = MaterialCardView(context).apply {
                radius = 16f * density; cardElevation = 1.5f * density; strokeWidth = 0
                // 🔥 FIX: Background kartu menyesuaikan dengan lunas/belum menggunakan tema
                setCardBackgroundColor(if (debt.isPaid) colorBackground else colorSurface) 
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (12f * density).toInt() }
                
                setOnClickListener {
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
            }

            // Container Utama Card
            val masterContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16f * density).toInt(), (16f * density).toInt(), (16f * density).toInt(), (16f * density).toInt())
            }

            // Baris 1: Nama dan Status Lunas/Sisa
            val rowTop = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            
            val leftTop = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
            leftTop.addView(TextView(context).apply { text = debt.contactName; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(colorTextPrimary) })
            leftTop.addView(TextView(context).apply { text = "📅 ${sdfDisplayPremium.format(Date(debt.timestamp))}"; textSize = 11.5f; setTextColor(colorTextSecondary) })
            rowTop.addView(leftTop)

            if (debt.isPaid) {
                val badgeLunas = TextView(context).apply { 
                    text = "LUNAS ✅"
                    textSize = 12f; setTypeface(null, Typeface.BOLD)
                    setTextColor(colorSurface)
                    background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 20f * density; setColor(colorIncome) }
                    setPadding((10*density).toInt(), (4*density).toInt(), (10*density).toInt(), (4*density).toInt())
                }
                rowTop.addView(badgeLunas)
            } else {
                val rightTop = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
                rightTop.addView(TextView(context).apply { text = "SISA TAGIHAN"; textSize = 10f; setTypeface(null, Typeface.BOLD); setTextColor(colorTextSecondary) })
                rightTop.addView(TextView(context).apply {
                    text = formatRupiah.format(debt.remainingAmount)
                    textSize = 15f; setTypeface(null, Typeface.BOLD)
                    setTextColor(if (state.currentTab == "DEBT") colorDebt else colorReceivable)
                })
                rowTop.addView(rightTop)
            }
            masterContainer.addView(rowTop)

            // Garis Pembatas
            masterContainer.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1f * density).toInt()).apply { setMargins(0, (12*density).toInt(), 0, (12*density).toInt()) }
                setBackgroundColor(colorDivider)
            })

            // Baris 2: Total Pinjaman & Progress
            val rowBottom = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            
            val infoBottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
            infoBottom.addView(TextView(context).apply { text = "Total Pinjaman: ${formatRupiah.format(debt.amount)}"; textSize = 12f; setTextColor(colorTextPrimary); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            infoBottom.addView(TextView(context).apply { text = "$progressPercent% Terbayar"; textSize = 11f; setTextColor(colorTextSecondary) })
            rowBottom.addView(infoBottom)

            // Progress Bar
            val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (6f * density).toInt()).apply { topMargin = (6f*density).toInt() }
                progressDrawable = ContextCompat.getDrawable(context, com.google.android.material.R.drawable.design_snackbar_background) 
                progress = progressPercent
                max = 100
                progressTintList = android.content.res.ColorStateList.valueOf(if (state.currentTab == "DEBT") colorDebt else colorReceivable)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(colorDivider)
            }
            rowBottom.addView(progressBar)

            masterContainer.addView(rowBottom)

            itemCard.addView(masterContainer)
            binding.listContainer.addView(itemCard)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
