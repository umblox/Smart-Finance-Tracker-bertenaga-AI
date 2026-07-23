package com.smartfinance.tracker.ui.transaction

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.provider.ContactsContract
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.databinding.DialogTransactionPremiumBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
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
    private var filteredCategoriesCloud = mutableListOf<Map<String, Any>>()
    private var isDebtTransaction = false

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
        val dialog = AlertDialog.Builder(requireContext()).setView(binding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        viewModel = ViewModelProvider(this)[TransactionViewModel::class.java]

        binding.tvDialogTitle.text = "Edit Transaksi"
        binding.btnDelete.visibility = View.VISIBLE

        val docId = transactionData["id"] as? String ?: ""
        val currentAmount = (transactionData["amount"] as? Number)?.toLong() ?: 0L
        val currentNote = transactionData["note"] as? String ?: ""
        val currentTimestamp = (transactionData["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val currentCategoryId = (transactionData["categoryId"] as? Number)?.toLong() ?: 0L
        val targetDebtId = transactionData["debtId"] as? String ?: ""
        
        isDebtTransaction = targetDebtId.isNotEmpty()
        binding.etPremiumTxAmount.setText(currentAmount.toString())
        binding.etPremiumTxDate.setText(sdfPremium.format(Date(currentTimestamp)))

        if (isDebtTransaction) {
            binding.rbPremiumTxExpense.text = "Saya Berhutang (Hutang)"
            binding.rbPremiumTxIncome.text = "Orang Lain Berhutang (Piutang)"
            
            binding.tvContactLabel.visibility = View.VISIBLE
            binding.layoutContact.visibility = View.VISIBLE
            binding.cardSpinner.visibility = View.GONE
            binding.tvCategoryLabel.visibility = View.GONE
            
            val isReceivableInitial = currentCategoryId == 104L || currentNote.contains("PIUTANG")
            if (isReceivableInitial) binding.rbPremiumTxIncome.isChecked = true else binding.rbPremiumTxExpense.isChecked = true
            currentType = "DEBT"

            var extractedName = currentNote.replace(Regex("\\[.*?\\]"), "").trim()
            if (extractedName.contains("-")) extractedName = extractedName.split("-")[0].trim()
            
            binding.etPremiumTxNote.setText(currentNote.substringAfter("- ").ifEmpty { "INPUT MANUAL" })
            binding.etContact.setText(extractedName)

            binding.btnPickContact.setOnClickListener {
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                contactPickerLauncher.launch(intent)
            }
        } else {
            val initialTypeRaw = (transactionData["type"] as? String ?: "EXPENSE").trim().uppercase(Locale.ROOT)
            currentType = initialTypeRaw
            binding.etPremiumTxNote.setText(currentNote)
            if (currentType == "INCOME") binding.rbPremiumTxIncome.isChecked = true else binding.rbPremiumTxExpense.isChecked = true
        }

        binding.rgPremiumTxType.setOnCheckedChangeListener { _, checkedId ->
            if (!isDebtTransaction) {
                currentType = if (checkedId == binding.rbPremiumTxIncome.id) "INCOME" else "EXPENSE"
                mapSpinnerHierarchyCloud(currentCategoryId)
            }
        }

        lifecycleScope.launch {
            try {
                allCategoriesCloud = viewModel.getCategoriesForDropdown()
                mapSpinnerHierarchyCloud(currentCategoryId)
            } catch (e: Exception) {
                allCategoriesCloud = listOf(
                    mapOf("id" to 101L, "name" to "Hutang", "type" to "DEBT"),
                    mapOf("id" to 104L, "name" to "Piutang", "type" to "DEBT")
                )
                mapSpinnerHierarchyCloud(currentCategoryId)
            }
        }

        binding.btnCancel.setOnClickListener { dialog.dismiss() }

        binding.btnDelete.setOnClickListener {
            if (docId.isNotEmpty()) {
                lifecycleScope.launch {
                    if (targetDebtId.isNotEmpty()) viewModel.deleteDebt(targetDebtId)
                    viewModel.deleteTransaction(docId)
                    
                    Toast.makeText(context, "Berhasil dihapus!", Toast.LENGTH_SHORT).show()
                    onUpdateAction()
                    dialog.dismiss()
                }
            }
        }

        binding.btnSave.setOnClickListener {
            val amountVal = binding.etPremiumTxAmount.text.toString().toDoubleOrNull() ?: 0.0
            val noteRawVal = binding.etPremiumTxNote.text.toString().trim()
            val dateVal = binding.etPremiumTxDate.text.toString().trim()

            if (amountVal > 0.0 && noteRawVal.isNotEmpty() && dateVal.isNotEmpty() && filteredCategoriesCloud.isNotEmpty() && docId.isNotEmpty()) {
                val parsedDate = try { sdfPremium.parse(dateVal)?.time ?: currentTimestamp } catch (e: Exception) { currentTimestamp }
                
                val selectedCategory = filteredCategoriesCloud[binding.spinnerPremiumTxCategory.selectedItemPosition]
                var catId = selectedCategory["id"] as Long
                var catName = selectedCategory["name"] as String

                lifecycleScope.launch {
                    var finalTxType = if (binding.rgPremiumTxType.checkedRadioButtonId == binding.rbPremiumTxIncome.id) "INCOME" else "EXPENSE"
                    var finalNote = noteRawVal.uppercase(Locale.ROOT)

                    if (isDebtTransaction) {
                        val contactNameVal = binding.etContact.text.toString().trim().uppercase(Locale.ROOT)
                        if (contactNameVal.isEmpty()) {
                            Toast.makeText(context, "Nama kontak wajib diisi!", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val isReceivableSelected = binding.rgPremiumTxType.checkedRadioButtonId == binding.rbPremiumTxIncome.id
                        catId = if (isReceivableSelected) 104L else 101L
                        catName = if (isReceivableSelected) "Piutang" else "Hutang"
                        
                        val selectedDebtType = if (isReceivableSelected) "RECEIVABLE" else "DEBT"
                        finalTxType = if (isReceivableSelected) "EXPENSE" else "INCOME"
                        finalNote = "[$catName] $contactNameVal - $finalNote"

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
                    updatedTxMap["debtId"] = targetDebtId

                    viewModel.saveTransaction(docId, updatedTxMap)
                    
                    Toast.makeText(context, "Perubahan sukses disimpan!", Toast.LENGTH_SHORT).show()
                    onUpdateAction()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Data input tidak valid!", Toast.LENGTH_SHORT).show()
            }
        }

        return dialog
    }

    private fun mapSpinnerHierarchyCloud(selectedCategoryId: Long) {
        filteredCategoriesCloud.clear()
        val displayNames = mutableListOf<String>()

        if (currentType == "DEBT") {
            val debtSystemCategories = allCategoriesCloud.filter { (it["type"] as? String) == "DEBT" }
            debtSystemCategories.forEach { cat ->
                filteredCategoriesCloud.add(cat)
                displayNames.add("🔒 ${cat["name"] as String}")
            }
        } else {
            val typedList = allCategoriesCloud.filter { (it["type"] as? String) == currentType }
            val parents = typedList.filter { it["parentCategoryId"] == null }
            val subs = typedList.filter { it["parentCategoryId"] != null }

            parents.forEach { parent ->
                filteredCategoriesCloud.add(parent)
                displayNames.add("📁 ${parent["name"] as String}")

                val parentId = parent["id"] as Long
                val children = subs.filter { (it["parentCategoryId"] as? Number)?.toLong() == parentId }
                children.forEach { child ->
                    filteredCategoriesCloud.add(child)
                    displayNames.add("    └── 💰 ${child["name"] as String}")
                }
            }
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, displayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPremiumTxCategory.adapter = adapter

        val targetSearchId = if (isDebtTransaction) {
            if (binding.rgPremiumTxType.checkedRadioButtonId == binding.rbPremiumTxIncome.id) 104L else 101L
        } else {
            selectedCategoryId
        }

        val selectedIdx = filteredCategoriesCloud.indexOfFirst { (it["id"] as Long) == targetSearchId }
        if (selectedIdx != -1) binding.spinnerPremiumTxCategory.setSelection(selectedIdx)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
