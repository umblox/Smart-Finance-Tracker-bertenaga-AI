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
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

    // Fungsi pembantu mengeksekusi perintah pembuatan kategori terstruktur dari Groq
    suspend fun executeInterceptorCommand(aiResponse: String): String {
        try {
            val line = aiResponse.lines().first { it.contains("CMD_CREATE_CATEGORY") }
            val parts = line.split(":")
            if (parts.size >= 3) {
                val catName = parts[1].trim()
                val catType = parts[2].trim().uppercase()
                
                val newCat = CategoryEntity(name = catName, type = catType, iconName = "ic_custom")
                db.categoryDao().insertCategory(newCat)
                return "🗂️ **KATEGORI BARU BERHASIL DIBUAT VIA GROQ!**\n\n▪️ **Nama**: \"$catName\"\n▪️ **Tipe**: $catType\n\n*Dropdown Spinner di Dashboard sekarang sudah diperbarui secara otomatis.*"
            }
        } catch (e: Exception) {
            return aiResponse
        }
        return aiResponse
    }

    // =========================================================================
    // MODUL AI LOKAL CADANGAN (Hanya jalan jika Groq offline/error/limit)
    // =========================================================================
    suspend fun processLocalFallback(input: String, debugError: String): String {
        val text = input.lowercase(Locale.ROOT).trim()

        // 1. Deteksi Laporan Keuangan Lokal
        if (text.contains("laporan") || text.contains("rekap") || text.contains("cek saldo")) {
            val transactions = db.transactionDao().getAllTransactions().first()
            var harian = 0.0
            var bulanan = 0.0
            val now = System.currentTimeMillis()
            val calTx = Calendar.getInstance()
            val calNow = Calendar.getInstance()

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
            return "📊 **LAPORAN DATABASE DARURAT (LOKAL)**\n\n" +
                   "▪️ Pengeluaran Hari Ini: **${formatRupiah.format(harian)}**\n" +
                   "▪️ Pengeluaran Bulan Ini: **${formatRupiah.format(bulanan)}**\n\n" +
                   "*(Log teknis gangguan Groq: $debugError)*"
        }

        // 2. Deteksi Pencatatan Transaksi Finansial Manual lewat Angka Regex
        val numberPattern = Pattern.compile("\\d+")
        val numberMatcher = numberPattern.matcher(text)
        
        if (!numberMatcher.find()) {
            return "🤖 **Respon AI Lokal**:\n\nMaaf, Groq Cloud sedang offline dan perintah Anda tidak dikenali oleh sistem lokal. " +
                   "Jika ingin mencatat transaksi darurat, gunakan format: *'beli rokok 25000'* atau *'gaji 3000000'*.\n\n*(Detail Error: $debugError)*"
        }

        val amount = numberMatcher.group().toDoubleOrNull() ?: 0.0
        val isIncome = text.contains("gaji") || text.contains("terima") || text.contains("masuk") || text.contains("gajian")
        val type = if (isIncome) "INCOME" else "EXPENSE"

        var cleanNote = input.replace(numberMatcher.group(), "", ignoreCase = true)
            .replace("rp", "", ignoreCase = true).replace("beli", "", ignoreCase = true)
            .replace("jajan", "", ignoreCase = true).replace("tadi", "", ignoreCase = true).trim()

        if (cleanNote.isEmpty()) cleanNote = if (isIncome) "PEMASUKAN LOKAL" else "PENGELUARAN LOKAL"

        val existingCats = db.categoryDao().getAllCategories().first()
        val matchedCat = existingCats.find { it.type == type }
        val finalCatId = matchedCat?.id ?: 1L
        val finalCatName = matchedCat?.name ?: (if (isIncome) "Gaji" else "Umum")

        db.transactionDao().insertTransaction(TransactionEntity(
            amount = amount, type = type, categoryId = finalCatId, categoryName = finalCatName,
            note = cleanNote.uppercase(Locale.ROOT), timestamp = System.currentTimeMillis()
        ))

        return "📝 **BERHASIL DICATAT OLEH ENGINE CADANGAN LOKAL!**\n\n" +
               "▪️ **Keterangan**: ${cleanNote.uppercase(Locale.ROOT)}\n" +
               "▪️ **Kategori**: $finalCatName\n" +
               "▪️ **Nominal**: ${formatRupiah.format(amount)}\n\n" +
               "*(Catatan: Transaksi ini disimpan oleh mesin cadangan karena Groq mengembalikan log: $debugError)*"
    }
}
