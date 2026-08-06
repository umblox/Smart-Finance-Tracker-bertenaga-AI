package com.smartfinance.tracker.ui.settings

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.RecurringTransaction
import com.smartfinance.tracker.databinding.DialogRecurringTxBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class RecurringTxListDialog : DialogFragment() {

    private var _binding: DialogRecurringTxBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: RecurringTxViewModel

    private fun getThemeColor(resId: Int): Int = ContextCompat.getColor(requireContext(), resId)

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogRecurringTxBinding.inflate(layoutInflater)
        // 🔥 FIX: Upgrade UI
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(binding.root).create()

        viewModel = ViewModelProvider(requireActivity())[RecurringTxViewModel::class.java]

        binding.btnAddSchedule.setOnClickListener {
            RecurringTxFormDialog.newInstance(null).show(parentFragmentManager, "FormTx")
        }

        lifecycleScope.launch {
            viewModel.schedules.collect { schedules ->
                renderSchedules(schedules)
            }
        }

        return dialog
    }

    private fun renderSchedules(schedules: List<RecurringTransaction>) {
        val density = requireContext().resources.displayMetrics.density
        binding.listContainer.removeAllViews()

        if (schedules.isEmpty()) {
            binding.listContainer.addView(TextView(requireContext()).apply { 
                text = getString(R.string.recurring_empty_state)
                setTextColor(getThemeColor(R.color.text_secondary)); textSize = 14f; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, 40, 0, 40) 
            })
            return
        }

        val formatRp = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        for (doc in schedules) {
            val card = MaterialCardView(requireContext()).apply {
                radius = 12 * density; cardElevation = 1 * density; 
                setCardBackgroundColor(getThemeColor(R.color.surface_white))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() }
                
                setOnClickListener {
                    RecurringTxFormDialog.newInstance(doc.id).show(parentFragmentManager, "FormTx")
                }
            }
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt()) }
            
            // 🔥 Mengambil teks label interval (Mingguan, Harian, dll) dari resources agar bisa dwibahasa
            val intervalLabel = when (doc.interval) {
                "DAILY" -> getString(R.string.recurring_interval_daily)
                "WEEKLY" -> getString(R.string.recurring_interval_weekly)
                "MONTHLY" -> getString(R.string.recurring_interval_monthly)
                "YEARLY" -> getString(R.string.recurring_interval_yearly)
                else -> doc.interval
            }

            row.addView(TextView(requireContext()).apply { text = doc.note; setTextColor(getThemeColor(R.color.text_primary)); setTypeface(null, Typeface.BOLD); textSize = 16f })
            row.addView(TextView(requireContext()).apply { text = "${formatRp.format(doc.amount)} • $intervalLabel"; setTextColor(getThemeColor(R.color.primary)); textSize = 14f; setPadding(0, 4, 0, 0) })
            
            card.addView(row)
            binding.listContainer.addView(card)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
