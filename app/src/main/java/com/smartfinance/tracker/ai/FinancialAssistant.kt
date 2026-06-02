package com.smartfinance.tracker.ai

import com.smartfinance.tracker.data.repository.FinanceRepository
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import java.util.regex.Pattern

class FinancialAssistant(private val repository: FinanceRepository) {

    val systemInstruction = "Anda adalah Asisten Keuangan Pribadi di aplikasi Smart Finance Tracker."

    suspend fun processNaturalLanguage(input: String): String {
        val lowerInput = input.lowercase()

        // 1. Filter Proteksi
        if (lowerInput.contains("presiden") || lowerInput.contains("cuaca")) {
            return "Maaf, saya hanya bisa membantu mencatat transaksi dan keuangan Anda."
        }

        // 2. Cek Saldo
        if (lowerInput.contains("saldo") || lowerInput.contains("total uang")) {
            val transactions = repository.getAllTransactions().first()
            var income = 0.0
            var expense = 0.0
            transactions.forEach { 
                if (it.type == "INCOME") income += it.amount else expense += it.amount 
            }
            return "Saldo Anda saat ini: Rp ${String.format("%,.0f", income - expense)}"
        }

        // 3. Catat Transaksi Otomatis (Format: beli rokok 15000)
        val pattern = Pattern.compile("(beli|gaji|bayar|catat)\\s+([a-zA-Z\\s]+)\\s+(\\d+)")
        val matcher = pattern.matcher(lowerInput)

        if (matcher.find()) {
            val aksi = matcher.group(1) ?: ""
            val nama = matcher.group(2)?.trim() ?: "Umum"
            val nominalStr = matcher.group(3) ?: "0"
            val nominal = nominalStr.toDoubleOrNull() ?: 0.0

            if (nominal > 0.0) {
                val isIncome = aksi.contains("gaji")
                val type = if (isIncome) "INCOME" else "EXPENSE"
                
                val tx = TransactionEntity(
                    amount = nominal,
                    type = type,
                    categoryId = if (isIncome) 1 else 2,
                    categoryName = if (isIncome) "Gaji" else "Makanan/Umum",
                    note = nama,
                    timestamp = System.currentTimeMillis()
                )
                repository.insertTransaction(tx)
                return "Berhasil mencatat ${if (isIncome) "Pemasukan" else "Pengeluaran"} '$nama' sebesar Rp ${String.format("%,.0f", nominal)}."
            }
        }

        return "Maaf, format kurang spesifik. Coba ketik: 'beli rokok 15000' atau 'gaji 5000000'."
    }
}
