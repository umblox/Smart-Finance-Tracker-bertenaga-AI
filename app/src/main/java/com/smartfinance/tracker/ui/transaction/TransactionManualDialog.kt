package com.smartfinance.tracker.ui.transaction

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
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
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class TransactionManualDialog(private val onSaved: () -> Unit) : DialogFragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private var currentType = "EXPENSE"
    
    private var allCategoriesCloud = listOf<Map<String, Any>>()
    private var filteredCategoriesCloud = mutableListOf<Map<String, Any>>()

    private lateinit var etAmount: TextInputEditText
    private lateinit var etNote: TextInputEditText
    private lateinit var etDate: TextInputEditText
    private lateinit var etContact: TextInputEditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var rbExpense: RadioButton
    private lateinit var rbIncome: RadioButton
    private lateinit var rbDebt: RadioButton

    private val sdfPremium = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    private val contactPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            contactUri?.let { uri ->
                val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val cursor: Cursor? = requireContext().contentResolver.query(uri, projection, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (nameIdx != -1) etContact.setText(cursor.getString(nameIdx))
                }
                cursor?.close()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) openContactPicker()
        else Toast.makeText(context, "Akses kontak ditolak.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val viewInflated = LayoutInflater.from(context).inflate(R.layout.dialog_transaction_manual_premium, null, false)
        val innerLayout = viewInflated.findViewById<LinearLayout>(viewInflated.id) ?: (viewInflated as ViewGroup).getChildAt(0) as LinearLayout

        etAmount = viewInflated.findViewById(R.id.etManualPremiumAmount)
        etNote = viewInflated.findViewById(R.id.etManualPremiumNote)
        etDate = viewInflated.findViewById(R.id.etManualPremiumDate)
        etContact = viewInflated.findViewById(R.id.etManualPremiumContact)
        spinnerCategory = viewInflated.findViewById(R.id.spinnerManualPremiumCategory)
        rbExpense = viewInflated.findViewById(R.id.rbManualPremiumExpense)
        rbIncome = viewInflated.findViewById(R.id.rbManualPremiumIncome)
        rbDebt = viewInflated.findViewById(R.id.rbManualPremiumDebt)
        val rgType = viewInflated.findViewById<RadioGroup>(R.id.rgManualPremiumType)
        val btnPickContact = viewInflated.findViewById<MaterialButton>(R.id.btnManualPremiumPick)

        etDate.setText(sdfPremium.format(Date()))

        val dialog = AlertDialog.Builder(context).setView(viewInflated).create()

        val actionButtonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(40, 20, 40, 30)
            }
        }

        val btnCancel = MaterialButton(context).apply {
            text = "Batal"
            textSize = 14f; cornerRadius = 24; setTextColor(Color.parseColor("#475569"))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 16 }
            setOnClickListener { dialog.dismiss() }
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

        btnPickContact.setOnClickListener { checkContactPermissionAndOpen() }

        rgType.setOnCheckedChangeListener { _, checkedId ->
            currentType = when (checkedId) {
                rbExpense.id -> "EXPENSE"
                rbIncome.id -> "INCOME"
                else -> "DEBT"
            }
            mapSpinnerHierarchyCloud()
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
                mapSpinnerHierarchyCloud()
            } catch (e: Exception) {
                allCategoriesCloud = listOf(
                    mapOf("id" to 101L, "name" to "Hutang", "type" to "DEBT"),
                    mapOf("id" to 104L, "name" to "Piutang", "type" to "DEBT"),
                    mapOf("id" to 15L, "name" to "Lain-lain / Umum", "type" to "EXPENSE")
                )
                mapSpinnerHierarchyCloud()
            }
        }

        btnSave.setOnClickListener {
            val amountVal = etAmount.text.toString().toDoubleOrNull() ?: 0.0
            val noteVal = etNote.text.toString().trim()
            val dateVal = etDate.text.toString().trim()
            val contactVal = etContact.text.toString().trim()

            if (rbDebt.isChecked && contactVal.isEmpty()) {
                Toast.makeText(context, "Nama kontak wajib diisi untuk transaksi Utang-Piutang!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (amountVal > 0.0 && noteVal.isNotEmpty() && filteredCategoriesCloud.isNotEmpty()) {
                lifecycleScope.launch {
                    val targetTime = try { sdfPremium.parse(dateVal)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
                    val selectedCat = filteredCategoriesCloud[spinnerCategory.selectedItemPosition]
                    val catId = selectedCat["id"] as Long
                    val catName = selectedCat["name"] as String

                    val finalType = when (catId) {
                        101L, 103L -> "INCOME"     
                        102L, 104L -> "EXPENSE"    
                        else -> if (rbIncome.isChecked) "INCOME" else "EXPENSE"
                    }

                    val finalNote = if (rbDebt.isChecked) {
                        "[$catName] $contactVal - $noteVal".uppercase(Locale.ROOT)
                    } else {
                        noteVal.uppercase(Locale.ROOT)
                    }

                    val txId = "tx_${System.currentTimeMillis()}"
                    val generatedDebtId = if (rbDebt.isChecked) "debt_${System.currentTimeMillis()}" else null

                    val txMap = hashMapOf(
                        "id" to txId,
                        "amount" to amountVal,
                        "type" to finalType,
                        "categoryId" to catId,
                        "categoryName" to catName,
                        "note" to finalNote,
                        "timestamp" to targetTime,
                        "debtId" to generatedDebtId
                    )
                    
                    firestore.collection("transactions").document(txId).set(txMap).await()

                    if (rbDebt.isChecked && generatedDebtId != null) {
                        val selectedDebtType = if (catId == 104L) "RECEIVABLE" else "DEBT"
                        
                        // ✅ FIX SINKRONISASI VARIABEL: Mengubah amountValue menjadi nominal input manual amountVal yang sah
                        val debtMap = hashMapOf(
                            "id" to generatedDebtId,
                            "contactName" to contactVal.uppercase(Locale.ROOT),
                            "contactPhoneNumber" to "0812",
                            "amount" to amountVal,
                            "remainingAmount" to amountVal,
                            "type" to selectedDebtType,
                            "note" to "Input Manual Form Cloud",
                            "timestamp" to targetTime,
                            "isPaid" to false
                        )
                        firestore.collection("debts").document(generatedDebtId).set(debtMap).await()
                    }

                    Toast.makeText(context, "Berhasil Disimpan Langsung ke Cloud Server!", Toast.LENGTH_SHORT).show()
                    onSaved()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Mohon lengkapi nominal dan nama transaksi!", Toast.LENGTH_SHORT).show()
            }
        }

        return dialog
    }

    private fun mapSpinnerHierarchyCloud() {
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
    }

    private fun checkContactPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            openContactPicker()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun openContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }
}
