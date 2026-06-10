package com.smartfinance.tracker.ui.transaction

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class TransactionEditorDialog(
    private val transactionData: HashMap<String, Any>,
    private val onUpdateAction: () -> Unit
) : DialogFragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private var categoryListCloud = listOf<Map<String, Any>>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val docId = transactionData["id"] as? String ?: ""
        val currentAmount = (transactionData["amount"] as? Number)?.toLong() ?: 0L
        val currentNote = transactionData["note"] as? String ?: ""
        val currentTimestamp = (transactionData["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val currentCategoryId = (transactionData["categoryId"] as? Number)?.toLong() ?: 0L
        val originalType = transactionData["type"] as? String ?: "EXPENSE"
        
        // 🔥 AKURAT: Tangkap jembatan KTP debtId dari map payload transaksi
        val targetDebtId = transactionData["debtId"] as? String ?: ""

        val viewInflated = LayoutInflater.from(context).inflate(R.layout.dialog_transaction_premium, null, false)

        val etAmount = viewInflated.findViewById<TextInputEditText>(R.id.etPremiumTxAmount)
        val etNote = viewInflated.findViewById<TextInputEditText>(R.id.etPremiumTxNote)
        val etCategoryManual = viewInflated.findViewById<TextInputEditText>(R.id.etPremiumTxCategory)
        val rgType = viewInflated.findViewById<RadioGroup>(R.id.rgPremiumTxType)
        val rbIncome = viewInflated.findViewById<RadioButton>(R.id.rbPremiumTxIncome)
        val rbExpense = viewInflated.findViewById<RadioButton>(R.id.rbPremiumTxExpense)

        etAmount.setText(currentAmount.toString())
        etNote.setText(currentNote)
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val etDate = EditText(context).apply {
            setText(sdf.format(Date(currentTimestamp)))
            setTextColor(Color.parseColor("#2D3748"))
            textSize = 14f
            setPadding(20, 20, 20, 20)
        }
        (viewInflated as LinearLayout).addView(TextView(context).apply { 
            text = "Tanggal Transaksi (YYYY-MM-DD)"
            textSize = 12f; setTextColor(Color.parseColor("#64748B")); setPadding(20, 10, 0, 0)
        }, 4)
        viewInflated.addView(etDate, 5)

        if (originalType == "INCOME" || originalType == "DEBT") {
            rbIncome.isChecked = true
        } else {
            rbExpense.isChecked = true
        }

        lifecycleScope.launch {
            try {
                val snapshot = firestore.collection("categories").get().await()
                val list = ArrayList<Map<String, Any>>()
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val mutableData = HashMap(data)
                    mutableData["id"] = doc.getLong("id") ?: 0L
                    list.add(mutableData)
                }
                categoryListCloud = list
                val currentCat = categoryListCloud.find { (it["id"] as Long) == currentCategoryId }
                etCategoryManual.setText(currentCat?.get("name") as? String ?: "Piutang")
            } catch (e: Exception) {
                etCategoryManual.setText("Piutang")
            }
        }

        val btnSave = MaterialButton(context).apply {
            text = "Simpan Perubahan Premium"
            textSize = 14f; cornerRadius = 24; setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0D9488"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(40, 20, 40, 40) }
        }
        viewInflated.addView(btnSave)

        val rowHeaderAction = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(10, 10, 20, 0) }
        val btnDelete = TextView(context).apply { text = "🗑️ HAPUS TRANSAKSI"; textSize = 12f; setTextColor(Color.parseColor("#EF4444")); setTypeface(null, Typeface.BOLD); setPadding(20, 20, 20, 20) }
        rowHeaderAction.addView(btnDelete)
        viewInflated.addView(rowHeaderAction, 0)

        val editorDialog = AlertDialog.Builder(context).setView(viewInflated).create()

        // 🗑️ ACTION SINKRONISASI HAPUS MUTLAK BERBASIS TARGET ID KTP
        btnDelete.setOnClickListener {
            if (docId.isNotEmpty()) {
                lifecycleScope.launch {
                    // 🔥 BERES: Jika membawa KTP debtId, langsung lenyapkan dokumen pinjamannya di awan!
                    if (targetDebtId.isNotEmpty()) {
                        firestore.collection("debts").document(targetDebtId).delete()
                    }
                    
                    firestore.collection("transactions").document(docId).delete().addOnSuccessListener {
                        Toast.makeText(context, "Berhasil dihapus dari Cloud!", Toast.LENGTH_SHORT).show()
                        onUpdateAction()
                        editorDialog.dismiss()
                    }
                }
            }
        }

        // ✏️ ACTION SINKRONISASI EDIT MUTLAK BERBASIS TARGET ID KTP
        btnSave.setOnClickListener {
            val amountVal = etAmount.text.toString().toDoubleOrNull() ?: 0.0
            val noteVal = etNote.text.toString().trim()
            val dateVal = etDate.text.toString().trim()
            val categoryNameManual = etCategoryManual.text.toString().trim()

            if (amountVal > 0.0 && noteVal.isNotEmpty() && dateVal.isNotEmpty() && docId.isNotEmpty()) {
                val parsedDate = try { sdf.parse(dateVal)?.time ?: currentTimestamp } catch (e: Exception) { currentTimestamp }
                val selectedType = if (rgType.checkedRadioButtonId == rbIncome.id) "INCOME" else "EXPENSE"

                lifecycleScope.launch {
                    val upperNote = noteVal.uppercase(Locale.ROOT)
                    
                    // 🔥 BERES: Jika membawa KTP debtId, update nilai nominal utang pasangannya secara presisi
                    if (targetDebtId.isNotEmpty()) {
                        firestore.collection("debts").document(targetDebtId).update(
                            "amount", amountVal,
                            "remainingAmount", amountVal
                        )
                    }

                    val targetCatId = if (selectedType == "INCOME") 101L else 102L

                    val updatedTxMap = hashMapOf(
                        "id" to docId,
                        "amount" to amountVal,
                        "note" to upperNote,
                        "timestamp" to parsedDate,
                        "categoryId" to targetCatId,
                        "categoryName" to categoryNameManual,
                        "type" to selectedType,
                        "debtId" to targetDebtId // Tetap amankan KTP pengikatnya
                    )

                    firestore.collection("transactions").document(docId).set(updatedTxMap).addOnSuccessListener {
                        Toast.makeText(context, "Perubahan sukses disimpan di Cloud!", Toast.LENGTH_SHORT).show()
                        onUpdateAction()
                        editorDialog.dismiss()
                    }
                }
            } else {
                Toast.makeText(context, "Semua data wajib diisi secara valid!", Toast.LENGTH_SHORT).show()
            }
        }

        return editorDialog
    }
}
