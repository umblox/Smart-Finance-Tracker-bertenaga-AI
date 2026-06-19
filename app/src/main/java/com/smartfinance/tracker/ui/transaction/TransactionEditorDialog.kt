package com.smartfinance.tracker.ui.transaction

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import com.smartfinance.tracker.utils.FirebaseManager

class TransactionEditorDialog(
    private val transactionData: HashMap<String, Any>,
    private val onUpdateAction: () -> Unit
) : DialogFragment() {

    private val firestore = FirebaseManager.getFirestore()
    private var currentType = "EXPENSE"
    private var allCategoriesCloud = listOf<Map<String, Any>>()
    private var filteredCategoriesCloud = mutableListOf<Map<String, Any>>()
    
    private lateinit var spinnerCategory: Spinner
    private lateinit var etContact: TextInputEditText
    private lateinit var rgType: RadioGroup
    private lateinit var rbLeft: RadioButton
    private lateinit var rbRight: RadioButton
    private var isDebtTransaction = false

    // ✅ PREMIUM FORMAT SINKRON: Format penanggalan terpadu lengkap dengan Jam dan Menit WIB
    private val sdfPremium = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            requireContext().contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (nameIndex != -1) etContact.setText(cursor.getString(nameIndex))
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val docId = transactionData["id"] as? String ?: ""
        val currentAmount = (transactionData["amount"] as? Number)?.toLong() ?: 0L
        val currentNote = transactionData["note"] as? String ?: ""
        val currentTimestamp = (transactionData["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val currentCategoryId = (transactionData["categoryId"] as? Number)?.toLong() ?: 0L
        val targetDebtId = transactionData["debtId"] as? String ?: ""
        
        isDebtTransaction = targetDebtId.isNotEmpty()

        val viewInflated = LayoutInflater.from(context).inflate(R.layout.dialog_transaction_premium, null, false)
        val innerLayout = viewInflated.findViewById<LinearLayout>(viewInflated.id) ?: (viewInflated as ViewGroup).getChildAt(0) as LinearLayout

        val etAmount = viewInflated.findViewById<TextInputEditText>(R.id.etPremiumTxAmount)
        val etNote = viewInflated.findViewById<TextInputEditText>(R.id.etPremiumTxNote)
        spinnerCategory = viewInflated.findViewById(R.id.spinnerPremiumTxCategory)
        rgType = viewInflated.findViewById(R.id.rgPremiumTxType)
        rbLeft = viewInflated.findViewById(R.id.rbPremiumTxExpense)
        rbRight = viewInflated.findViewById(R.id.rbPremiumTxIncome)

        etAmount.setText(currentAmount.toString())

        if (isDebtTransaction) {
            rbLeft.text = "Saya Berhutang (Hutang)"
            rbRight.text = "Orang Lain Berhutang (Piutang)"
            
            val isReceivableInitial = currentCategoryId == 104L || currentNote.contains("PIUTANG")
            if (isReceivableInitial) rbRight.isChecked = true else rbLeft.isChecked = true
            currentType = "DEBT"

            var extractedName = currentNote.replace(Regex("\\[.*?\\]"), "").trim()
            if (extractedName.contains("-")) {
                extractedName = extractedName.split("-")[0].trim()
            }
            etNote.setText(currentNote.substringAfter("- ").ifEmpty { "INPUT MANUAL" })

            val contactLabel = TextView(context).apply {
                text = "Nama Kontak Terkait:"
                textSize = 12f 
                setTextColor(Color.parseColor("#64748B"))
                setTypeface(null, Typeface.BOLD)
                setPadding((20 * density).toInt(), (14 * density).toInt(), 0, (4 * density).toInt())
            }
            innerLayout.addView(contactLabel)

            val contactRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((20 * density).toInt(), 0, (20 * density).toInt(), 0)
            }
            
            val tilContact = TextInputLayout(context, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox).apply {
                boxStrokeColor = Color.parseColor("#0D9488")
                setBoxCornerRadii(12 * density, 12 * density, 12 * density, 12 * density)
            }
            
            val tilLayoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tilContact.layoutParams = tilLayoutParams

            etContact = TextInputEditText(context).apply {
                setText(extractedName)
                setTextColor(Color.parseColor("#1E293B"))
            }
            tilContact.addView(etContact)
            
            val btnPick = MaterialButton(context).apply {
                text = "👥 HUBUNG"
                textSize = 11f 
                cornerRadius = (10 * density).toInt()
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#475569"))
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                    contactPickerLauncher.launch(intent)
                }
            }
            
            val btnLayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (54 * density).toInt()).apply { leftMargin = (10 * density).toInt() }
            btnPick.layoutParams = btnLayoutParams
            
            contactRow.addView(tilContact)
            contactRow.addView(btnPick)
            innerLayout.addView(contactRow)

        } else {
            val initialTypeRaw = (transactionData["type"] as? String ?: "EXPENSE").trim().uppercase(Locale.ROOT)
            currentType = initialTypeRaw
            etNote.setText(currentNote)
            if (currentType == "INCOME") rbRight.isChecked = true else rbLeft.isChecked = true
        }

        // ✅ FIX PANDUAN VISUAL: Mengarahkan format penulisan input ke premium bertenaga jam menit
        innerLayout.addView(TextView(context).apply { 
            text = "Tanggal Transaksi (DD-MM-YYYY • HH:mm)"
            textSize = 12f; setTextColor(Color.parseColor("#64748B")); setPadding((20 * density).toInt(), (14 * density).toInt(), 0, (4 * density).toInt())
        })
        
        val etDate = EditText(context).apply {
            // ✅ FIX PARSING BAWAAN: Tampilkan data tanggal bawaan asli menggunakan format premium baru
            setText(sdfPremium.format(Date(currentTimestamp)))
            setTextColor(Color.parseColor("#2D3748"))
            textSize = 14.5f
            setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
        }
        innerLayout.addView(etDate)

        rgType.setOnCheckedChangeListener { _, checkedId ->
            if (!isDebtTransaction) {
                currentType = if (checkedId == rbRight.id) "INCOME" else "EXPENSE"
                mapSpinnerHierarchyCloud(currentCategoryId)
            }
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

        val actionButtonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { 
                setMargins((20 * density).toInt(), (24 * density).toInt(), (20 * density).toInt(), (10 * density).toInt()) 
            }
        }

        val btnCancel = MaterialButton(context).apply {
            text = "Batal"
            textSize = 14f; cornerRadius = 24; setTextColor(Color.parseColor("#475569"))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = (12 * density).toInt() }
        }
        
        val btnSave = MaterialButton(context).apply {
            text = "Simpan"
            textSize = 14f; cornerRadius = 24; setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0D9488"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        actionButtonsRow.addView(btnCancel)
        actionButtonsRow.addView(btnSave)
        innerLayout.addView(actionButtonsRow)

        val btnDelete = MaterialButton(context).apply {
            text = "🗑️ HAPUS TRANSAKSI PERMANEN"
            textSize = 12f; cornerRadius = 24; setTextColor(Color.parseColor("#EF4444"))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins((20 * density).toInt(), 0, (20 * density).toInt(), (20 * density).toInt())
            }
        }
        innerLayout.addView(btnDelete)

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
            val noteRawVal = etNote.text.toString().trim()
            val dateVal = etDate.text.toString().trim()

            if (amountVal > 0.0 && noteRawVal.isNotEmpty() && dateVal.isNotEmpty() && filteredCategoriesCloud.isNotEmpty() && docId.isNotEmpty()) {
                // ✅ PARSING EKSEKUSI AMAN: Mengubah string input jam-menit manual ke bentuk Long timestamp murni Firestore
                val parsedDate = try { sdfPremium.parse(dateVal)?.time ?: currentTimestamp } catch (e: Exception) { currentTimestamp }
                
                val selectedCategory = filteredCategoriesCloud[spinnerCategory.selectedItemPosition]
                var catId = selectedCategory["id"] as Long
                var catName = selectedCategory["name"] as String

                lifecycleScope.launch {
                    var finalTxType = if (rgType.checkedRadioButtonId == rbRight.id) "INCOME" else "EXPENSE"
                    var finalNote = noteRawVal.uppercase(Locale.ROOT)

                    if (isDebtTransaction) {
                        val contactNameVal = etContact.text.toString().trim().uppercase(Locale.ROOT)
                        if (contactNameVal.isEmpty()) {
                            Toast.makeText(context, "Nama kontak wajib diisi!", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val isReceivableSelected = rgType.checkedRadioButtonId == rbRight.id
                        catId = if (isReceivableSelected) 104L else 101L
                        catName = if (isReceivableSelected) "Piutang" else "Hutang"
                        
                        val selectedDebtType = if (isReceivableSelected) "RECEIVABLE" else "DEBT"
                        finalTxType = if (isReceivableSelected) "EXPENSE" else "INCOME"
                        finalNote = "[$catName] $contactNameVal - $finalNote"

                        firestore.collection("debts").document(targetDebtId).update(
                            "contactName", contactNameVal,
                            "amount", amountVal,
                            "remainingAmount", amountVal,
                            "type", selectedDebtType,
                            "timestamp", parsedDate
                        )
                    }

                    val updatedTxMap = hashMapOf(
                        "id" to docId,
                        "amount" to amountVal,
                        "note" to finalNote,
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

        val targetSearchId = if (isDebtTransaction) {
            if (rgType.checkedRadioButtonId == rbRight.id) 104L else 101L
        } else {
            selectedCategoryId
        }

        val selectedIdx = filteredCategoriesCloud.indexOfFirst { (it["id"] as Long) == targetSearchId }
        if (selectedIdx != -1) spinnerCategory.setSelection(selectedIdx)
    }
}
