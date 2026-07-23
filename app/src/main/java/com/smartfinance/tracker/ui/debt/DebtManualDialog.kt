package com.smartfinance.tracker.ui.debt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.databinding.DialogTransactionPremiumBinding
import com.smartfinance.tracker.ui.transaction.TransactionActionViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DebtManualDialog(
    private val initialTabFilter: String,
    private val onSavedAction: () -> Unit
) : DialogFragment() {

    private var _binding: DialogTransactionPremiumBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TransactionActionViewModel
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

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) launchNativeContactPicker()
        else Toast.makeText(context, "Akses kontak ditolak.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogTransactionPremiumBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(binding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        viewModel = ViewModelProvider(this)[TransactionActionViewModel::class.java]

        binding.tvDialogTitle.text = "Tambah Utang-Piutang"
        
        binding.cardSpinner.visibility = View.GONE
        binding.tvCategoryLabel.visibility = View.GONE
        binding.tvContactLabel.visibility = View.VISIBLE
        binding.layoutContact.visibility = View.VISIBLE

        binding.rbPremiumTxExpense.text = "Saya Berhutang (Hutang)"
        binding.rbPremiumTxIncome.text = "Orang Lain Berhutang (Piutang)"
        if (initialTabFilter == "DEBT") binding.rbPremiumTxExpense.isChecked = true else binding.rbPremiumTxIncome.isChecked = true

        binding.etPremiumTxDate.setText(sdfPremium.format(Date()))

        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnPickContact.setOnClickListener { checkContactPermissionAndLaunch() }

        binding.btnSave.setOnClickListener {
            val name = binding.etContact.text.toString().trim().uppercase(Locale.ROOT)
            val amountVal = binding.etPremiumTxAmount.text.toString().toDoubleOrNull() ?: 0.0
            val noteText = binding.etPremiumTxNote.text.toString().trim()
            val dateVal = binding.etPremiumTxDate.text.toString().trim()
            val selectedType = if (binding.rgPremiumTxType.checkedRadioButtonId == binding.rbPremiumTxExpense.id) "DEBT" else "RECEIVABLE"

            if (name.isNotEmpty() && amountVal > 0.0 && dateVal.isNotEmpty()) {
                lifecycleScope.launch {
                    val targetTimestamp = try { sdfPremium.parse(dateVal)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
                    val debtId = "debt_${System.currentTimeMillis()}"
                    
                    val debtMap = hashMapOf(
                        "id" to debtId,
                        "contactName" to name,
                        "contactPhoneNumber" to "0812",
                        "amount" to amountVal,
                        "remainingAmount" to amountVal,
                        "type" to selectedType,
                        "note" to noteText.ifEmpty { "Input Manual Buku Utang" },
                        "timestamp" to targetTimestamp,
                        "isPaid" to false
                    )
                    viewModel.saveDebt(debtId, debtMap)

                    val flowType = if (selectedType == "RECEIVABLE") "EXPENSE" else "INCOME"
                    val catId = if (selectedType == "RECEIVABLE") 104L else 101L
                    val catName = if (selectedType == "RECEIVABLE") "Piutang" else "Hutang"
                    val txId = "tx_${System.currentTimeMillis()}"
                    
                    val txMap = hashMapOf(
                        "id" to txId,
                        "amount" to amountVal,
                        "type" to flowType,
                        "categoryId" to catId,
                        "categoryName" to catName,
                        "note" to "[$catName] $name - ${noteText.ifEmpty { "INPUT MANUAL PINJAMAN" }.uppercase(Locale.ROOT)}",
                        "timestamp" to targetTimestamp,
                        "debtId" to debtId
                    )
                    viewModel.saveTransaction(txId, txMap)
                    
                    Toast.makeText(context, "Pinjaman Tersimpan ke Cloud!", Toast.LENGTH_SHORT).show()
                    onSavedAction()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Mohon lengkapi nominal dan nama kontak!", Toast.LENGTH_SHORT).show()
            }
        }
        return dialog
    }

    private fun checkContactPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) launchNativeContactPicker()
        else requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun launchNativeContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
