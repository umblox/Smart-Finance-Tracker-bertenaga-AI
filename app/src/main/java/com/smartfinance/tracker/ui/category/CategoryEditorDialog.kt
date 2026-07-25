package com.smartfinance.tracker.ui.category

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
import com.smartfinance.tracker.databinding.DialogCategoryEditorBinding
import kotlinx.coroutines.launch
import java.util.ArrayList

class CategoryEditorDialog : DialogFragment() {

    companion object {
        fun newInstance(category: Category?, activeTypeFilter: String): CategoryEditorDialog {
            val frag = CategoryEditorDialog()
            val args = Bundle().apply {
                putString("TYPE_FILTER", activeTypeFilter)
                if (category != null) {
                    putString("DOC_ID", category.docId)
                    putLong("ID", category.id)
                    putString("NAME", category.name)
                    putString("ICON", category.iconName)
                    putBoolean("IS_LOCKED", category.isLocked)
                    category.parentCategoryId?.let { putLong("PARENT_ID", it) }
                }
            }
            frag.arguments = args
            return frag
        }
    }

    private var _binding: DialogCategoryEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: CategoryViewModel
    private var availableParents = ArrayList<Category>()

    // 🔥 FIX 1: Fullscreen aman
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCategoryEditorBinding.inflate(layoutInflater)
        // 🔥 FIX 2: Menghapus Theme Lawas yang menghancurkan UI
        val dialog = AlertDialog.Builder(requireContext()).setView(binding.root).create()

        viewModel = ViewModelProvider(requireActivity())[CategoryViewModel::class.java]

        val docId = arguments?.getString("DOC_ID")
        val currentNumericId = if (arguments?.containsKey("ID") == true) arguments?.getLong("ID") else null
        val currentName = arguments?.getString("NAME") ?: ""
        val isLocked = arguments?.getBoolean("IS_LOCKED") ?: false
        val currentParentId = if (arguments?.containsKey("PARENT_ID") == true) arguments?.getLong("PARENT_ID") else null
        val activeTypeFilter = arguments?.getString("TYPE_FILTER") ?: "EXPENSE"

        binding.tvTitle.text = if (docId == null) "Tambah Kategori Baru" else "Ubah Detail Kategori"
        binding.btnDelete.visibility = if (docId != null && !isLocked) View.VISIBLE else View.GONE
        binding.btnSave.visibility = if (docId != null && isLocked) View.GONE else View.VISIBLE
        
        binding.etName.setText(currentName)
        if (docId != null && isLocked) {
            binding.etName.isEnabled = false
            binding.spinnerParent.isEnabled = false
        }

        binding.btnClose.setOnClickListener { dismiss() }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                availableParents.clear()
                val typedParents = state.allCategoriesForEditor.filter { 
                    it.parentCategoryId == null && 
                    it.type == activeTypeFilter && 
                    it.id != currentNumericId 
                }
                availableParents.addAll(typedParents)

                val listNames = mutableListOf("[Tanpa Induk / Kategori Utama]")
                availableParents.forEach { listNames.add(it.name) }

                if (context != null) {
                    val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listNames)
                    spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerParent.adapter = spinnerAdapter

                    currentParentId?.let { pId ->
                        val matchIdx = availableParents.indexOfFirst { it.id == pId }
                        if (matchIdx != -1) binding.spinnerParent.setSelection(matchIdx + 1)
                    }
                }
            }
        }

        binding.btnDelete.setOnClickListener {
            if (docId != null && !isLocked) {
                lifecycleScope.launch {
                    try {
                        viewModel.deleteCategoryFromCloud(docId)
                        Toast.makeText(context, "Kategori sukses dilenyapkan!", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal menghapus!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.btnSave.setOnClickListener {
            val finalName = binding.etName.text.toString()
            val selectedPos = binding.spinnerParent.selectedItemPosition
            val finalParentId = if (selectedPos == 0 || availableParents.isEmpty()) null else availableParents[selectedPos - 1].id
            val iconName = arguments?.getString("ICON") ?: "ic_custom"

            lifecycleScope.launch {
                try {
                    viewModel.validateAndSaveCategory(
                        docId = docId, currentNumericId = currentNumericId, 
                        name = finalName, type = activeTypeFilter, 
                        iconName = iconName, isLocked = isLocked, parentId = finalParentId
                    )
                    Toast.makeText(context, "Kategori sukses disimpan!", Toast.LENGTH_SHORT).show()
                    dismiss()
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
