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
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
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

    private lateinit var etAmount: EditText
    private lateinit var etNote: EditText
    private lateinit var etDate: EditText
    private lateinit var etContact: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var rbExpense: RadioButton
    private lateinit var rbIncome: RadioButton
    private lateinit var rbDebt: RadioButton

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
        val density = context.resources.displayMetrics.density

        val root = RelativeLayout(context).apply {
            setBackgroundColor(Color.parseColor("#F7FAFC"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val topBar = RelativeLayout(context).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.WHITE)
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        }
        val btnClose = TextView(context).apply { text = "✕"; textSize = 20f; setTextColor(Color.parseColor("#2D3748")); setTypeface(null, Typeface.BOLD) }
        topBar.addView(btnClose)
        val tvTitle = TextView(context).apply { text = "Catat Manual"; textSize = 16f; setTextColor(Color.parseColor("#1A202C")); setTypeface(null, Typeface.BOLD); layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply { addRule(RelativeLayout.CENTER_IN_PARENT) } }
        topBar.addView(tvTitle)
        root.addView(topBar)

        val scrollView = ScrollView(context).apply {
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT).apply { addRule(RelativeLayout.BELOW, topBar.id); bottomMargin = (80 * density).toInt() }
            layoutParams = lp
        }
        val form = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (16 * density).toInt()) }

        form.addView(TextView(context).apply { text = "Nominal Transaksi (Rp)"; setTextColor(Color.parseColor("#718096")); textSize = 11f })
        etAmount = EditText(context).apply { 
            inputType = android.text.InputType.TYPE_CLASS_NUMBER; setTextColor(Color.parseColor("#2D3748"))
            background.mutate().colorFilter = androidx.core.graphics.BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                Color.parseColor("#CBD5E0"), androidx.core.graphics.BlendModeCompat.SRC_ATOP
            )
        }
        form.addView(etAmount)

        form.addView(TextView(context).apply { text = "Jenis Aliran Kas"; setTextColor(Color.parseColor("#718096")); textSize = 11f; setPadding(0, (12 * density).toInt(), 0, 0) })
        val rgType = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL; setPadding(0, (4 * density).toInt(), 0, (8 * density).toInt()) }
        rbExpense = RadioButton(context).apply { text = "Pengeluaran"; id = View.generateViewId(); isChecked = true }
        rbIncome = RadioButton(context).apply { text = "Pemasukan"; id = View.generateViewId() }
        rbDebt = RadioButton(context).apply { text = "Utang-Piutang"; id = View.generateViewId() }
        rgType.addView(rbExpense)
        rgType.addView(rbIncome)
        rgType.addView(rbDebt)
        form.addView(rgType)

        form.addView(TextView(context).apply { text = "Kategori Transaksi"; setTextColor(Color.parseColor("#718096")); textSize = 11f })
        spinnerCategory = Spinner(context).apply { setBackgroundColor(Color.WHITE); setPadding((8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt()) }
        form.addView(spinnerCategory)

        form.addView(TextView(context).apply { text = "Nama Transaksi / Catatan"; setTextColor(Color.parseColor("#718096")); textSize = 11f; setPadding(0, (16 * density).toInt(), 0, 0) })
        etNote = EditText(context).apply { 
            setTextColor(Color.parseColor("#2D3748")); hint = "Contoh: Sisa bayar sembako / pinjam modal"
            background.mutate().colorFilter = androidx.core.graphics.BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                Color.parseColor("#CBD5E0"), androidx.core.graphics.BlendModeCompat.SRC_ATOP
            )
        }
        form.addView(etNote)

        form.addView(TextView(context).apply { text = "Tanggal (YYYY-MM-DD)"; setTextColor(Color.parseColor("#718096")); textSize = 12f; setPadding(0, (16 * density).toInt(), 0, 0) })
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        etDate = EditText(context).apply { 
            setText(sdf.format(Date())); setTextColor(Color.parseColor("#2D3748"))
            background.mutate().colorFilter = androidx.core.graphics.BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                Color.parseColor("#CBD5E0"), androidx.core.graphics.BlendModeCompat.SRC_ATOP
            )
        }
        form.addView(etDate)

        form.addView(TextView(context).apply { text = "Kontak Terkait (Wajib untuk Utang-Piutang)"; setTextColor(Color.parseColor("#718096")); textSize = 11f; setPadding(0, (16 * density).toInt(), 0, 0) })
        val contactRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        etContact = EditText(context).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); setTextColor(Color.parseColor("#2D3748")) }
        val btnPickContact = Button(context).apply { text = "Cari"; backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4A5568")) }
        contactRow.addView(etContact)
        contactRow.addView(btnPickContact)
        // 🔥 FIX MUTLAK: Teks typo siluman telah dihapus murni di baris ini
        form.addView(contactRow)

        scrollView.addView(form)
        root.addView(scrollView)

        val btnSave = Button(context).apply {
            text = "Simpan Transaksi Manual"
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 22 * density; setColor(Color.parseColor("#008080")) }
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, (46 * density).toInt()).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); setMargins((24 * density).toInt(), 0, (24 * density).toInt(), (16 * density).toInt()) }
            layoutParams = lp
        }
        root.addView(btnSave)

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen).setView(root).create()

        btnClose.setOnClickListener { dialog.dismiss() }
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
                    val targetTime = try { sdf.parse(dateVal)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
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
                    val txMap = hashMapOf(
                        "id" to txId,
                        "amount" to amountVal,
                        "type" to finalType,
                        "categoryId" to catId,
                        "categoryName" to catName,
                        "note" to finalNote,
                        "timestamp" to targetTime
                    )
                    
                    firestore.collection("transactions").document(txId).set(txMap).await()

                    if (rbDebt.isChecked) {
                        val debtId = "debt_${System.currentTimeMillis()}"
                        val selectedDebtType = if (catId == 104L) "RECEIVABLE" else "DEBT"
                        
                        val debtMap = hashMapOf(
                            "id" to debtId,
                            "contactName" to contactVal.uppercase(Locale.ROOT),
                            "contactPhoneNumber" to "0812",
                            "amount" to amountVal,
                            "remainingAmount" to amountVal,
                            "type" to selectedDebtType,
                            "note" to "Input Manual Form Cloud",
                            "timestamp" to targetTime,
                            "isPaid" to false
                        )
                        firestore.collection("debts").document(debtId).set(debtMap).await()
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
