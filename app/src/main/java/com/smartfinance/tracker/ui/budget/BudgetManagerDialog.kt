package com.smartfinance.tracker.ui.budget

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.Budget
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.databinding.DialogBudgetManagerBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.HashMap

class BudgetManagerDialog : DialogFragment() {

    private var _binding: DialogBudgetManagerBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: BudgetViewModel
    private var editingDocId: String? = null
    private var expenseCategories = listOf<Category>()
    
    private val formatRp = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    private fun getThemeColor(resId: Int): Int = ContextCompat.getColor(requireContext(), resId)

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    // 1. PINDAHKAN INFLATE KE onCreateView
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogBudgetManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    // 2. PINDAHKAN LOGIKA KE onViewCreated (DI SINI viewLifecycleOwner SUDAH AMAN!)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[BudgetViewModel::class.java]

        binding.btnAddBudget.setOnClickListener { openFormMode(null) }
        binding.btnBack.setOnClickListener { binding.layoutForm.visibility = View.GONE; binding.layoutList.visibility = View.VISIBLE }
        binding.btnDelete.setOnClickListener { deleteCurrentBudget() }
        binding.btnSave.setOnClickListener { saveBudget() }

        // Pantau Perubahan Data secara Paralel
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.categories.collect { allCats ->
                    expenseCategories = allCats.filter { it.type == "EXPENSE" }.sortedBy { it.name }
                    val names = expenseCategories.map { it.name }
                    binding.spinnerCategory.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, names)
                }
            }
            
            launch {
                viewModel.budgets.collect { budgets -> renderBudgets(budgets) }
            }
            
            launch {
                viewModel.transactions.collect { renderBudgets(viewModel.budgets.value) }
            }
        }
    }

    private fun openFormMode(budget: Budget?) {
        binding.layoutList.visibility = View.GONE
        binding.layoutForm.visibility = View.VISIBLE

        if (budget == null) {
            editingDocId = null
            binding.tvFormTitle.text = "Anggaran Baru"
            binding.etLimitAmount.text?.clear()
            binding.btnDelete.visibility = View.GONE
            binding.spinnerCategory.setSelection(0)
        } else {
            editingDocId = budget.id
            binding.tvFormTitle.text = "Edit Anggaran"
            binding.etLimitAmount.setText(budget.limitAmount.toLong().toString())
            binding.btnDelete.visibility = View.VISIBLE
            
            val index = expenseCategories.indexOfFirst { it.id == budget.categoryId }
            if (index >= 0) binding.spinnerCategory.setSelection(index)
        }
    }

    private fun renderBudgets(budgets: List<Budget>) {
        if (_binding == null) return
        val density = requireContext().resources.displayMetrics.density
        binding.listContainer.removeAllViews()

        if (budgets.isEmpty()) {
            binding.listContainer.addView(TextView(requireContext()).apply { 
                text = "Belum ada anggaran yang diatur.\nKlik tombol di bawah untuk membuat baru."
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
                setOnClickListener { openFormMode(budget) }
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
                    text = "⚠️ Kamu sudah melebihi batas anggaran!"; setTextColor(getThemeColor(R.color.expense_red)); textSize = 11f; setPadding(0, (4*density).toInt(), 0, 0)
                })
            } else {
                 row.addView(TextView(requireContext()).apply { 
                    text = "Tersisa: ${formatRp.format(budget.limitAmount - spentAmount)}"; setTextColor(getThemeColor(R.color.income_green)); textSize = 11f; setPadding(0, (4*density).toInt(), 0, 0)
                })
            }

            card.addView(row)
            binding.listContainer.addView(card)
        }
    }

    private fun saveBudget() {
        val limitText = binding.etLimitAmount.text.toString()
        if (limitText.isEmpty() || expenseCategories.isEmpty()) { 
            Snackbar.make(binding.rootFrame, "Mohon isi nominal batas anggaran!", Snackbar.LENGTH_SHORT).show()
            return 
        }

        val selectedCat = expenseCategories[binding.spinnerCategory.selectedItemPosition]
        val data = HashMap<String, Any>().apply {
            put("categoryId", selectedCat.id)
            put("categoryName", selectedCat.name)
            put("limitAmount", limitText.toDoubleOrNull() ?: 0.0)
        }

        lifecycleScope.launch {
            try {
                viewModel.saveBudget(editingDocId, data)
                Snackbar.make(binding.rootFrame, "✅ Anggaran berhasil disimpan!", Snackbar.LENGTH_SHORT).setBackgroundTint(getThemeColor(R.color.primary)).show()
                binding.layoutForm.visibility = View.GONE; binding.layoutList.visibility = View.VISIBLE
            } catch (e: Exception) {
                Snackbar.make(binding.rootFrame, "❌ Gagal menyimpan anggaran", Snackbar.LENGTH_SHORT).setBackgroundTint(getThemeColor(R.color.expense_red)).show()
            }
        }
    }

    private fun deleteCurrentBudget() {
        val docId = editingDocId ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Anggaran")
            .setMessage("Yakin ingin menghapus batas anggaran ini?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    viewModel.deleteBudget(docId)
                    Snackbar.make(binding.rootFrame, "🗑️ Anggaran dihapus!", Snackbar.LENGTH_SHORT).show()
                    binding.layoutForm.visibility = View.GONE; binding.layoutList.visibility = View.VISIBLE
                }
            }
            .setNegativeButton("Batal", null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
