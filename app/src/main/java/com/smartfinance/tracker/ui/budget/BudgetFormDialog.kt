package com.smartfinance.tracker.ui.budget

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.databinding.DialogBudgetFormBinding
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
    private var expenseCategories = listOf<Category>()

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogBudgetFormBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(binding.root).create()

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
    }

    private fun observeCategoriesAndLoadData() {
        lifecycleScope.launch {
            viewModel.categories.collect { allCats ->
                // Filter murni hanya Pengeluaran
                expenseCategories = allCats.filter { it.type == "EXPENSE" }.sortedBy { it.name }
                val names = expenseCategories.map { it.name }
                binding.spinnerCategory.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, names)
                
                // Isi formulir jika ini mode Edit (setelah kategori berhasil dimuat)
                loadDataIfEditing()
            }
        }
    }

    private fun loadDataIfEditing() {
        val docId = editingDocId
        if (docId == null) {
            binding.tvFormTitle.text = "Anggaran Baru"
            binding.btnDelete.visibility = View.GONE
            if (expenseCategories.isNotEmpty()) binding.spinnerCategory.setSelection(0)
        } else {
            val budget = viewModel.budgets.value.find { it.id == docId } ?: return
            binding.tvFormTitle.text = "Edit Anggaran"
            binding.btnDelete.visibility = View.VISIBLE
            binding.etLimitAmount.setText(budget.limitAmount.toLong().toString())
            
            val index = expenseCategories.indexOfFirst { it.id == budget.categoryId }
            if (index >= 0) binding.spinnerCategory.setSelection(index)
        }
    }

    private fun saveBudget() {
        val limitText = binding.etLimitAmount.text.toString()
        val selectedCat = if (expenseCategories.isNotEmpty()) expenseCategories[binding.spinnerCategory.selectedItemPosition] else null

        lifecycleScope.launch {
            try {
                // UI murni mengoper ke ViewModel
                viewModel.validateAndSaveBudget(editingDocId, limitText, selectedCat)
                Toast.makeText(context, "✅ Anggaran berhasil disimpan!", Toast.LENGTH_SHORT).show()
                dismiss() // Tutup formulir dengan manis
            } catch (e: Exception) {
                Toast.makeText(context, "❌ ${e.message}", Toast.LENGTH_SHORT).show()
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
                    try {
                        viewModel.deleteBudget(docId)
                        Toast.makeText(context, "🗑️ Anggaran dihapus!", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, "❌ Gagal menghapus anggaran", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
