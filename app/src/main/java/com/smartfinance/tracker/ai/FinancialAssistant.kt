package com.smartfinance.tracker.ai

import com.smartfinance.tracker.data.local.entity.CategoryEntity
import com.smartfinance.tracker.data.local.entity.DebtEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.data.repository.FinanceRepository
import org.json.JSONObject
import java.util.Calendar

class FinancialAssistant(private val repository: FinanceRepository) {

    // SYSTEM INSTRUCTION: Proteksi ketat instruksi AI sesuai permintaan Anda
    val systemInstruction = """
        Anda adalah Asisten Keuangan Pribadi Eksklusif di dalam aplikasi Smart Finance Tracker.
        Tugas utama Anda hanyalah:
        1. Mencatat transaksi (pemasukan/pengeluaran).
        2. Membuat kategori baru jika diminta.
        3. Membaca data untuk memberikan laporan (harian, mingguan, bulanan, tahunan, atau per kategori).
        
        ATURAN MUTLAK KEAMANAN & SKOP:
        - Tolak mentah-mentah menjawab pertanyaan di luar topik asisten keuangan aplikasi ini (misal: tanya resep makanan, coding, gosip, dsb).
        - JIKA user bertanya tentang teori keuangan umum, tips investasi, atau edukasi finansial makro/mikro secara umum, Anda HARUS MENOLAK. Katakan bahwa Anda hanya bertugas menganalisis dan mengelola data riil yang ada di dalam aplikasi ini saja.
        - Jawablah menggunakan bahasa Indonesia yang santun, ringkas, dan jelas.
        - Jika melakukan pencatatan, pastikan nominal angka terdeteksi dengan benar.
    """.trimIndent()

    // Fungsi untuk memproses teks manual jika Function Calling SDK mengalami kendala (Fallback Parser)
    suspend fun processNaturalLanguage(text: String): String {
        val lowerText = text.lowercase()
        val now = System.currentTimeMillis()

        return when {
            // 1. Logika Input Transaksi Otomatis (Contoh sederhananya)
            lowerText.contains("catat pengeluaran") || lowerText.contains("bayar") || lowerText.contains("beli") -> {
                val amount = extractAmount(lowerText)
                if (amount > 0) {
                    val note = text.replace(Regex("(?i)(catat pengeluaran|beli|bayar|sebesar|rp|\\d+)"), "").trim()
                    repository.insertTransaction(
                        TransactionEntity(
                            amount = amount,
                            type = "EXPENSE",
                            categoryId = 1, // Default Kategori Umum
                            categoryName = "Pengeluaran",
                            note = note,
                            timestamp = now
                        )
                    )
                    "Berhasil mencatat pengeluaran sebesar Rp ${String.format("%,.0f", amount)} untuk '$note'."
                } else {
                    "Saya mendeteksi perintah pengeluaran, tetapi nominal rupiahnya kurang jelas. Bisa diulangi?"
                }
            }

            lowerText.contains("catat pemasukan") || lowerText.contains("gaji") || lowerText.contains("terima uang") -> {
                val amount = extractAmount(lowerText)
                if (amount > 0) {
                    val note = text.replace(Regex("(?i)(catat pemasukan|gaji|terima uang|sebesar|rp|\\d+)"), "").trim()
                    repository.insertTransaction(
                        TransactionEntity(
                            amount = amount,
                            type = "INCOME",
                            categoryId = 2,
                            categoryName = "Pemasukan",
                            note = note,
                            timestamp = now
                        )
                    )
                    "Alhamdulillah, berhasil mencatat pemasukan sebesar Rp ${String.format("%,.0f", amount)} dari '$note'."
                } else {
                    "Nominal pemasukan tidak terdeteksi dengan jelas. Mohon tuliskan angkanya."
                }
            }

            // 2. Logika Pembuatan Kategori Otomatis via Chat
            lowerText.contains("tambah kategori") || lowerText.contains("buat kategori") -> {
                val cleanText = text.replace(Regex("(?i)(tambah kategori|buat kategori)"), "").trim()
                val parts = cleanText.split(" ")
                val catName = parts.firstOrNull() ?: "Baru"
                val type = if (lowerText.contains("pemasukan")) "INCOME" else "EXPENSE"
                
                repository.insertCategory(
                    CategoryEntity(name = catName, type = type, iconName = "ic_income")
                )
                "Kategori $type baru bernama '$catName' berhasil ditambahkan ke sistem."
            }

            // 3. Logika Laporan Otomatis via Chat
            lowerText.contains("laporan") -> {
                getReportSummary(lowerText)
            }

            // Proteksi Sesuai Permintaan Anda: Menolak Pertanyaan Umum Luar Aplikasi
            else -> {
                "Maaf, sebagai asisten finansial aplikasi ini, saya hanya berwenang untuk mencatat transaksi, menambah kategori, dan menyajikan laporan dari data keuangan Anda di aplikasi ini. Saya tidak dapat menjawab pertanyaan atau teori di luar data aplikasi."
            }
        }
    }

    private fun extractAmount(text: String): Double {
        val matches = Regex("\\d+").findAll(text)
        var numberStr = ""
        for (match in matches) {
            numberStr += match.value
        }
        return numberStr.toDoubleOrNull() ?: 0.0
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

