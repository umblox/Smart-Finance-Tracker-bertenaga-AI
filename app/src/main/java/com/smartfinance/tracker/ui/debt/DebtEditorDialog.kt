package com.smartfinance.tracker.ui.debt

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class DebtEditorDialog(
    private val debtItemData: HashMap<String, Any>,
    private val onUpdateAction: () -> Unit
) : DialogFragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val sdfPremium = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val docId = debtItemData["id"] as? String ?: ""
        val contactName = debtItemData["contactName"] as? String ?: "TEMAN"
        val remainingAmount = (debtItemData["remainingAmount"] as? Number)?.toDouble() ?: 0.0
        val isPaid = debtItemData["isPaid"] as? Boolean ?: false
        val debtType = debtItemData["type"] as? String ?: "DEBT"

        val options = arrayOf("✏️ Bayar / Cicil Pinjaman", "🗑️ Hapus Catatan Ini")
        val builder = AlertDialog.Builder(context).setTitle("Aksi Kontak: $contactName")

        val dialogWrapper = AlertDialog.Builder(context).create() // Jembatan penampung utama

        builder.setItems(options) { _, which ->
            if (which == 0) {
                if (isPaid) {
                    Toast.makeText(context, "Pinjaman ini sudah lunas sepenuhnya!", Toast.LENGTH_SHORT).show()
                    return@setItems
                }

                val viewInflated = LayoutInflater.from(context).inflate(R.layout.dialog_transaction_premium, null, false)
                val innerLayout = viewInflated.findViewById<LinearLayout>(viewInflated.id) ?: (viewInflated as ViewGroup).getChildAt(0) as LinearLayout
                
                val etPayAmount = viewInflated.findViewById<TextInputEditText>(R.id.etPremiumTxAmount)
                val etPayNote = viewInflated.findViewById<TextInputEditText>(R.id.etPremiumTxNote)
                val spinnerCategory = viewInflated.findViewById<Spinner>(R.id.spinnerPremiumTxCategory)
                val rgType = viewInflated.findViewById<RadioGroup>(R.id.rgPremiumTxType)
                
                val tvCategoryLabel = viewInflated.findViewWithTag<TextView>("tvCategoryLabel") ?: (spinnerCategory.parent as ViewGroup).getChildAt((spinnerCategory.parent as ViewGroup).indexOfChild(spinnerCategory) - 1) as? TextView

                spinnerCategory.visibility = View.GONE
                tvCategoryLabel?.visibility = View.GONE
                rgType.visibility = View.GONE

                etPayAmount.setHint("Masukkan Nominal Pembayaran (Rp)")
                etPayNote.setHint("Keterangan cicilan (Boleh kosong)")

                innerLayout.addView(TextView(context).apply { 
                    text = "Tanggal & Waktu Cicilan (DD-MM-YYYY • HH:mm)"
                    textSize = 12f; setTextColor(Color.parseColor("#64748B")); setPadding((20 * density).toInt(), (14 * density).toInt(), 0, (4 * density).toInt())
                })
                
                val etPayDate = EditText(context).apply {
                    setText(sdfPremium.format(Date()))
                    setTextColor(Color.parseColor("#2D3748")); textSize = 14.5f
                    setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
                }
                innerLayout.addView(etPayDate)

                val actionButtonsRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; weightSum = 2f
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { 
                        setMargins((20 * density).toInt(), (24 * density).toInt(), (20 * density).toInt(), (20 * density).toInt()) 
                    }
                }
                val btnCancelPay = MaterialButton(context).apply { text = "Batal"; textSize = 14f; cornerRadius = 24; setTextColor(Color.parseColor("#475569")); backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = (12 * density).toInt() } }
                val btnSavePay = MaterialButton(context).apply { text = "Proses Pembayaran"; textSize = 14f; cornerRadius = 24; setTextColor(Color.WHITE); backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0D9488")); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                actionButtonsRow.addView(btnCancelPay)
                actionButtonsRow.addView(btnSavePay)
                innerLayout.addView(actionButtonsRow)

                val payDialog = AlertDialog.Builder(context).setView(viewInflated).create()
                btnCancelPay.setOnClickListener { payDialog.dismiss() }

                btnSavePay.setOnClickListener {
                    val payValue = etPayAmount.text.toString().toDoubleOrNull() ?: 0.0
                    val userPayNote = etPayNote.text.toString().trim()
                    val payDateVal = etPayDate.text.toString().trim()

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
                AlertDialog.Builder(context).apply {
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

        val optionsDialog = builder.create()
        return optionsDialog
    }
}

