package com.smartfinance.tracker.ui.debt

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.DebtEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class AddDebtFragment : Fragment() {

    private lateinit var etName: EditText
    private val CONTACT_PERMISSION_CODE = 1001
    private val CONTACT_PICK_CODE = 1002

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val tvLabel = TextView(context).apply { text = "➕ TAMBAH HUTANG / PIUTANG BARU"; textStyle(this) }
        root.addView(tvLabel)

        etName = EditText(context).apply { hint = "Nama Kontak / Teman" }
        root.addView(etName)

        // TOMBOL PERIZINAN KONTAK ASLI ANDROID
        val btnContact = Button(context).apply { 
            text = "👥 PILIH DARI KONTAK HP"
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    openContactPicker()
                } else {
                    requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), CONTACT_PERMISSION_CODE)
                }
            }
        }
        root.addView(btnContact)

        val etAmount = EditText(context).apply { hint = "Nominal Jumlah (Rp)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        root.addView(etAmount)

        val etNote = EditText(context).apply { hint = "Keterangan / Catatan Tambahan" }
        root.addView(etNote)

        val radioGroup = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        val rbDebt = RadioButton(context).apply { id = View.generateViewId(); text = "Hutang (Saya Pinjam)"; isChecked = true }
        val rbReceivable = RadioButton(context).apply { id = View.generateViewId(); text = "Piutang (Dia Pinjam)" }
        radioGroup.addView(rbDebt)
        radioGroup.addView(rbReceivable)
        root.addView(radioGroup)

        val tvContent = TextView(context).apply { text = "\nMemuat riwayat transaksi pinjaman..."; textSize = 14f }
        val db = AppDatabase.getDatabase(context)

        val btnSave = Button(context).apply {
            text = "SIMPAN TRANSAKSI PINJAMAN"
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#008080"))
            setOnClickListener {
                val name = etName.text.toString().trim()
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                val note = etNote.text.toString().trim()

                if (name.isNotEmpty() && amount > 0.0) {
                    lifecycleScope.launch {
                        val type = if (rbDebt.isChecked) "DEBT" else "RECEIVABLE"
                        val debtTx = DebtEntity(
                            contactName = name,
                            contactPhoneNumber = "08123",
                            amount = amount,
                            remainingAmount = amount,
                            type = type,
                            note = if (note.isEmpty()) "Pinjaman" else note,
                            timestamp = System.currentTimeMillis(),
                            isPaid = false
                        )
                        db.debtDao().insertDebt(debtTx)
                        Toast.makeText(context, "Transaksi Pinjaman Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                        etName.setText("")
                        etAmount.setText("")
                        etNote.setText("")
                        loadDebtHistory(db, tvContent)
                    }
                }
            }
        }
        root.addView(btnSave)

        // FORM INPUT CICILAN PEMBAYARAN HUTANG PIUTANG
        val tvPayLabel = TextView(context).apply { text = "\n💵 INPUT CICILAN PEMBAYARAN REKOR"; textStyle(this) }
        root.addView(tvPayLabel)

        val etDebtTargetId = EditText(context).apply { hint = "Ketik ID Pinjaman Target"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        root.addView(etDebtTargetId)

        val etPayAmount = EditText(context).apply { hint = "Nominal Cicilan (Rp)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        root.addView(etPayAmount)

        val btnPay = Button(context).apply {
            text = "BAYAR CICILAN & UPDATE SALDO"
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2F855A"))
            setOnClickListener {
                val targetId = etDebtTargetId.text.toString().toLongOrNull()
                val payAmount = etPayAmount.text.toString().toDoubleOrNull() ?: 0.0

                if (targetId != null && payAmount > 0.0) {
                    lifecycleScope.launch {
                        val debts = db.debtDao().getAllDebts().first()
                        val matchDebt = debts.find { it.id == targetId }
                        
                        if (matchDebt != null) {
                            val nextRemaining = (matchDebt.remainingAmount - payAmount).coerceAtLeast(0.0)
                            
                            // 1. Update status sisa pinjaman di tabel debt
                            val updatedDebt = matchDebt.copy(
                                remainingAmount = nextRemaining,
                                isPaid = nextRemaining <= 0.0
                            )
                            db.debtDao().insertDebt(updatedDebt)

                            // 2. LOGIKA INTERKONEKSI SALDO UTAMA DASHBOARD:
                            // Jika kita bayar hutang = uang keluar (EXPENSE). Jika teman bayar piutang ke kita = uang masuk (INCOME).
                            val txType = if (matchDebt.type == "DEBT") "EXPENSE" else "INCOME"
                            val newTx = TransactionEntity(
                                amount = payAmount,
                                type = txType,
                                categoryId = 4L,
                                categoryName = "Cicilan Hutang",
                                note = "Bayar cicilan ke/dari ${matchDebt.contactName}",
                                timestamp = System.currentTimeMillis()
                            )
                            db.transactionDao().insertTransaction(newTx)

                            Toast.makeText(context, "Cicilan tercatat! Saldo utama ter-update.", Toast.LENGTH_SHORT).show()
                            etDebtTargetId.setText("")
                            etPayAmount.setText("")
                            loadDebtHistory(db, tvContent)
                        } else {
                            Toast.makeText(context, "ID Pinjaman tidak ditemukan!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        root.addView(btnPay)

        val tvTitleList = TextView(context).apply { text = "\n🤝 HISTORY JALUR PINJAMAN"; textStyle(this) }
        root.addView(tvTitleList)
        root.addView(tvContent)

        loadDebtHistory(db, tvContent)
        return root
    }

    private fun textStyle(tv: TextView) {
        tv.textSize = 14f
        tv.setTypeface(null, android.graphics.Typeface.BOLD)
        tv.setTextColor(android.graphics.Color.parseColor("#2D3748"))
    }

    private fun openContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        startActivityForResult(intent, CONTACT_PICK_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CONTACT_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openContactPicker()
        } else {
            Toast.makeText(requireContext(), "Izin membaca kontak ditolak sistem HP.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CONTACT_PICK_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { contactUri ->
                val cursor = requireContext().contentResolver.query(contactUri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        etName.setText(name)
                    }
                    cursor.close()
                }
            }
        }
    }

    private fun loadDebtHistory(db: AppDatabase, tvContent: TextView) {
        lifecycleScope.launch {
            val debts = db.debtDao().getAllDebts().first()
            val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
            
            if (debts.isEmpty()) {
                tvContent.text = "Tidak ada riwayat pinjaman."
            } else {
                val builder = StringBuilder()
                debts.forEach { debt ->
                    val jenis = if (debt.type == "DEBT") "⚠️ ID [${debt.id}] Hutang ke:" else "💰 ID [${debt.id}] Piutang di:"
                    val status = if (debt.isPaid) "[LUNAS]" else "[SISA PINJAMAN: ${formatRupiah.format(debt.remainingAmount)}]"
                    builder.append("$jenis ${debt.contactName}\nAwal: ${formatRupiah.format(debt.amount)} | $status\n---------------------------\n")
                }
                tvContent.text = builder.toString()
            }
        }
    }
}
