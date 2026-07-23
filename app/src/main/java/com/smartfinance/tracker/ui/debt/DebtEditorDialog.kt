package com.smartfinance.tracker.ui.debt

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.databinding.DialogTransactionPremiumBinding
import com.smartfinance.tracker.utils.FirebaseManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class DebtEditorDialog(
    private val debtItemData: HashMap<String, Any>,
    private val onUpdateAction: () -> Unit
) : DialogFragment() {

    private var _binding: DialogTransactionPremiumBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseManager.getFirestore()
    private val sdfPremium = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val docId = debtItemData["id"] as? String ?: ""
        val contactName = debtItemData["contactName"] as? String ?: "TEMAN"
        val remainingAmount = (debtItemData["remainingAmount"] as? Number)?.toDouble() ?: 0.0
        val isPaid = debtItemData["isPaid"] as? Boolean ?: false
        val debtType = debtItemData["type"] as? String ?: "DEBT"

        val options = arrayOf("✏️ Bayar / Cicil Pinjaman", "🗑️ Hapus Catatan Ini")
        val builder = AlertDialog.Builder(requireContext()).setTitle("Aksi Kontak: $contactName")
        val dialogWrapper = builder.create()

        builder.setItems(options) { _, which ->
            if (which == 0) {
                if (isPaid) {
                    Toast.makeText(context, "Pinjaman ini sudah lunas sepenuhnya!", Toast.LENGTH_SHORT).show()
                    return@setItems
                }

                _binding = DialogTransactionPremiumBinding.inflate(layoutInflater)
                val payDialog = AlertDialog.Builder(requireContext()).setView(binding.root).create()
                payDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                binding.tvDialogTitle.text = "Bayar/Cicil $contactName"
                binding.cardSpinner.visibility = View.GONE
                binding.tvCategoryLabel.visibility = View.GONE
                binding.rgPremiumTxType.visibility = View.GONE
                binding.tvTypeLabel.visibility = View.GONE

                binding.etPremiumTxAmount.hint = "Nominal Pembayaran (Rp)"
                binding.etPremiumTxNote.hint = "Keterangan cicilan (Opsional)"
                binding.etPremiumTxDate.setText(sdfPremium.format(Date()))

                binding.btnCancel.setOnClickListener { payDialog.dismiss() }

                binding.btnSave.text = "Proses Cicilan"
                binding.btnSave.setOnClickListener {
                    val payValue = binding.etPremiumTxAmount.text.toString().toDoubleOrNull() ?: 0.0
                    val userPayNote = binding.etPremiumTxNote.text.toString().trim()
                    val payDateVal = binding.etPremiumTxDate.text.toString().trim()

                    if (payValue > 0.0 && payDateVal.isNotEmpty() && docId.isNotEmpty()) {
                        lifecycleScope.launch {
                            val payTimestamp = try { sdfPremium.parse(payDateVal)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
                            val newRemaining = (remainingAmount - payValue).coerceAtLeast(0.0)
                            
                            firestore.collection("debts").document(docId).update(
                                "remainingAmount", newRemaining,
                                "isPaid", newRemaining <= 0.0
                            ).await()

                            val flowType = if (debtType == "DEBT") "EXPENSE" else "INCOME"
                            val targetCatId = if (debtType == "DEBT") 102L else 103L
                            val targetCatName = if (debtType == "DEBT") "Pembayaran kembali" else "Penagihan Utang"
                            val txId = "tx_${System.currentTimeMillis()}"

                            val payTransactionMap = hashMapOf(
                                "id" to txId,
                                "amount" to payValue,
                                "type" to flowType,
                                "categoryId" to targetCatId,
                                "categoryName" to targetCatName,
                                "note" to "[$targetCatName] ${contactName.uppercase(Locale.ROOT)} - ${userPayNote.ifEmpty { "CICILAN MANUAL CLOUD" }.uppercase(Locale.ROOT)}",
                                "timestamp" to payTimestamp,
                                "debtId" to docId
                            )
                            firestore.collection("transactions").document(txId).set(payTransactionMap).await()
                            
                            Toast.makeText(context, "Cicilan Berhasil Tercatat!", Toast.LENGTH_SHORT).show()
                            onUpdateAction()
                            payDialog.dismiss()
                            dialogWrapper.dismiss()
                        }
                    } else {
                        Toast.makeText(context, "Mohon masukkan nominal cicilan valid!", Toast.LENGTH_SHORT).show()
                    }
                }
                payDialog.show()
            } else if (which == 1) {
                AlertDialog.Builder(requireContext()).apply {
                    setTitle("Hapus Data")
                    setMessage("Apakah Anda yakin ingin menghapus permanen catatan pinjaman dari $contactName?")
                    setPositiveButton("Hapus") { _, _ ->
                        lifecycleScope.launch {
                            firestore.collection("debts").document(docId).delete().await()
                            Toast.makeText(context, "Catatan berhasil dihapus dari awan!", Toast.LENGTH_SHORT).show()
                            onUpdateAction()
                            dialogWrapper.dismiss()
                        }
                    }
                    setNegativeButton("Batal") { d, _ -> d.dismiss() }
                    show()
                }
            }
        }
        return builder.create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
