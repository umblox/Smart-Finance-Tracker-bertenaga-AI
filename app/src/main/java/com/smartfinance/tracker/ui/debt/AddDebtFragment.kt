package com.smartfinance.tracker.ui.debt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.DebtEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class AddDebtFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // FORM INPUT UTANG BARU
        val tvLabel = TextView(context).apply { text = "➕ TAMBAH HUTANG / PIUTANG BARU"; textStyle(this) }
        root.addView(tvLabel)

        val etName = EditText(context).apply { hint = "Nama Kontak / Teman" }
        root.addView(etName)

        val btnContact = Button(context).apply { 
            text = "👥 PILIH DARI KONTAK HP"
            setOnClickListener {
                // Simulasi pembacaan kontak lokal HP secara aman
                etName.setText("Kontak Terpilih")
                Toast.makeText(context, "Berhasil membaca data kontak HP!", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnContact)

        val etAmount = EditText(context).apply { hint = "Nominal Jumlah (Rp)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        root.addView(etAmount)

        val etNote = EditText(context).apply { hint = "Keterangan / Catatan Tambahan" }
        root.addView(etNote)

        // Pilihan Jenis Transaksi Hutang
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
                            contactPhoneNumber = "08123456789",
                            amount = amount,
                            remainingAmount = amount,
                            type = type,
                            note = if (note.isEmpty()) "Pinjaman Umum" else note,
                            timestamp = System.currentTimeMillis(),
                            isPaid = false
                        )
                        db.debtDao().insertDebt(debtTx)
                        Toast.makeText(context, "Transaksi Pinjaman Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                        
                        // Bersihkan Form
                        etName.setText("")
                        etAmount.setText("")
                        etNote.setText("")
                        
                        // Refresh Tampilan Daftar List Bawah
                        loadDebtHistory(db, tvContent)
                    }
                } else {
                    Toast.makeText(context, "Harap isi nama kontak dan nominal jumlah!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(btnSave)

        // AREA HISTORY DAFTAR LIST BAWAH
        val tvTitleList = TextView(context).apply { text = "\n🤝 DAFTAR HISTORY HUTANG & PIUTANG"; textStyle(this) }
        root.addView(tvTitleList)
        root.addView(tvContent)

        // Load awal data list
        loadDebtHistory(db, tvContent)

        return root
    }

    private fun textStyle(tv: TextView) {
        tv.textSize = 15f
        tv.setTypeface(null, android.graphics.Typeface.BOLD)
        tv.setTextColor(android.graphics.Color.parseColor("#2D3748"))
    }

    private fun loadDebtHistory(db: AppDatabase, tvContent: TextView) {
        lifecycleScope.launch {
            val debts = db.debtDao().getAllDebts().first()
            val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
            
            if (debts.isEmpty()) {
                tvContent.text = "Tidak ada riwayat hutang atau piutang tercatat."
            } else {
                val builder = StringBuilder()
                debts.forEach { debt ->
                    val jenis = if (debt.type == "DEBT") "⚠️ Hutang Ke:" else "💰 Piutang Di:"
                    val status = if (debt.isPaid) "[LUNAS]" else "[BELUM LUNAS]"
                    builder.append("$jenis ${debt.contactName}\nNominal: ${formatRupiah.format(debt.amount)} $status\nKeterangan: ${debt.note}\n---------------------------\n")
                }
                tvContent.text = builder.toString()
            }
        }
    }
}
