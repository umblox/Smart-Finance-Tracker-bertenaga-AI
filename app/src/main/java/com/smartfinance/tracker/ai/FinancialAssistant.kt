package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

class FinancialAssistant(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    suspend fun processNaturalLanguage(input: String): String {
        val text = input.lowercase(Locale.ROOT).trim()
        val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

        // =========================================================================
        // FITUR 1: MANAJEMEN LAPORAN KEUANGAN (BACA DATABASE) VIA CHAT LOCAL
        // =========================================================================
        if (text.contains("laporan") || text.contains("rekap") || text.contains("cek pengeluaran")) {
            val transactions = db.transactionDao().getAllTransactions().first()
            var harian = 0.0
            var bulanan = 0.0
            val now = System.currentTimeMillis()
            val calTx = Calendar.getInstance()
            val calNow = Calendar.getInstance().apply { timeInMillis = now }

            for (tx in transactions) {
                calTx.timeInMillis = tx.timestamp
                val diffDays = (now - tx.timestamp) / (1000 * 60 * 60 * 24)
                
                if (tx.type == "EXPENSE") {
                    if (diffDays <= 0) harian += tx.amount
                    if (calTx.get(Calendar.MONTH) == calNow.get(Calendar.MONTH) && calTx.get(Calendar.YEAR) == calNow.get(Calendar.YEAR)) {
                        bulanan += tx.amount
                    }
                }
            }

            return "📊 **LAPORAN OTOMATIS DATABASE HP**\n\n" +
                   "▪️ Total Pengeluaran Hari Ini: **${formatRupiah.format(harian)}**\n" +
                   "▪️ Total Pengeluaran Bulan Ini: **${formatRupiah.format(bulanan)}**\n\n" +
                   "*(Data dibaca langsung secara riil dari penyimpanan SQLite lokal Anda)*"
        }

        // =========================================================================
        // FITUR 2: BUAT & HAPUS KATEGORI LANGSUNG KE SISTEM APLIKASI
        // =========================================================================
        if (text.contains("buat kategori") || text.contains("tambah kategori")) {
            val rawName = input.replace("buat kategori", "", ignoreCase = true)
                .replace("tambah kategori", "", ignoreCase = true).trim()
            if (rawName.isNotEmpty()) {
                val isIncomeType = text.contains("pemasukan")
                val cleanName = rawName.replace("pemasukan", "", ignoreCase = true)
                    .replace("pengeluaran", "", ignoreCase = true).trim()
                
                val newCat = CategoryEntity(
                    name = cleanName,
                    type = if (isIncomeType) "INCOME" else "EXPENSE",
                    iconName = "ic_custom"
                )
                db.categoryDao().insertCategory(newCat)
                return "🗂️ **Kategori Berhasil Dibuat!**\n\nNama: \"$cleanName\"\nTipe: ${if (isIncomeType) "🟢 PEMASUKAN" else "🔴 PENGELUARAN"}\n\n*Sekarang menu dropdown di dashboard sudah ter-update.*"
            }
        }

        if (text.contains("hapus kategori")) {
            val targetName = input.replace("hapus kategori", "", ignoreCase = true).trim()
            val allCats = db.categoryDao().getAllCategories().first()
            val match = allCats.find { it.name.equals(targetName, ignoreCase = true) }
            
            return if (match != null) {
                db.categoryDao().deleteCategory(match)
                "🗑️ Kategori **\"${match.name}\"** telah dihapus dari sistem aplikasi."
            } else {
                "❌ Kategori **\"$targetName\"** tidak ditemukan. Ketik 'daftar kategori' untuk mengecek."
            }
        }

        if (text == "daftar kategori" || text == "lihat kategori") {
            val allCats = db.categoryDao().getAllCategories().first()
            val sb = StringBuilder("🗂️ **DAFTAR KATEGORI SISTEM APLIKASI:**\n\n")
            if (allCats.isEmpty()) return "Belum ada kategori tersimpan."
            allCats.forEach { sb.append("- [${it.type}] ${it.name}\n") }
            return sb.toString()
        }

        // =========================================================================
        // FITUR 3: PENCATATAN TRANSAKSI (FALLBACK LOCAL ENGINE)
        // =========================================================================
        val numberPattern = Pattern.compile("\\d+")
        val numberMatcher = numberPattern.matcher(text)
        
        if (!numberMatcher.find()) {
            return "Format tidak dikenali. Coba ketik perintah seperti:\n" +
                   "• *'beli rokok 20000'*\n" +
                   "• *'berikan laporan keuangan saya'*\n" +
                   "• *'buat kategori Jajan pengeluaran'*"
        }
        
        val amount = numberMatcher.group().toDoubleOrNull() ?: 0.0
        val isIncome = text.contains("gaji") || text.contains("terima") || text.contains("masuk") || text.contains("gajian")
        val type = if (isIncome) "INCOME" else "EXPENSE"
        
        var cleanNote = input.replace(numberMatcher.group(), "", ignoreCase = true)
            .replace("rp", "", ignoreCase = true).replace("beli", "", ignoreCase = true)
            .replace("saya", "", ignoreCase = true).replace("tadi", "", ignoreCase = true)
            .replace("habis", "", ignoreCase = true).replace("jajan", "", ignoreCase = true)
            .replace("catat", "", ignoreCase = true).replace("ya", "", ignoreCase = true).trim()
            
        if (cleanNote.isEmpty()) cleanNote = if (isIncome) "Pemasukan AI" else "Pengeluaran AI"
        
        // Ambil kategori dari database secara dinamis biar aman
        val existingCats = db.categoryDao().getAllCategories().first()
        var matchedCat = existingCats.find { 
            cleanNote.contains(it.name, ignoreCase = true) || it.name.contains(cleanNote, ignoreCase = true) 
        }
        
        // Jika tidak ada yang cocok di string ketikan, cari default berdasarkan tipe transaksi
        if (matchedCat == null) {
            matchedCat = existingCats.find { it.type == type }
        }

        val finalCatId = matchedCat?.id ?: 1L
        val finalCatName = matchedCat?.name ?: (if (isIncome) "Gaji" else "Makanan/Umum")

        db.transactionDao().insertTransaction(TransactionEntity(
            amount = amount,
            type = type,
            categoryId = finalCatId,
            categoryName = finalCatName,
            note = cleanNote.uppercase(Locale.ROOT),
            timestamp = System.currentTimeMillis()
        ))

        return "📝 **BERHASIL DICATAT KE DATABASE LOCAL!**\n\n" +
               "▪️ **Keterangan**: ${cleanNote.uppercase(Locale.ROOT)}\n" +
               "▪️ **Kategori Masuk**: $finalCatName\n" +
               "▪️ **Nominal**: ${formatRupiah.format(amount)}"
    }
}
