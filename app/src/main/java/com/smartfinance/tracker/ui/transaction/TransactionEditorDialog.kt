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
    private var currentType = "EXPENSE"
    private var allCategoriesCloud = listOf<Map<String, Any>>()
    private var filteredCategoriesCloud = mutableListOf<Map<String, Any>>()
    
    private lateinit var spinnerCategory: Spinner

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val docId = transactionData["id"] as? String ?: ""
        val currentAmount = (transactionData["amount"] as? Number)?.toLong() ?: 0L
        val currentNote = transactionData["note"] as? String ?: ""
        val currentTimestamp = (transactionData["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val currentCategoryId = (transactionData["categoryId"] as? Number)?.toLong() ?: 0L
        val targetDebtId = transactionData["debtId"] as? String ?: ""
        
        val initialTypeRaw = (transactionData["type"] as? String ?: "EXPENSE").trim().uppercase(Locale.ROOT)
        currentType = if (targetDebtId.isNotEmpty()) "DEBT" else initialTypeRaw

        val viewInflated = LayoutInflater.from(context).inflate(R.layout.dialog_transaction_premium, null, false)
        val innerLayout = viewInflated.findViewById<LinearLayout>(viewInflated.id) ?: (viewInflated as ViewGroup).getChildAt(0) as LinearLayout

        val etAmount = viewInflated.findViewById<TextInputEditText>(R.id.etPremiumTxAmount)
        val etNote = viewInflated.findViewById<TextInputEditText>(R.id.etPremiumTxNote)
        spinnerCategory = viewInflated.findViewById(R.id.spinnerPremiumTxCategory)
        val rgType = viewInflated.findViewById<RadioGroup>(R.id.rgPremiumTxType)
        val rbExpense = viewInflated.findViewById<RadioButton>(R.id.rbPremiumTxExpense)
        val rbIncome = viewInflated.findViewById<RadioButton>(R.id.rbPremiumTxIncome)

        etAmount.setText(currentAmount.toString())
        etNote.setText(currentNote)
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val etDate = EditText(context).apply {
            setText(sdf.format(Date(currentTimestamp)))
            setTextColor(Color.parseColor("#2D3748"))
            textSize = 14f
            setPadding(20, 20, 20, 20)
        }
        
        innerLayout.addView(TextView(context).apply { 
            text = "Tanggal Transaksi (YYYY-MM-DD)"
            textSize = 12f; setTextColor(Color.parseColor("#64748B")); setPadding(20, 10, 0, 0)
        }, 4)
        innerLayout.addView(etDate, 5)

        // Kunci penanda radio group kas keluar/masuk asli dokumen
        if (currentType == "INCOME" || currentType == "DEBT") {
            rbIncome.isChecked = true
        } else {
            rbExpense.isChecked = true
        }

        // Jika transaksi terikat utang piutang, bekukan tipe agar matematika saldo tidak hancur miring
        if (targetDebtId.isNotEmpty()) {
            rbIncome.isEnabled = false
            rbExpense.isEnabled = false
        }

        rgType.setOnCheckedChangeListener { _, checkedId ->
            if (targetDebtId.isEmpty()) {
                currentType = if (checkedId == rbIncome.id) "INCOME" else "EXPENSE"
                mapSpinnerHierarchyCloud(currentCategoryId)
            }
        }

        // Ambil master data kategori cloud secara real-time
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
                allCategoriesCloud = list
                mapSpinnerHierarchyCloud(currentCategoryId)
            } catch (e: Exception) {
                allCategoriesCloud = listOf(
                    mapOf("id" to 101L, "name" to "Hutang", "type" to "DEBT"),
                    mapOf("id" to 104L, "name" to "Piutang", "type" to "DEBT")
                )
                mapSpinnerHierarchyCloud(currentCategoryId)
            }
        }

        // 🔘 BARIS TOMBOL AKSI PREMIUM BAWAH (SIMPAN + BATAL)
        val actionButtonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(40, 20, 40, 40)
            }
        }

        // Tombol Batal Keluar Dialog Aman
        val btnCancel = MaterialButton(context).apply {
            text = "Batal"
            textSize = 14f; cornerRadius = 24; setTextColor(Color.parseColor("#475569"))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 16 }
        }
        
        val btnSave = MaterialButton(context).apply {
            text = "Simpan"
            textSize = 14f; cornerRadius = 24; setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0D9488"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        actionButtonsRow.addView(btnCancel)
        actionButtonsRow.addView(btnSave)
        innerLayout.addView(actionButtonsRow)

        // Baris Atas Khusus Aksi Hapus Lenyap
        val rowHeaderAction = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(10, 10, 20, 0) }
        val btnDelete = TextView(context).apply { text = "🗑️ HAPUS TRANSAKSI"; textSize = 12f; setTextColor(Color.parseColor("#EF4444")); setTypeface(null, Typeface.BOLD); setPadding(20, 20, 20, 20) }
        rowHeaderAction.addView(btnDelete)
        innerLayout.addView(rowHeaderAction, 0)

        val editorDialog = AlertDialog.Builder(context).setView(viewInflated).create()

        btnCancel.setOnClickListener { editorDialog.dismiss() }

        btnDelete.setOnClickListener {
            if (docId.isNotEmpty()) {
                lifecycleScope.launch {
                    if (targetDebtId.isNotEmpty()) {
                        firestore.collection("debts").document(targetDebtId).delete()
                    }
                    firestore.collection("transactions").document(docId).delete().addOnSuccessListener {
                        Toast.makeText(context, "Berhasil dihapus!", Toast.LENGTH_SHORT).show()
                        onUpdateAction()
                        editorDialog.dismiss()
                    }
                }
            }
        }

        btnSave.setOnClickListener {
            val amountVal = etAmount.text.toString().toDoubleOrNull() ?: 0.0
            val noteVal = etNote.text.toString().trim()
            val dateVal = etDate.text.toString().trim()

            if (amountVal > 0.0 && noteVal.isNotEmpty() && dateVal.isNotEmpty() && filteredCategoriesCloud.isNotEmpty() && docId.isNotEmpty()) {
                val parsedDate = try { sdf.parse(dateVal)?.time ?: currentTimestamp } catch (e: Exception) { currentTimestamp }
                
                val selectedCategory = filteredCategoriesCloud[spinnerCategory.selectedItemPosition]
                val catId = selectedCategory["id"] as Long
                val catName = selectedCategory["name"] as String

                lifecycleScope.launch {
                    val upperNote = noteVal.uppercase(Locale.ROOT)
                    
                    if (targetDebtId.isNotEmpty()) {
                        firestore.collection("debts").document(targetDebtId).update(
                            "amount", amountVal,
                            "remainingAmount", amountVal
                        )
                    }

                    // Tentukan penyelarasan tipe data kas akhir agar seirama dengan ReportFragment
                    val finalTxType = if (targetDebtId.isNotEmpty()) {
                        if (catId == 104L) "RECEIVABLE" else "DEBT"
                    } else {
                        if (rgType.checkedRadioButtonId == rbIncome.id) "INCOME" else "EXPENSE"
                    }

                    val updatedTxMap = hashMapOf(
                        "id" to docId,
                        "amount" to amountVal,
                        "note" to upperNote,
                        "timestamp" to parsedDate,
                        "categoryId" to catId,
                        "categoryName" to catName,
                        "type" to finalTxType,
                        "debtId" to targetDebtId
                    )

                    firestore.collection("transactions").document(docId).set(updatedTxMap).addOnSuccessListener {
                        Toast.makeText(context, "Perubahan sukses disimpan!", Toast.LENGTH_SHORT).show()
                        onUpdateAction()
                        editorDialog.dismiss()
                    }
                }
            } else {
                Toast.makeText(context, "Data input tidak valid!", Toast.LENGTH_SHORT).show()
            }
        }

        return editorDialog
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
        spinnerCategory.adapter = adapter

        val selectedIdx = filteredCategoriesCloud.indexOfFirst { (it["id"] as Long) == selectedCategoryId }
        if (selectedIdx != -1) spinnerCategory.setSelection(selectedIdx)
    }
}
