package com.smartfinance.tracker.ui.debt

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
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

class DebtEditorDialog(
    private val debtItemData: HashMap<String, Any>,
    private val onUpdateAction: () -> Unit
) : DialogFragment() {

    private val sdfPremium = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val docId = debtItemData["id"] as? String ?: ""
        val contactName = debtItemData["contactName"] as? String ?: "TEMAN"
        val remainingAmount = (debtItemData["remainingAmount"] as? Number)?.toDouble() ?: 0.0
        val isPaid = debtItemData["isPaid"] as? Boolean ?: false
        val debtType = debtItemData["type"] as? String ?: "DEBT"

        val options = arrayOf(
            getString(R.string.debt_action_pay),
            getString(R.string.debt_action_delete)
        )
        
        // 🔥 FIX: Menggunakan MaterialAlertDialogBuilder agar UI melengkung, elegan, & ramah Dark Mode
        return MaterialAlertDialogBuilder(requireContext(), R.style.Theme_SmartFinance)
            .setTitle(getString(R.string.debt_action_title, contactName))
            .setItems(options) { _, which ->
                if (which == 0) {
                    if (isPaid) {
                        Toast.makeText(context, getString(R.string.debt_toast_paid_off), Toast.LENGTH_SHORT).show()
                        return@setItems
                    }

                    val localBinding = DialogTransactionPremiumBinding.inflate(layoutInflater)
                    val activityContext = requireActivity()
                    
                    val payDialog = MaterialAlertDialogBuilder(activityContext, R.style.Theme_SmartFinance)
                        .setView(localBinding.root)
                        .create()
                        
                    payDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                    localBinding.tvDialogTitle.text = getString(R.string.debt_pay_title, contactName)
                    
                    localBinding.btnCategoryPicker.visibility = View.GONE
                    localBinding.tvCategoryLabel.visibility = View.GONE
                    localBinding.rgPremiumTxType.visibility = View.GONE
                    localBinding.tvTypeLabel.visibility = View.GONE

                    val parentAmountLayout = localBinding.etPremiumTxAmount.parent.parent as? com.google.android.material.textfield.TextInputLayout
                    val parentNoteLayout = localBinding.etPremiumTxNote.parent.parent as? com.google.android.material.textfield.TextInputLayout
                    
                    parentAmountLayout?.hint = getString(R.string.debt_pay_hint_amount)
                    parentNoteLayout?.hint = getString(R.string.debt_pay_hint_note)
                    
                    localBinding.etPremiumTxDate.setText(sdfPremium.format(Date()))

                    localBinding.btnCancel.setOnClickListener { payDialog.dismiss() }

                    localBinding.btnSave.text = getString(R.string.debt_pay_btn)
                    localBinding.btnSave.setOnClickListener {
                        val payValue = localBinding.etPremiumTxAmount.text.toString().toDoubleOrNull() ?: 0.0
                        val userPayNote = localBinding.etPremiumTxNote.text.toString().trim()
                        val payDateVal = localBinding.etPremiumTxDate.text.toString().trim()

                        if (payValue > 0.0 && payDateVal.isNotEmpty() && docId.isNotEmpty()) {
                            
                            val safeScope = activityContext.lifecycleScope
                            val safeViewModel = ViewModelProvider(activityContext)[DebtViewModel::class.java]
                            
                            safeScope.launch {
                                try {
                                    val currentFormattedDate = sdfPremium.format(Date())
                                    val payTimestamp = try { 
                                        if (payDateVal == currentFormattedDate) System.currentTimeMillis()
                                        else sdfPremium.parse(payDateVal)?.time ?: System.currentTimeMillis() 
                                    } catch (e: Exception) { System.currentTimeMillis() }
                                    
                                    val newRemaining = (remainingAmount - payValue).coerceAtLeast(0.0)
                                    
                                    val flowType = if (debtType == "DEBT") "EXPENSE" else "INCOME"
                                    val targetCatId = if (debtType == "DEBT") 102L else 103L
                                    val targetCatName = if (debtType == "DEBT") "Pembayaran kembali" else "Penagihan Utang"
                                    val txId = "tx_${System.currentTimeMillis()}"

                                    val payTransactionMap = HashMap<String, Any>()
                                    payTransactionMap["id"] = txId
                                    payTransactionMap["amount"] = payValue
                                    payTransactionMap["type"] = flowType
                                    payTransactionMap["categoryId"] = targetCatId
                                    payTransactionMap["categoryName"] = targetCatName
                                    payTransactionMap["note"] = "[$targetCatName] ${contactName.uppercase(Locale.ROOT)} - ${userPayNote.ifEmpty { "CICILAN MANUAL" }.uppercase(Locale.ROOT)}"
                                    payTransactionMap["timestamp"] = payTimestamp
                                    payTransactionMap["debtId"] = docId
                                    
                                    safeViewModel.processDebtInstallment(docId, newRemaining, newRemaining <= 0.0, txId, payTransactionMap)
                                    
                                    Toast.makeText(activityContext, getString(R.string.debt_toast_installment_success), Toast.LENGTH_SHORT).show()
                                    onUpdateAction()
                                    payDialog.dismiss()
                                } catch (e: Exception) {
                                    Toast.makeText(activityContext, getString(R.string.debt_toast_installment_fail), Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(activityContext, getString(R.string.debt_toast_invalid_amount), Toast.LENGTH_SHORT).show()
                        }
                    }
                    payDialog.show()
                    
                } else if (which == 1) {
                    MaterialAlertDialogBuilder(requireContext(), R.style.Theme_SmartFinance).apply {
                        setTitle(getString(R.string.debt_delete_title))
                        setMessage(getString(R.string.debt_delete_message, contactName))
                        setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                            val safeScope = requireActivity().lifecycleScope
                            val safeViewModel = ViewModelProvider(requireActivity())[DebtViewModel::class.java]
                            
                            safeScope.launch {
                                try {
                                    safeViewModel.deleteDebtPermanently(docId)
                                    Toast.makeText(requireActivity(), getString(R.string.debt_toast_deleted), Toast.LENGTH_SHORT).show()
                                    onUpdateAction()
                                } catch (e: Exception) {
                                    Toast.makeText(requireActivity(), getString(R.string.debt_toast_delete_fail), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        setNegativeButton(getString(R.string.action_cancel), null)
                        show()
                    }
                }
            }.create()
    }
}
