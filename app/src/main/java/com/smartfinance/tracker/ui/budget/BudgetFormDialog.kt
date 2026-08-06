package com.smartfinance.tracker.ui.budget

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.databinding.DialogBudgetFormBinding
import com.smartfinance.tracker.ui.category.CategoryPickerDialog
import kotlinx.coroutines.launch

class BudgetFormDialog : DialogFragment() {

    companion object {
        fun newInstance(docId: String?): BudgetFormDialog {
            val frag = BudgetFormDialog()
            val args = Bundle()
            args.putString("DOC_ID", docId)
            frag.arguments = args
            return frag
        }
    }

    private var _binding: DialogBudgetFormBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: BudgetViewModel

    private var editingDocId: String? = null
    private var selectedCategory: Category? = null

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogBudgetFormBinding.inflate(layoutInflater)
        // 🔥 FIX: Upgrade UI
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(binding.root).create()

        viewModel = ViewModelProvider(requireActivity())[BudgetViewModel::class.java]
        editingDocId = arguments?.getString("DOC_ID")

        setupUI()
        observeCategoriesAndLoadData()

        return dialog
    }

    private fun setupUI() {
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { saveBudget() }
        binding.btnDelete.setOnClickListener { deleteCurrentBudget() }
        
        binding.btnCategoryPicker.setOnClickListener { showCategoryPicker() }
    }

    private fun showCategoryPicker() {
        CategoryPickerDialog("EXPENSE", selectedCategory?.id) { selectedCat ->
            if (selectedCat.type != "EXPENSE") {
                Toast.makeText(context, getString(R.string.budget_toast_expense_only), Toast.LENGTH_SHORT).show()
                return@CategoryPickerDialog
            }
            selectedCategory = selectedCat
            binding.btnCategoryPicker.text = selectedCat.name
        }.show(parentFragmentManager, "CategoryPickerDialog")
    }

    private fun observeCategoriesAndLoadData() {
        lifecycleScope.launch {
            viewModel.categories.collect { 
                loadDataIfEditing()
            }
        }
    }

    private fun loadDataIfEditing() {
        val docId = editingDocId
        if (docId == null) {
            binding.tvFormTitle.text = getString(R.string.budget_form_title_new)
            binding.btnDelete.visibility = View.GONE
        } else {
            val budget = viewModel.budgets.value.find { it.id == docId } ?: return
            binding.tvFormTitle.text = getString(R.string.budget_form_title_edit)
            binding.btnDelete.visibility = View.VISIBLE
            binding.etLimitAmount.setText(budget.limitAmount.toLong().toString())
            
            selectedCategory = viewModel.categories.value.find { it.id == budget.categoryId }
            binding.btnCategoryPicker.text = budget.categoryName
        }
    }

    private fun saveBudget() {
        val limitText = binding.etLimitAmount.text.toString()

        lifecycleScope.launch {
            try {
                viewModel.validateAndSaveBudget(editingDocId, limitText, selectedCategory)
                Toast.makeText(context, getString(R.string.budget_toast_saved), Toast.LENGTH_SHORT).show()
                dismiss() 
            } catch (e: Exception) {
                Toast.makeText(context, "❌ ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteCurrentBudget() {
        val docId = editingDocId ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.budget_delete_title))
            .setMessage(getString(R.string.budget_delete_message))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                lifecycleScope.launch {
                    try {
                        viewModel.deleteBudget(docId)
                        Toast.makeText(context, getString(R.string.budget_toast_deleted), Toast.LENGTH_SHORT).show()
                        dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, getString(R.string.budget_toast_delete_fail), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
