package com.smartfinance.tracker.ui.budget

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.Budget
import com.smartfinance.tracker.databinding.DialogBudgetManagerBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class BudgetManagerDialog : DialogFragment() {

    private var _binding: DialogBudgetManagerBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: BudgetViewModel
    private val formatRp = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    private fun getThemeColor(resId: Int): Int = ContextCompat.getColor(requireContext(), resId)

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogBudgetManagerBinding.inflate(layoutInflater)
        // 🔥 FIX: Upgrade UI ke Material
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(binding.root).create()

        viewModel = ViewModelProvider(requireActivity())[BudgetViewModel::class.java]

        binding.btnAddBudget.setOnClickListener {
            BudgetFormDialog.newInstance(null).show(parentFragmentManager, "BudgetForm")
        }

        lifecycleScope.launch {
            launch {
                viewModel.budgets.collect { budgets -> renderBudgets(budgets) }
            }
            launch {
                viewModel.transactions.collect { renderBudgets(viewModel.budgets.value) }
            }
        }

        return dialog
    }

    private fun renderBudgets(budgets: List<Budget>) {
        if (_binding == null) return
        val density = requireContext().resources.displayMetrics.density
        binding.listContainer.removeAllViews()

        if (budgets.isEmpty()) {
            binding.listContainer.addView(TextView(requireContext()).apply { 
                text = getString(R.string.budget_empty_state)
                setTextColor(getThemeColor(R.color.text_secondary)); textSize = 13f
                textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, (40 * density).toInt(), 0, (40 * density).toInt()) 
            })
            return
        }

        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        
        val currentMonthExpenses = viewModel.transactions.value.filter { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            tx.type == "EXPENSE" && txCal.get(Calendar.MONTH) == currentMonth && txCal.get(Calendar.YEAR) == currentYear
        }

        budgets.forEach { budget ->
            val spentAmount = currentMonthExpenses.filter { it.categoryId == budget.categoryId }.sumOf { it.amount }
            val progressPercent = if (budget.limitAmount > 0) ((spentAmount / budget.limitAmount) * 100).toInt() else 0
            val isOverBudget = spentAmount > budget.limitAmount

            val card = MaterialCardView(requireContext()).apply {
                radius = 12 * density; cardElevation = 1 * density; setCardBackgroundColor(getThemeColor(R.color.surface_white))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() }
                
                setOnClickListener { 
                    BudgetFormDialog.newInstance(budget.id).show(parentFragmentManager, "BudgetForm")
                }
            }
            
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt()) }
            
            val headerRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
            headerRow.addView(TextView(requireContext()).apply { 
                text = budget.categoryName; setTextColor(getThemeColor(R.color.text_primary)); setTypeface(null, Typeface.BOLD); textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) 
            })
            headerRow.addView(TextView(requireContext()).apply { 
                text = "${formatRp.format(spentAmount)} / ${formatRp.format(budget.limitAmount)}"
                setTextColor(getThemeColor(if (isOverBudget) R.color.expense_red else R.color.text_secondary)); textSize = 12f
            })
            row.addView(headerRow)

            val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (8f * density).toInt()).apply { topMargin = (8f*density).toInt() }
                progressDrawable = ContextCompat.getDrawable(context, com.google.android.material.R.drawable.design_snackbar_background)
                max = 100
                progress = Math.min(progressPercent, 100)
                progressTintList = android.content.res.ColorStateList.valueOf(getThemeColor(if (isOverBudget) R.color.expense_red else R.color.primary))
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(getThemeColor(R.color.divider_color))
            }
            row.addView(progressBar)
            
            if (isOverBudget) {
                row.addView(TextView(requireContext()).apply { 
                    text = getString(R.string.budget_warning_over); setTextColor(getThemeColor(R.color.expense_red)); textSize = 11f; setPadding(0, (4*density).toInt(), 0, 0)
                })
            } else {
                 row.addView(TextView(requireContext()).apply { 
                    text = getString(R.string.budget_remaining, formatRp.format(budget.limitAmount - spentAmount)); setTextColor(getThemeColor(R.color.income_green)); textSize = 11f; setPadding(0, (4*density).toInt(), 0, 0)
                })
            }

            card.addView(row)
            binding.listContainer.addView(card)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
