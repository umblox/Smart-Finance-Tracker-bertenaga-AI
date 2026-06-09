package com.smartfinance.tracker.ui.debt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.provider.ContactsContract
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.DebtEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.data.remote.FirebaseSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class AddDebtFragment : Fragment() {

    private lateinit var db: AppDatabase
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    private var currentTab = "DEBT"
    
    private var activeContactEditText: EditText? = null

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            
            requireContext().contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val contactName = cursor.getString(nameIndex)
                        activeContactEditText?.setText(contactName)
                    }
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchNativeContactPicker()
        } else {
            Toast.makeText(context, "Izin kontak ditolak. Anda tetap bisa mengetik manual.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        db = AppDatabase.getDatabase(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F7FAFC"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // 1. HEADER
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(44, 44, 44, 20)
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvTitle = TextView(context).apply {
            text = "🤝 PENCATATAN PINJAMAN"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3748"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnAddManual = Button(context).apply {
            text = "➕ PINJAMAN"
            textSize = 12f
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
            setTextColor(Color.WHITE)
        }
        headerLayout.addView(tvTitle)
        headerLayout.addView(btnAddManual)
        root.addView(headerLayout)

        // 2. SUMMARY CARDS
        val summaryLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(44, 0, 44, 24)
        }
        val cardDebt = createSummaryCard("Hutang Saya", "#D69E2E")
        val cardReceivable = createSummaryCard("Piutang (Di Orang)", "#2B6CB0")
        summaryLayout.addView(cardDebt)
        summaryLayout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(24, 1) })
        summaryLayout.addView(cardReceivable)
        root.addView(summaryLayout)

        // 3. TABS
        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(44, 0, 44, 16)
        }
        val btnTabDebt = Button(context).apply { text = "Hutang Saya"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        val btnTabReceivable = Button(context).apply { text = "Piutang / Tagihan"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        tabLayout.addView(btnTabDebt)
        tabLayout.addView(btnTabReceivable)
        root.addView(tabLayout)

        // 4. DATA LISTCONTAINER
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 0, 44, 44)
        }
        scrollView.addView(listContainer)
        root.addView(scrollView)

        btnAddManual.setOnClickListener {
            showAddDebtManualDialog(listContainer, cardDebt, cardReceivable)
        }

        setTabStyles(btnTabDebt, btnTabReceivable)

        // 🔥 FIX SINKRONISASI TOMBOL TAB: Tambahkan refreshDebtList agar database dipicu ulang real-time sesuai filter tab aktif
        btnTabDebt.setOnClickListener {
            currentTab = "DEBT"
            setTabStyles(btnTabDebt, btnTabReceivable)
            refreshDebtList(listContainer, cardDebt, cardReceivable)
        }

        btnTabReceivable.setOnClickListener {
            currentTab = "RECEIVABLE"
            setTabStyles(btnTabReceivable, btnTabDebt)
            refreshDebtList(listContainer, cardDebt, cardReceivable)
        }

        refreshDebtList(listContainer, cardDebt, cardReceivable)

        return root
    }

    private fun setTabStyles(active: Button, inactive: Button) {
        active.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
        active.setTextColor(Color.WHITE)
        inactive.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
        inactive.setTextColor(Color.parseColor("#4A5568"))
    }

    private fun checkContactPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            launchNativeContactPicker()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun launchNativeContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    private fun showAddDebtManualDialog(listContainer: LinearLayout, cardDebt: LinearLayout, cardReceivable: LinearLayout) {
        val context = requireContext()
        val formLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 20, 44, 20)
        }

        val rowBersama = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val etName = EditText(context).apply { 
            hint = "Nama Kontak Orang"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        activeContactEditText = etName

        val btnPickContact = Button(context).apply {
            text = "👥 BERSAMA"
            textSize = 11f
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4A5568"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                checkContactPermissionAndLaunch()
            }
        }
        
        rowBersama.addView(etName)
        rowBersama.addView(btnPickContact)
        formLayout.addView(rowBersama)

        val etAmount = EditText(context).apply { hint = "Nominal (ex: 250000)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        formLayout.addView(etAmount)
        
        formLayout.addView(TextView(context).apply { text = "\nJenis Pinjaman:"; textSize = 12f; setTextColor(Color.parseColor("#718096")) })
        
        val rgType = RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8; bottomMargin = 8 }
        }
        val rbDebt = RadioButton(context).apply { 
            text = "Hutang (Saya Meminjam)"
            id = View.generateViewId()
            textSize = 13f
            isChecked = true
        }
        val rbReceivable = RadioButton(context).apply { 
            text = "Piutang (Saya Meminjamkan)"
            id = View.generateViewId()
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = 16 }
        }
        rgType.addView(rbDebt)
        rgType.addView(rbReceivable)
        formLayout.addView(rgType)

        AlertDialog.Builder(context).apply {
            setTitle("📝 Tambah Catatan Pinjaman")
            setView(formLayout)
            setPositiveButton("Simpan") { _, _ ->
                val name = etName.text.toString().trim().uppercase(Locale.ROOT)
                val amountValue = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                val selectedType = if (rgType.checkedRadioButtonId == rbDebt.id) "DEBT" else "RECEIVABLE"

                if (name.isNotEmpty() && amountValue > 0.0) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            db.debtDao().insertDebt(DebtEntity(
                                contactName = name, contactPhoneNumber = "0812", amount = amountValue,
                                remainingAmount = amountValue, type = selectedType, note = "Input Manual Premium",
                                timestamp = System.currentTimeMillis(), isPaid = false
                            ))
                        }

                        val flowType = if (selectedType == "RECEIVABLE") "EXPENSE" else "INCOME"
                        val catId = if (selectedType == "RECEIVABLE") 104L else 101L
                        val catName = if (selectedType == "RECEIVABLE") "Piutang" else "Hutang"
                        
                        val newTransaction = TransactionEntity(
                            amount = amountValue, 
                            type = flowType, 
                            categoryId = catId, 
                            categoryName = catName,
                            note = "[${catName.uppercase(Locale.ROOT)}] $name - INPUT MANUAL PINJAMAN", 
                            timestamp = System.currentTimeMillis()
                        )

                        val generatedId = withContext(Dispatchers.IO) {
                            db.transactionDao().insertTransaction(newTransaction)
                        }

                        val finalizedTransaction = newTransaction.copy(id = generatedId)
                        FirebaseSyncManager(context).syncSingleTransactionToCloud(finalizedTransaction)
                        Toast.makeText(context, "Pinjaman Berhasil Tersimpan!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            setNegativeButton("Batal", null)
            show()
        }
    }

    private fun refreshDebtList(container: LinearLayout, cardDebt: LinearLayout, cardReceivable: LinearLayout) {
        lifecycleScope.launch {
            db.debtDao().getAllDebts().collect { allDebts ->
                container.removeAllViews()
                
                var totalDebtSum = 0.0
                var totalReceivableSum = 0.0

                allDebts.filter { !it.isPaid }.forEach { 
                    if (it.type == "DEBT") totalDebtSum += it.remainingAmount else totalReceivableSum += it.remainingAmount
                }

                (cardDebt.getChildAt(1) as TextView).text = formatRupiah.format(totalDebtSum)
                (cardReceivable.getChildAt(1) as TextView).text = formatRupiah.format(totalReceivableSum)

                val filteredList = allDebts.filter { it.type == currentTab }

                if (filteredList.isEmpty()) {
                    val tvEmpty = TextView(requireContext()).apply {
                        text = "\nTidak ada data catatan aktif pada kategori ini."
                        textSize = 14f
                        setTextColor(Color.parseColor("#A0AEC0"))
                        gravity = Gravity.CENTER
                    }
                    container.addView(tvEmpty)
                } else {
                    filteredList.forEach { debtItem ->
                        val itemCard = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(24, 28, 24, 28)
                            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                            background.setTint(Color.WHITE)
                            gravity = Gravity.CENTER_VERTICAL
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 }
                        }

                        val leftInfo = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        leftInfo.addView(TextView(requireContext()).apply { text = debtItem.contactName; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#2D3748")) })
                        
                        val statusLabel = if (debtItem.isPaid) "LUNAS ✅" else "Sisa: ${formatRupiah.format(debtItem.remainingAmount)}"
                        leftInfo.addView(TextView(requireContext()).apply { text = statusLabel; textSize = 12f; setTextColor(if (debtItem.isPaid) Color.parseColor("#2F855A") else Color.parseColor("#718096")) })
                        itemCard.addView(leftInfo)

                        val tvOriginalAmount = TextView(requireContext()).apply {
                            text = formatRupiah.format(debtItem.amount)
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(if (currentTab == "DEBT") Color.parseColor("#D69E2E") else Color.parseColor("#2B6CB0"))
                            gravity = Gravity.END
                        }
                        itemCard.addView(tvOriginalAmount)

                        itemCard.setOnClickListener {
                            showDebtActionOptions(debtItem, container, cardDebt, cardReceivable)
                        }

                        container.addView(itemCard)
                    }
                }
            }
        }
    }

    private fun showDebtActionOptions(debt: DebtEntity, listContainer: LinearLayout, cardDebt: LinearLayout, cardReceivable: LinearLayout) {
        val options = arrayOf("✏️ Bayar / Cicil Pinjaman", "🗑️ Hapus Catatan Ini")
        
        AlertDialog.Builder(requireContext()).apply {
            setTitle("Aksi Kontak: ${debt.contactName}")
            setItems(options) { _, which ->
                if (which == 0) {
                    if (debt.isPaid) {
                        Toast.makeText(context, "Pinjaman ini sudah lunas sepenuhnya!", Toast.LENGTH_SHORT).show()
                        return@setItems
                    }
                    
                    val etPay = EditText(context).apply { 
                        hint = "Masukkan jumlah uang (ex: 50000)" 
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    }
                    AlertDialog.Builder(context).apply {
                        setTitle("Bayar / Cicil Pinjaman")
                        setMessage("Sisa tanggungan saat ini: ${formatRupiah.format(debt.remainingAmount)}")
                        setView(etPay)
                        setPositiveButton("Proses") { _, _ ->
                            val payValue = etPay.text.toString().toDoubleOrNull() ?: 0.0
                            if (payValue > 0.0) {
                                lifecycleScope.launch {
                                    val newRemaining = (debt.remainingAmount - payValue).coerceAtLeast(0.0)
                                    withContext(Dispatchers.IO) {
                                        db.debtDao().insertDebt(debt.copy(remainingAmount = newRemaining, isPaid = newRemaining <= 0.0))
                                    }

                                    val flowType = if (debt.type == "DEBT") "EXPENSE" else "INCOME"
                                    val targetCatId = if (debt.type == "DEBT") 102L else 103L
                                    val targetCatName = if (debt.type == "DEBT") "Pembayaran kembali" else "Penagihan Utang"

                                    val payTransaction = TransactionEntity(
                                        amount = payValue, 
                                        type = flowType, 
                                        categoryId = targetCatId, 
                                        categoryName = targetCatName,
                                        note = "[$targetCatName] ${debt.contactName.uppercase(Locale.ROOT)} - CICILAN MANUAL", 
                                        timestamp = System.currentTimeMillis()
                                    )

                                    val generatedId = withContext(Dispatchers.IO) {
                                        db.transactionDao().insertTransaction(payTransaction)
                                    }

                                    val finalizedPayTransaction = payTransaction.copy(id = generatedId)
                                    FirebaseSyncManager(context).syncSingleTransactionToCloud(finalizedPayTransaction)
                                    Toast.makeText(context, "Cicilan Berhasil Diperbarui!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        setNegativeButton("Batal", null)
                        show()
                    }
                } else {
                    lifecycleScope.launch {
                        val clearedDebt = debt.copy(remainingAmount = 0.0, isPaid = true)
                        withContext(Dispatchers.IO) { db.debtDao().insertDebt(clearedDebt) }
                        Toast.makeText(context, "Catatan Berhasil Dibersihkan!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            show()
        }
    }

    private fun createSummaryCard(label: String, colorHex: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            background.setTint(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            
            addView(TextView(context).apply { text = label; textSize = 12f; setTextColor(Color.parseColor("#718096")) })
            addView(TextView(context).apply { text = "Rp 0"; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor(colorHex)) })
        }
    }
}
