package com.smartfinance.tracker.ui.transaction

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.data.local.DatabaseProvider
import com.smartfinance.tracker.databinding.DialogTransactionManualPremiumBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class TransactionManualDialog(private val onSaved: () -> Unit) : DialogFragment() {

    private var _binding: DialogTransactionManualPremiumBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TransactionViewModel
    
    private var allCategoriesCloud = listOf<Map<String, Any>>()
    private var selectedCategoryMap: Map<String, Any>? = null

    private val sdfPremium = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    private val contactPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            contactUri?.let { uri ->
                val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                requireContext().contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (nameIdx != -1) binding.etManualPremiumContact.setText(cursor.getString(nameIdx))
                    }
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) openContactPicker()
        else Toast.makeText(context, "Akses kontak ditolak.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogTransactionManualPremiumBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(binding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        viewModel = ViewModelProvider(this)[TransactionViewModel::class.java]

        binding.etManualPremiumDate.setText(sdfPremium.format(Date()))

        binding.btnManualCancel.setOnClickListener { dialog.dismiss() }
        binding.btnManualPremiumPick.setOnClickListener { checkContactPermissionAndOpen() }
        binding.btnCategoryPicker.setOnClickListener { showCategoryPickerDialog() }

        lifecycleScope.launch {
            try {
                allCategoriesCloud = viewModel.getCategoriesForDropdown()
            } catch (e: Exception) {
                allCategoriesCloud = listOf(
                    mapOf("id" to 101L, "name" to "Hutang", "type" to "DEBT"),
                    mapOf("id" to 104L, "name" to "Piutang", "type" to "DEBT"),
                    mapOf("id" to 15L, "name" to "Lain-lain / Umum", "type" to "EXPENSE")
                )
            }
        }

        binding.btnManualSave.setOnClickListener {
            val amountVal = binding.etManualPremiumAmount.text.toString().toDoubleOrNull() ?: 0.0
            val noteVal = binding.etManualPremiumNote.text.toString().trim()
            val dateVal = binding.etManualPremiumDate.text.toString().trim()
            val contactVal = binding.etManualPremiumContact.text.toString().trim()

            if (selectedCategoryMap == null) {
                Toast.makeText(context, "Harap pilih Kategori terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val catId = (selectedCategoryMap!!["id"] as? Number)?.toLong() ?: 15L
            val catName = selectedCategoryMap!!["name"] as? String ?: "Umum"
            val typeRaw = (selectedCategoryMap!!["type"] as? String)?.uppercase(Locale.ROOT) ?: "EXPENSE"

            val isDebtTransaction = typeRaw == "DEBT" || typeRaw == "RECEIVABLE" || catId == 101L || catId == 102L || catId == 103L || catId == 104L

            if (isDebtTransaction && contactVal.isEmpty()) {
                Toast.makeText(context, "Nama kontak wajib diisi untuk transaksi Utang-Piutang!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (amountVal > 0.0 && noteVal.isNotEmpty()) {
                lifecycleScope.launch {
                    val currentFormattedDate = sdfPremium.format(Date())
                    val targetTime = try { 
                        if (dateVal == currentFormattedDate) System.currentTimeMillis()
                        else sdfPremium.parse(dateVal)?.time ?: System.currentTimeMillis() 
                    } catch (e: Exception) { System.currentTimeMillis() }

                    val finalType = when (catId) {
                        101L, 103L -> "INCOME"     
                        102L, 104L -> "EXPENSE"    
                        else -> if (typeRaw == "INCOME") "INCOME" else "EXPENSE"
                    }

                    val finalNote = if (isDebtTransaction) "[$catName] $contactVal - $noteVal".uppercase(Locale.ROOT) else noteVal.uppercase(Locale.ROOT)
                    val txId = "tx_${System.currentTimeMillis()}"
                    val generatedDebtId = if (isDebtTransaction) "debt_${System.currentTimeMillis()}" else null

                    val txMap = HashMap<String, Any>()
                    txMap["id"] = txId
                    txMap["amount"] = amountVal
                    txMap["type"] = finalType
                    txMap["categoryId"] = catId
                    txMap["categoryName"] = catName
                    txMap["note"] = finalNote
                    txMap["timestamp"] = targetTime
                    if (generatedDebtId != null) txMap["debtId"] = generatedDebtId
                    
                    viewModel.saveTransaction(txId, txMap)

                    if (isDebtTransaction && generatedDebtId != null) {
                        val selectedDebtType = if (catId == 104L || typeRaw == "RECEIVABLE") "RECEIVABLE" else "DEBT"
                        val debtMap = HashMap<String, Any>()
                        debtMap["id"] = generatedDebtId
                        debtMap["contactName"] = contactVal.uppercase(Locale.ROOT)
                        debtMap["contactPhoneNumber"] = "0812"
                        debtMap["amount"] = amountVal
                        debtMap["remainingAmount"] = amountVal
                        debtMap["type"] = selectedDebtType
                        debtMap["note"] = "Input Manual Form DB"
                        debtMap["timestamp"] = targetTime
                        debtMap["isPaid"] = false
                        
                        viewModel.saveDebt(generatedDebtId, debtMap)
                    }

                    if (finalType == "EXPENSE") {
                        // 🔥 Fix Warning: Menghapus argumen newAmount yang tidak dipakai
                        checkAndTriggerBudgetAlert(catId, catName)
                    }

                    Toast.makeText(context, "Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                    onSaved()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Mohon lengkapi nominal dan nama transaksi!", Toast.LENGTH_SHORT).show()
            }
        }

        return dialog
    }

    // 🔥 Fix Warning: Menghapus parameter newAmount yang tidak terpakai
    private suspend fun checkAndTriggerBudgetAlert(categoryId: Long, categoryName: String) = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.db
        try {
            val budgetDoc = db.budgetDao().getByCategoryId(categoryId)

            if (budgetDoc != null) {
                val limitAmount = budgetDoc.limitAmount

                if (limitAmount > 0.0) {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                    val startOfMonth = cal.timeInMillis

                    val txs = db.transactionDao().getByCategoryId(categoryId)
                    var totalSpent = 0.0 
                    for (tx in txs) {
                        if (tx.timestamp >= startOfMonth && (tx.type == "EXPENSE" || tx.type == "RECEIVABLE")) { 
                            totalSpent += tx.amount
                        }
                    }

                    if (totalSpent >= (limitAmount * 0.8)) {
                        withContext(Dispatchers.Main) {
                            com.smartfinance.tracker.worker.AiWorkerManager.triggerBudgetAlert(
                                requireContext(),
                                categoryName,
                                totalSpent,
                                limitAmount
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showCategoryPickerDialog() {
        val typeRaw = if (binding.rgManualPremiumType.checkedRadioButtonId == binding.rbManualPremiumIncome.id) "INCOME" else "EXPENSE"
        val currentFilter = if (binding.rgManualPremiumType.checkedRadioButtonId == binding.rbManualPremiumDebt.id) "DEBT" else typeRaw
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

    private fun checkContactPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) openContactPicker()
        else requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun openContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
