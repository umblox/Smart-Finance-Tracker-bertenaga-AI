package com.smartfinance.tracker.ui.category

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.databinding.DialogCategoryEditorBinding
import kotlinx.coroutines.launch
import java.util.ArrayList
import java.util.HashMap

class CategoryEditorDialog(
    private val categoryData: HashMap<String, Any>?,
    private val activeTypeFilter: String,
    private val onSavedAction: () -> Unit
) : DialogFragment() {

    private var _binding: DialogCategoryEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: CategoryViewModel
    private var availableParents = ArrayList<Category>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCategoryEditorBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            .setView(binding.root)
            .create()

        viewModel = ViewModelProvider(this)[CategoryViewModel::class.java]

        val docId = categoryData?.get("docId") as? String ?: ""
        val currentNumericId = categoryData?.get("id") as? Long
        val currentName = categoryData?.get("name") as? String ?: ""
        val isLocked = categoryData?.get("isLocked") as? Boolean ?: false
        val currentParentId = (categoryData?.get("parentCategoryId") as? Number)?.toLong()

        binding.tvTitle.text = if (categoryData == null) "Tambah Kategori Baru" else "Ubah Detail Kategori"
        binding.btnDelete.visibility = if (categoryData != null && !isLocked) View.VISIBLE else View.GONE
        binding.btnSave.visibility = if (categoryData != null && isLocked) View.GONE else View.VISIBLE
        
        binding.etName.setText(currentName)
        if (categoryData != null && isLocked) {
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
            if (categoryData != null && !isLocked && docId.isNotEmpty()) {
                lifecycleScope.launch {
                    viewModel.deleteCategoryFromCloud(docId)
                    Toast.makeText(context, "Kategori sukses dilenyapkan!", Toast.LENGTH_SHORT).show()
                    onSavedAction()
                    dialog.dismiss()
                }
            }
        }

        binding.btnSave.setOnClickListener {
            val finalName = binding.etName.text.toString().trim()
            if (finalName.isNotEmpty()) {
                val selectedPos = binding.spinnerParent.selectedItemPosition
                val finalParentId = if (selectedPos == 0 || availableParents.isEmpty()) null else availableParents[selectedPos - 1].id

                val targetDocId = if (docId.isEmpty()) "cat_${System.currentTimeMillis()}" else docId
                val targetNumericId = currentNumericId ?: System.currentTimeMillis()

                // FIX: Menghindari type inference Kotlin yang salah
                val categoryMap = HashMap<String, Any>()
                categoryMap["id"] = targetNumericId
                categoryMap["name"] = finalName
                categoryMap["type"] = activeTypeFilter
                categoryMap["iconName"] = categoryData?.get("iconName") as? String ?: "ic_custom"
                categoryMap["isLocked"] = false
                if (finalParentId != null) {
                    categoryMap["parentCategoryId"] = finalParentId
                }

                lifecycleScope.launch {
                    viewModel.saveCategoryToCloud(targetDocId, categoryMap)
                    Toast.makeText(context, "Kategori sukses disimpan ke Cloud!", Toast.LENGTH_SHORT).show()
                    onSavedAction()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Nama kategori tidak boleh kosong!", Toast.LENGTH_SHORT).show()
            }
        }

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
