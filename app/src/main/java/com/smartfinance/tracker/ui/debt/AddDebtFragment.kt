package com.smartfinance.tracker.ui.debt

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.DebtEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class AddDebtFragment : Fragment() {

    private lateinit var db: AppDatabase
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    private var currentTab = "DEBT" // Default tab: HUTANG SAYA

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        db = AppDatabase.getDatabase(context)

        // ROOT LAYOUT UTAMA
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F7FAFC"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // 1. HEADER & TOMBOL TAMBAH MANUAL
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

        // 2. KARTU DUA RINGKASAN TOTAL (HUTANG VS PIUTANG)
        val summaryLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(44, 0, 44, 24)
        }
        val cardDebt = createSummaryCard("Hutang Saya", "#D69E2E")
        val cardReceivable = createSummaryCard("Piutang (Di Orang)", "#2B6CB0")
        summaryLayout.addView(cardDebt)
        summaryLayout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(24, 1) }) // Jarak spacer
        summaryLayout.addView(cardReceivable)
        root.addView(summaryLayout)

        // 3. TAB SELECTION BAR (HUTANG VS PIUTANG)
        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(44, 0, 44, 16)
        }
        val btnTabDebt = Button(context).apply { text = "Hutang Saya"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        val btnTabReceivable = Button(context).apply { text = "Piutang / Tagihan"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        tabLayout.addView(btnTabDebt)
        tabLayout.addView(btnTabReceivable)
        root.addView(tabLayout)

        // 4. SCROLL CONTAINER DATA LIST
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 0, 44, 44)
        }
        scrollView.addView(listContainer)
        root.addView(scrollView)

        // AKSI EVENT KLIK TOMBOL TAMBAH PINJAMAN MANUAL
        btnAddManual.setOnClickListener {
            showAddDebtManualDialog(listContainer, cardDebt, cardReceivable)
        }

        // WARNA DEFAULT AKTIF TAB
        setTabStyles(btnTabDebt, btnTabReceivable)

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

        // LOAD DATA AWAL RUNTIME
        refreshDebtList(listContainer, cardDebt, cardReceivable)

        return root
    }

    private fun setTabStyles(active: Button, inactive: Button) {
        active.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
        active.setTextColor(Color.WHITE)
        inactive.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
        inactive.setTextColor(Color.parseColor("#4A5568"))
    }

    // ==========================================
    // SEKSI MEMBACA & MEMPERBARUI TAMPILAN DATA (READ)
    // ==========================================
    private fun refreshDebtList(container: LinearLayout, cardDebt: LinearLayout, cardReceivable: LinearLayout) {
        container.removeAllViews()

        lifecycleScope.launch {
            val allDebts = withContext(Dispatchers.IO) { db.debtDao().getAllDebts().first() }
            
            var totalDebtSum = 0.0
            var totalReceivableSum = 0.0

            // Hitung kalkulasi ringkasan atas secara real-time
            allDebts.filter { !it.isPaid }.forEach { 
                if (it.type == "DEBT") totalDebtSum += it.remainingAmount else totalReceivableSum += it.remainingAmount
            }

            // Update isi teks nominal kartu summary atas
            (cardDebt.getChildAt(1) as TextView).text = formatRupiah.format(totalDebtSum)
            (cardReceivable.getChildAt(1) as TextView).text = formatRupiah.format(totalReceivableSum)

            // Filter data yang tampil sesuai tab aktif
            val filteredList = allDebts.filter { it.type == currentTab }

            if (filteredList.isEmpty()) {
                val tvEmpty = TextView(requireContext()).apply {
                    text = "\nTidak ada data catatan aktif pada kategori ini."
                    textSize = 14f
                    setTextColor(Color.parseColor("#A0AEC0"))
                    gravity = Gravity.CENTER
                }
                container.addView(tvEmpty)
                return@launch
            }

            filteredList.forEach { debtItem ->
                val itemCard = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(24, 28, 24, 28)
                    setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                    background.setTint(Color.WHITE)
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 }
                }

                // Bagian Kiri: Info Nama & Status Lunas
                val leftInfo = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                leftInfo.addView(TextView(requireContext()).apply { text = debtItem.contactName; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#2D3748")) })
                
                val statusLabel = if (debtItem.isPaid) "LUNAS ✅" else "Sisa Sisa: ${formatRupiah.format(debtItem.remainingAmount)}"
                leftInfo.addView(TextView(requireContext()).apply { text = statusLabel; textSize = 12f; setTextColor(if (debtItem.isPaid) Color.parseColor("#2F855A") else Color.parseColor("#718096")) })
                itemCard.addView(leftInfo)

                // Bagian Kanan: Jumlah Nominal Awal
                val tvOriginalAmount = TextView(requireContext()).apply {
                    text = formatRupiah.format(debtItem.amount)
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (currentTab == "DEBT") Color.parseColor("#D69E2E") else Color.parseColor("#2B6CB0"))
                    gravity = Gravity.END
                }
                itemCard.addView(tvOriginalAmount)

                // KLIK ITEM UNTUK AKSI DETAIL / BAYAR CICILAN (CRUD - UPDATE/DELETE)
                itemCard.setOnClickListener {
                    showDebtActionOptions(debtItem, container, cardDebt, cardReceivable)
                }

                container.addView(itemCard)
            }
        }
    }

    // ==========================================
    // SEKSI AKSI POP-UP CICILAN / HAPUS (UPDATE & DELETE)
    // ==========================================
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
                    
                    // FORM INPUT SUBSIDI CICILAN
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
                                    
                                    // 1. Update sisa hutang di tabel Debt
                                    withContext(Dispatchers.IO) {
                                        db.debtDao().insertDebt(debt.copy(remainingAmount = newRemaining, isPaid = newRemaining <= 0.0))
                                    }

                                    // 2. Suntikkan arus kas sinkron ke dashboard saldo utama
                                    val flowType = if (debt.type == "DEBT") "EXPENSE" else "INCOME"
                                    withContext(Dispatchers.IO) {
                                        db.transactionDao().insertTransaction(TransactionEntity(
                                            amount = payValue, type = flowType, categoryId = 11L, categoryName = "Cicilan & Pinjaman",
                                            note = "CICILAN MANUAL OLEH ${debt.contactName.uppercase()}", timestamp = System.currentTimeMillis()
                                        ))
                                    }

                                    refreshDebtList(listContainer, cardDebt, cardReceivable)
                                    Toast.makeText(context, "Cicilan Berhasil Diperbarui!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        setNegativeButton("Batal", null)
                        show()
                    }
                } else {
                    // AKSI HAPUS DATA PINJAMAN (DELETE)
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { db.debtDao().deleteDebt(debt) }
                        refreshDebtList(listContainer, cardDebt, cardReceivable)
                        Toast.makeText(context, "Catatan Berhasil Dihapus!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            show()
        }
    }

    // ==========================================
    // SEKSI TAMBAH PINJAMAN MANUAL (CREATE)
    // ==========================================
    private fun showAddDebtManualDialog(listContainer: LinearLayout, cardDebt: LinearLayout, cardReceivable: LinearLayout) {
        val context = requireContext()
        val formLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 20, 44, 20)
        }

        val etName = EditText(context).apply { hint = "Nama Kontak Orang" }
        val etAmount = EditText(context).apply { hint = "Nominal Nominal (ex: 250000)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        
        val spinnerType = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf("HUTANG (Saya Meminjam)", "PIUTANG (Saya Meminjamkan)"))
        }

        formLayout.addView(etName)
        formLayout.addView(etAmount)
        formLayout.addView(TextView(context).apply { text = "\nJenis Pinjaman:"; textSize = 12f })
        formLayout.addView(spinnerType)

        AlertDialog.Builder(context).apply {
            setTitle("📝 Tambah Catatan Pinjaman")
            setView(formLayout)
            setPositiveButton("Simpan") { _, _ ->
                val name = etName.text.toString().trim().uppercase()
                val amountValue = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                val selectedType = if (spinnerType.selectedItemPosition == 0) "DEBT" else "RECEIVABLE"

                if (name.isNotEmpty() && amountValue > 0.0) {
                    lifecycleScope.launch {
                        // 1. Masukkan data ke database Pinjaman (Debt Table)
                        withContext(Dispatchers.IO) {
                            db.debtDao().insertDebt(DebtEntity(
                                contactName = name, contactPhoneNumber = "0812", amount = amountValue,
                                remainingAmount = amountValue, type = selectedType, note = "Input Manual Pas",
                                timestamp = System.currentTimeMillis(), isPaid = false
                            ))
                        }

                        // 2. Efekkan langsung ke saldo kas dashboard utama
                        val flowType = if (selectedType == "RECEIVABLE") "EXPENSE" else "INCOME"
                        val catId = if (selectedType == "RECEIVABLE") 13L else 12L
                        val catName = if (selectedType == "RECEIVABLE") "Piutang (Memberi Pinjaman)" else "Hutang (Saya Meminjam)"
                        
                        withContext(Dispatchers.IO) {
                            db.transactionDao().insertTransaction(TransactionEntity(
                                amount = amountValue, type = flowType, categoryId = catId, categoryName = catName,
                                note = "INPUT MANUAL PINJAMAN OLEH $name", timestamp = System.currentTimeMillis()
                            ))
                        }

                        refreshDebtList(listContainer, cardDebt, cardReceivable)
                        Toast.makeText(context, "Pinjaman Berhasil Tersimpan!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            setNegativeButton("Batal", null)
            show()
        }
    }

    // ==========================================
    // UTILITY LAYOUT GENERATOR
    // ==========================================
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
