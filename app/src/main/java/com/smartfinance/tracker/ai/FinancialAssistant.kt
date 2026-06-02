package com.smartfinance.tracker.ai

import com.smartfinance.tracker.data.repository.FinanceRepository
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.data.local.entity.DebtEntity
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

        // 1. Filter Proteksi Topik Luar Keuangan
        if (lowerInput.contains("presiden") || lowerInput.contains("cuaca") || lowerInput.contains("siapa kamu")) {
            return "Maaf, saya adalah Asisten Keuangan Pribadi di aplikasi Smart Finance Tracker. Tugas saya hanya membantu Anda mencatat transaksi, membuat kategori, dan memberikan laporan keuangan berdasarkan data Anda."
        }

        // 2. Fitur Cek Saldo Otomatis dari Database Real
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

        // 3. Fitur Tulis Otomatis Pencatatan Transaksi Pemasukan / Pengeluaran via AI
        // Pola Regex untuk menangkap: [beli/gaji/catat] [nama_barang] [angka_nominal]
        val pattern = Pattern.compile("(beli|gaji|bayar|catat|pemasukan|pengeluaran)\\s+([a-zA-Z\\s]+)\\s+(\\d+[\\d\\.]*)")
        val matcher = pattern.matcher(lowerInput)

        if (matcher.find()) {
            val aksi = matcher.group(1) ?: ""
            val namaBarang = matcher.group(2)?.trim() ?: "Umum"
            var nominalStr = matcher.group(3) ?: "0"
            nominalStr = nominalStr.replace(".", "") // Bersihkan format titik angka
            val nominal = nominalStr.toDoubleOrNull() ?: 0.0

            if (nominal > 0.0) {
                val isIncome = aksi.contains("gaji") || aksi.contains("pemasukan")
                val type = if (isIncome) "INCOME" else "EXPENSE"
                
                // Simpan transaksi baru langsung ke database lewat repositori
                val newTransaction = TransactionEntity(
                    amount = nominal,
                    type = type,
                    categoryId = if (isIncome) 1 else 2, // Default ID Kategori Gaji atau Makanan
                    categoryName = if (isIncome) "Gaji" else "Makanan/Umum",
                    note = namaBarang,
                    timestamp = System.currentTimeMillis()
                )
                repository.insertTransaction(newTransaction)

                return "Berhasil mencatat **${if (isIncome) "Pemasukan" else "Pengeluaran"}** untuk *\"$namaBarang\"* sebesar **Rp ${String.format("%,.0f", nominal)}** ke dalam database."
            }
        }

        // Jika tidak masuk kriteria lokal, oper pesan penolakan agar dilempar ke sistem Gemini LLM
        return "Maaf, sebagai asisten finansial, format penulisan pencatatan atau pertanyaan Anda kurang spesifik. Coba ketik: 'beli rokok 10500' atau 'gaji masuk 5000000'."
    }
}
    }

    private suspend fun getReportSummary(query: String): String {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        var startTime = calendar.timeInMillis
        var periodTitle = "Hari Ini"

        when {
            query.contains("mingguan") || query.contains("minggu") -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                startTime = calendar.timeInMillis
                periodTitle = "1 Minggu Terakhir"
            }
            query.contains("bulanan") || query.contains("bulan") -> {
                calendar.add(Calendar.MONTH, -1)
                startTime = calendar.timeInMillis
                periodTitle = "1 Bulan Terakhir"
            }
            query.contains("tahunan") || query.contains("tahun") -> {
                calendar.add(Calendar.YEAR, -1)
                startTime = calendar.timeInMillis
                periodTitle = "1 Tahun Terakhir"
            }
            else -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                startTime = calendar.timeInMillis
            }
        }

        val totalIncome = repository.getTotalIncome(startTime, endTime) ?: 0.0
        val totalExpense = repository.getTotalExpense(startTime, endTime) ?: 0.0
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

