package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import java.util.regex.Pattern

class FinancialAssistant(private val context: Context) {

    val systemInstruction = "Anda adalah Asisten Keuangan Pribadi di aplikasi Smart Finance Tracker."

    suspend fun processNaturalLanguage(input: String): String {
        val lowerInput = input.lowercase()

        if (lowerInput.contains("presiden") || lowerInput.contains("cuaca")) {
            return "Maaf, saya hanya bisa membantu mencatat transaksi dan keuangan Anda."
        }

        val db = AppDatabase.getDatabase(context)
        val transactionDao = db.transactionDao()

        if (lowerInput.contains("saldo") || lowerInput.contains("total uang")) {
            val transactions = transactionDao.getAllTransactions().first()
            var income = 0.0
            var expense = 0.0
            for (tx in transactions) {
                if (tx.type == "INCOME") income += tx.amount else expense += tx.amount
            }
            return "Saldo Anda saat ini: Rp ${String.format("%,.0f", income - expense)}"
        }

        // PERBAIKAN REGEX: Menangkap nominal angka di mana saja, baik di depan maupun di belakang
        val numberPattern = Pattern.compile("\\d+[\\d\\.]*")
        val numberMatcher = numberPattern.matcher(lowerInput)
        
        if (numberMatcher.find()) {
            val nominalStr = numberMatcher.group().replace(".", "")
            val nominal = nominalStr.toDoubleOrNull() ?: 0.0
            
            if (nominal > 0.0) {
                // Tentukan tipe berdasarkan kata kunci yang fleksibel
                val isIncome = lowerInput.contains("gaji") || lowerInput.contains("pemasukan") || lowerInput.contains("masuk") || lowerInput.contains("gajian")
                val type = if (isIncome) "INCOME" else "EXPENSE"
                
                // Ambil sisa teks sebagai catatan/note barang
                val note = input.replace(numberMatcher.group(), "").replace("gaji", "").replace("beli", "").replace("catat", "").trim()
                val finalNote = if (note.isEmpty()) { if (isIncome) "Pemasukan AI" else "Pengeluaran AI" } else note

                val tx = TransactionEntity(
                    amount = nominal,
                    type = type,
                    categoryId = if (isIncome) 1L else 2L,
                    categoryName = if (isIncome) "Gaji" else "Makanan/Umum",
                    note = finalNote,
                    timestamp = System.currentTimeMillis()
                )
                transactionDao.insertTransaction(tx)
                return "Berhasil mencatat ${if (isIncome) "Pemasukan" else "Pengeluaran"} *\"$finalNote\"* sebesar **Rp ${String.format("%,.0f", nominal)}** ke database."
            }
        }

        return "Format kurang spesifik. Coba ketik: 'beli rokok 15000' atau 'gajian 5000000'."
    }
}
