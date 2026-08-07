package com.smartfinance.tracker.ui.transaction

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.provider.ContactsContract
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartfinance.tracker.R
import com.smartfinance.tracker.databinding.DialogTransactionPremiumBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class TransactionEditorDialog(
    private val transactionData: HashMap<String, Any>,
    private val onUpdateAction: () -> Unit
) : DialogFragment() {

    private var _binding: DialogTransactionPremiumBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TransactionViewModel

    private var currentType = "EXPENSE"
    private var allCategoriesCloud = listOf<Map<String, Any>>()
    private var selectedCategoryMap: Map<String, Any>? = null
    
    private var isInitialized = false 

    private val sdfPremium = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    private val contactPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            requireContext().contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (nameIndex != -1) binding.etContact.setText(cursor.getString(nameIndex))
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogTransactionPremiumBinding.inflate(layoutInflater)
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        viewModel = ViewModelProvider(this)[TransactionViewModel::class.java]

        binding.tvDialogTitle.text = getString(R.string.tx_edit_title)
        binding.btnDelete.visibility = View.VISIBLE

        val docId = transactionData["id"] as? String ?: ""
        val currentAmount = (transactionData["amount"] as? Number)?.toLong() ?: 0L
        val currentNote = transactionData["note"] as? String ?: ""
        val currentTimestamp = (transactionData["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val currentCategoryId = (transactionData["categoryId"] as? Number)?.toLong() ?: 0L
        val currentCategoryName = transactionData["categoryName"] as? String ?: ""
        val targetDebtId = transactionData["debtId"] as? String ?: ""
        
        val isDebtInitially = targetDebtId.isNotEmpty() || currentCategoryId in listOf(101L, 102L, 103L, 104L)
        
        binding.etPremiumTxAmount.setText(currentAmount.toString())
        binding.etPremiumTxDate.setText(sdfPremium.format(Date(currentTimestamp)))

        binding.tvContactLabel.visibility = View.VISIBLE
        binding.layoutContact.visibility = View.VISIBLE
        
        // 🔥 DELEGASI KE VIEWMODEL: Memotong kontak dari catatan
        val parsedNoteData = viewModel.parseTransactionNote(currentNote, isDebtInitially)
        
        if (isDebtInitially) {
            binding.rbPremiumTxDebt.isChecked = true
            currentType = "DEBT"
            binding.tvContactLabel.text = getString(R.string.tx_contact_label) + " *"
        } else {
            currentType = (transactionData["type"] as? String ?: "EXPENSE").trim().uppercase(Locale.ROOT)
            if (currentType == "INCOME") binding.rbPremiumTxIncome.isChecked = true else binding.rbPremiumTxExpense.isChecked = true
            binding.tvContactLabel.text = getString(R.string.tx_contact_label) + " (Opsional)"
        }
        
        binding.etPremiumTxNote.setText(parsedNoteData.cleanNote.ifEmpty { if (isDebtInitially) "MANUAL" else "" })
        binding.etContact.setText(parsedNoteData.contactName)

        binding.btnPickContact.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPickerLauncher.launch(intent)
        }

        if (currentCategoryId != 0L) {
            selectedCategoryMap = mapOf("id" to currentCategoryId, "name" to currentCategoryName, "type" to if (isDebtInitially) "DEBT" else currentType)
        }

        binding.rgPremiumTxType.setOnCheckedChangeListener { _, checkedId ->
            if (!isInitialized) return@setOnCheckedChangeListener 

            val newType = when (checkedId) {
                binding.rbPremiumTxIncome.id -> "INCOME"
                binding.rbPremiumTxDebt.id -> "DEBT"
                else -> "EXPENSE"
            }
            
            if (currentType != newType) {
                currentType = newType
                selectedCategoryMap = null
                binding.btnCategoryPicker.text = getString(R.string.tx_choose_category)

                if (newType == "DEBT") {
                    binding.tvContactLabel.text = getString(R.string.tx_contact_label) + " *"
                } else {
                    binding.tvContactLabel.text = getString(R.string.tx_contact_label) + " (Opsional)"
                }
            }
        }
        
        binding.btnCategoryPicker.setOnClickListener { showCategoryPickerDialog() }

        lifecycleScope.launch {
            try {
                allCategoriesCloud = viewModel.getCategoriesForDropdown()
            } catch (e: Exception) {
                allCategoriesCloud = listOf(
                    mapOf("id" to 101L, "name" to "Hutang", "type" to "DEBT"),
                    mapOf("id" to 104L, "name" to "Piutang", "type" to "DEBT"),
                    mapOf("id" to 102L, "name" to "Pembayaran kembali", "type" to "DEBT"),
                    mapOf("id" to 103L, "name" to "Penagihan Utang", "type" to "DEBT")
                )
            }
            
            val dbCategory = allCategoriesCloud.find { (it["id"] as? Number)?.toLong() == currentCategoryId }
            if (dbCategory != null) {
                selectedCategoryMap = dbCategory
            } else {
                selectedCategoryMap = mapOf("id" to currentCategoryId, "name" to currentCategoryName, "type" to currentType)
            }

            val nameToDisplay = selectedCategoryMap?.get("name") as? String ?: currentCategoryName
            if (nameToDisplay.isNotBlank() && nameToDisplay != "null") {
                binding.btnCategoryPicker.text = nameToDisplay
            } else {
                binding.btnCategoryPicker.text = getString(R.string.tx_choose_category)
            }
        }

        binding.btnCancel.setOnClickListener { dialog.dismiss() }

        binding.btnDelete.setOnClickListener {
            if (docId.isNotEmpty()) {
                lifecycleScope.launch {
                    if (targetDebtId.isNotEmpty()) viewModel.deleteDebt(targetDebtId)
                    viewModel.deleteTransaction(docId)
                    
                    Toast.makeText(context, getString(R.string.success_deleted), Toast.LENGTH_SHORT).show()
                    onUpdateAction()
                    dialog.dismiss()
                }
            }
        }

        binding.btnSave.setOnClickListener {
            val amountVal = binding.etPremiumTxAmount.text.toString().toDoubleOrNull() ?: 0.0
            val noteRawVal = binding.etPremiumTxNote.text.toString().trim()
            val dateVal = binding.etPremiumTxDate.text.toString().trim()
            val contactNameVal = binding.etContact.text.toString().trim().uppercase(Locale.ROOT)
            val isEditingDebt = binding.rgPremiumTxType.checkedRadioButtonId == binding.rbPremiumTxDebt.id

            if (isEditingDebt && contactNameVal.isEmpty()) {
                Toast.makeText(context, getString(R.string.tx_toast_contact_required_simple), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (amountVal > 0.0 && noteRawVal.isNotEmpty() && dateVal.isNotEmpty() && docId.isNotEmpty()) {
                
                if (selectedCategoryMap == null) {
                    Toast.makeText(context, getString(R.string.tx_toast_select_category), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val originalFormattedDate = sdfPremium.format(Date(currentTimestamp))
                val parsedDate = try { 
                    if (dateVal == originalFormattedDate) currentTimestamp 
                    else sdfPremium.parse(dateVal)?.time ?: currentTimestamp 
                } catch (e: Exception) { currentTimestamp }
                
                val catId = (selectedCategoryMap!!["id"] as? Number)?.toLong() ?: 15L
                val catName = selectedCategoryMap!!["name"] as? String ?: "Umum"

                lifecycleScope.launch {
                    // 🔥 DELEGASI KE VIEWMODEL: Menentukan tipe dan merakit format akhir
                    val selectedTypeRaw = if (binding.rgPremiumTxType.checkedRadioButtonId == binding.rbPremiumTxIncome.id) "INCOME" else "EXPENSE"
                    val finalTxType = viewModel.determineTransactionType(catId, selectedTypeRaw, isEditingDebt)
                    val finalNote = viewModel.formatTransactionNote(noteRawVal, contactNameVal, catName, isEditingDebt)

                    if (isEditingDebt && targetDebtId.isNotEmpty()) {
                        val selectedDebtType = if (catId == 104L || catId == 103L) "RECEIVABLE" else "DEBT"
                        viewModel.updateDebtFields(targetDebtId, contactNameVal, amountVal, selectedDebtType, parsedDate)
                    }

                    val updatedTxMap = HashMap<String, Any>()
                    updatedTxMap["id"] = docId
                    updatedTxMap["amount"] = amountVal
                    updatedTxMap["note"] = finalNote
                    updatedTxMap["timestamp"] = parsedDate
                    updatedTxMap["categoryId"] = catId
                    updatedTxMap["categoryName"] = catName
                    updatedTxMap["type"] = finalTxType
                    
                    if (targetDebtId.isNotEmpty() && isEditingDebt) {
                        updatedTxMap["debtId"] = targetDebtId
                    }

                    viewModel.saveTransaction(docId, updatedTxMap)
                    
                    Toast.makeText(context, getString(R.string.tx_toast_update_success), Toast.LENGTH_SHORT).show()
                    onUpdateAction()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(context, getString(R.string.tx_toast_invalid_input), Toast.LENGTH_SHORT).show()
            }
        }

        isInitialized = true 
        return dialog
    }

    private fun showCategoryPickerDialog() {
        val currentFilter = when (binding.rgPremiumTxType.checkedRadioButtonId) {
            binding.rbPremiumTxIncome.id -> "INCOME"
            binding.rbPremiumTxDebt.id -> "DEBT"
            else -> "EXPENSE"
        }
        val currentSelectedId = (selectedCategoryMap?.get("id") as? Number)?.toLong()

        com.smartfinance.tracker.ui.category.CategoryPickerDialog(currentFilter, currentSelectedId) { selectedCat ->
            val mappedCat = HashMap<String, Any>()
            mappedCat["id"] = selectedCat.id
            mappedCat["name"] = selectedCat.name
            mappedCat["type"] = selectedCat.type
            
            selectedCategoryMap = mappedCat
            binding.btnCategoryPicker.text = selectedCat.name
        }.show(parentFragmentManager, "CategoryPickerDialog")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
