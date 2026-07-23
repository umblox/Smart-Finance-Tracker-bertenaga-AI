package com.smartfinance.tracker.ui.transaction

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
import com.smartfinance.tracker.databinding.FragmentHistoryTransactionBinding
import com.smartfinance.tracker.data.model.Transaction
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashMap
import java.util.Locale

class HistoryTransactionFragment : Fragment() {

    private var _binding: FragmentHistoryTransactionBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: HistoryViewModel

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val sdfPremiumClock = SimpleDateFormat("HH:mm 'WIB'", Locale("id", "ID"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[HistoryViewModel::class.java]

        binding.btnPrevMonth.setOnClickListener { viewModel.changeMonth(-1) }
        binding.btnNextMonth.setOnClickListener { viewModel.changeMonth(1) }

        binding.fabAddTransaction.setOnClickListener {
            TransactionManualDialog { 
                // Dibiarkan kosong karena Repository Firestore di ViewModel
                // secara reaktif mendeteksi perubahan data dan memicu UI update secara otomatis!
            }.show(parentFragmentManager, "ManualDialog")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                renderHistoryUi(state)
            }
        }
    }

    private fun renderHistoryUi(state: HistoryUiState) {
        val density = requireContext().resources.displayMetrics.density

        binding.tvMonthLabel.text = state.currentMonthLabel
        binding.transactionListContainer.removeAllViews()

        if (state.isEmpty) {
            binding.transactionListContainer.addView(TextView(requireContext()).apply {
                text = "\nBelum ada rekam jejak keuangan pada bulan ini."
                gravity = Gravity.CENTER; setTextColor(Color.parseColor("#94A3B8")); textSize = 13.5f
                setTypeface(null, Typeface.ITALIC)
            })
            return
        }

        state.groupedTransactions.forEach { (dateHeader, transactionsList) ->
            val dateCard = MaterialCardView(requireContext()).apply {
                radius = 14 * density; cardElevation = 2 * density; strokeWidth = 0
                setCardBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (14 * density).toInt() }
            }
            
            val cardLayout = LinearLayout(requireContext()).apply { 
                orientation = LinearLayout.VERTICAL; setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt()) 
            }

            cardLayout.addView(TextView(requireContext()).apply { text = dateHeader.uppercase(Locale.ROOT); textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#64748B")) })
            cardLayout.addView(View(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { topMargin = (10 * density).toInt(); bottomMargin = (4 * density).toInt() }; setBackgroundColor(Color.parseColor("#F1F5F9")) })

            transactionsList.forEach { tx ->
                val itemRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                    
                    setOnClickListener {
                        // AMAN: Konversi Data Class kembali ke format HashMap bawaan Anda
                        // agar TransactionEditorDialog lama tetap bekerja tanpa *crash*
                        val passMap = HashMap<String, Any>().apply {
                            put("id", tx.id)
                            put("amount", tx.amount)
                            put("type", tx.type)
                            put("timestamp", tx.timestamp)
                            put("categoryName", tx.categoryName)
                            put("categoryId", tx.categoryId)
                            put("note", tx.note)
                            put("debtId", tx.debtId)
                        }
                        TransactionEditorDialog(passMap) {
                            // Kosong karena Reaktif
                        }.show(parentFragmentManager, "EditorDialog")
                    }
                }

                val left = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                left.addView(TextView(requireContext()).apply { text = tx.note; setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); setTextColor(Color.parseColor("#1E293B")); textSize = 14.5f })
                
                left.addView(TextView(requireContext()).apply {
                    text = "${tx.categoryName} • ${sdfPremiumClock.format(Date(tx.timestamp))}"
                    textSize = 11.5f; setTextColor(Color.parseColor("#94A3B8")); setPadding(0, (2 * density).toInt(), 0, 0)
                })

                val isIncomeFlow = tx.type == "INCOME" || tx.type == "DEBT"
                val rightText = if (isIncomeFlow) "+" else "-"
                val rightColor = if (isIncomeFlow) "#0284C7" else "#EF4444"

                val tvAmt = TextView(requireContext()).apply {
                    text = "$rightText ${formatRupiah.format(tx.amount)}"
                    setTextColor(Color.parseColor(rightColor))
                    setTypeface(null, Typeface.BOLD); textSize = 14.5f
                }

                itemRow.addView(left); itemRow.addView(tvAmt)
                cardLayout.addView(itemRow)
            }
            dateCard.addView(cardLayout)
            binding.transactionListContainer.addView(dateCard)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
