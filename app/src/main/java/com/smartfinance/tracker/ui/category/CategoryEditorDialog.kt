package com.smartfinance.tracker.ui.category

import android.os.Bundle
import android.view.LayoutInflater
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
    
    // 🔥 VARIABEL PENYIMPAN STATE IKON SAAT INI
    private var currentSelectedIcon = "ic_custom"

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogCategoryEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[CategoryViewModel::class.java]

        val docId = arguments?.getString("DOC_ID")
        val currentNumericId = if (arguments?.containsKey("ID") == true) arguments?.getLong("ID") else null
        val currentName = arguments?.getString("NAME") ?: ""
        val isLocked = arguments?.getBoolean("IS_LOCKED") ?: false
        val currentParentId = if (arguments?.containsKey("PARENT_ID") == true) arguments?.getLong("PARENT_ID") else null
        val activeTypeFilter = arguments?.getString("TYPE_FILTER") ?: "EXPENSE"
        
        // 🔥 AMBIL IKON DARI ARGUMEN (Atau default jika buat baru)
        currentSelectedIcon = arguments?.getString("ICON") ?: "ic_custom"

        binding.tvTitle.text = if (docId == null) "Tambah Kategori Baru" else "Ubah Detail Kategori"
        binding.btnDelete.visibility = if (docId != null && !isLocked) View.VISIBLE else View.GONE
        binding.btnSave.visibility = if (docId != null && isLocked) View.GONE else View.VISIBLE
        
        binding.etName.setText(currentName)
        
        // 🔥 RENDER IKON SAAT INI KE UI
        binding.ivCategoryIcon.setImageResource(com.smartfinance.tracker.utils.IconProvider.getIconResource(currentSelectedIcon))

        if (docId != null && isLocked) {
            // KUNCI: Kategori Sistem tidak boleh diedit sama sekali
            binding.etName.isEnabled = false
            binding.spinnerParent.isEnabled = false
            binding.ivCategoryIcon.isEnabled = false
            binding.ivCategoryIcon.alpha = 0.5f 
        } else {
            // BUKA PICKER IKON JIKA DIKLIK
            binding.ivCategoryIcon.setOnClickListener {
                IconPickerDialog(currentSelectedIcon) { newIcon ->
                    currentSelectedIcon = newIcon
                    binding.ivCategoryIcon.setImageResource(com.smartfinance.tracker.utils.IconProvider.getIconResource(newIcon))
                }.show(parentFragmentManager, "IconPicker")
            }
        }

        binding.btnClose.setOnClickListener { dismiss() }

        viewLifecycleOwner.lifecycleScope.launch {
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

            lifecycleScope.launch {
                try {
                    // 🔥 KIRIM IKON YANG DIPILIH KE VIEWMODEL
                    viewModel.validateAndSaveCategory(
                        docId = docId, currentNumericId = currentNumericId, 
                        name = finalName, type = activeTypeFilter, 
                        iconName = currentSelectedIcon, isLocked = isLocked, parentId = finalParentId
                    )
                    Toast.makeText(context, "Kategori sukses disimpan!", Toast.LENGTH_SHORT).show()
                    dismiss()
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
