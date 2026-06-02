package com.smartfinance.tracker.ai

import com.smartfinance.tracker.data.repository.FinanceRepository
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import java.util.regex.Pattern

class FinancialAssistant(private val repository: FinanceRepository) {

    val systemInstruction = """
        Anda adalah Asisten Keuangan Pribadi di aplikasi Smart Finance Tracker.
        Tugas Anda adalah menganalisis pesan keuangan pengguna, memberikan saran anggaran,
        dan menjawab pertanyaan terkait pengeluaran, pemasukan, atau hutang secara bijak.
        Jangan menjawab pertanyaan di luar topik keuangan pribadi.
    """.trimIndent()

    suspend fun processNaturalLanguage(input: String): String {
        val lowerInput = input.lowercase()

        // 1. Filter Proteksi Topik
        if (lowerInput.contains("presiden") || lowerInput.contains("cuaca") || lowerInput.contains("siapa kamu")) {
            return "Maaf, saya adalah Asisten Keuangan Pribadi di aplikasi Smart Finance Tracker. Tugas saya hanya membantu Anda mencatat transaksi, membuat kategori, dan memberikan laporan keuangan berdasarkan data Anda."
        }

        // 2. Fitur Cek Saldo Otomatis
        if (lowerInput.contains("berapa saldo") || lowerInput.contains("total uang") || lowerInput.contains("cek saldo")) {
            val transactions = repository.getAllTransactions().first()
            var income = 0.0
            var expense = 0.0
            transactions.forEach { 
                if (it.type == "INCOME") income += it.amount else expense += it.amount 
            }
            val saldo = income - expense
            return "Saldo uang Anda saat ini adalah *Rp ${String.format("%,.0f", saldo)}*. (Total Pemasukan: Rp ${String.format("%,.0f", income)}, Total Pengeluaran: Rp ${String.format("%,.0f", expense)})."
        }

        // 3. Fitur Tulis Transaksi Otomatis via Regex
        val pattern = Pattern.compile("(beli|gaji|bayar|catat|pemasukan|pengeluaran)\\s+([a-zA-Z\\s]+)\\s+(\\d+[\\d\\.]*)")
        val matcher = pattern.matcher(lowerInput)

        if (matcher.find()) {
            val aksi = matcher.group(1) ?: ""
            val namaBarang = matcher.group(2)?.trim() ?: "Umum"
            var nominalStr = matcher.group(3) ?: "0"
            nominalStr = nominalStr.replace(".", "")
            val nominal = nominalStr.toDoubleOrNull() ?: 0.0

            if (nominal > 0.0) {
                val isIncome = aksi.contains("gaji") || aksi.contains("pemasukan")
                val type = if (isIncome) "INCOME" else "EXPENSE"
                
                val newTransaction = TransactionEntity(
                    amount = nominal,
                    type = type,
                    categoryId = if (isIncome) 1 else 2,
                    categoryName = if (isIncome) "Gaji" else "Makanan/Umum",
                    note = namaBarang,
                    timestamp = System.currentTimeMillis()
                )
                repository.insertTransaction(newTransaction)

                return "Berhasil mencatat **${if (isIncome) "Pemasukan" else "Pengeluaran"}** untuk *\"$namaBarang\"* sebesar **Rp ${String.format("%,.0f", nominal)}** ke dalam database."
            }
        }

        return "Maaf, sebagai asisten finansial, format penulisan pencatatan Anda kurang spesifik. Coba ketik: 'beli rokok 10500' atau 'gaji masuk 5000000'."
    }
}
        val saldo = totalIncome - totalExpense

        return """
            📊 *Laporan Keuangan ($periodTitle)*
            =========================
            💰 Total Pemasukan: Rp ${String.format("%,.0f", totalIncome)}
            💸 Total Pengeluaran: Rp ${String.format("%,.0f", totalExpense)}
            -------------------------
            📈 Sisa Saldo: Rp ${String.format("%,.0f", saldo)}
        """.trimIndent()
    }
}

