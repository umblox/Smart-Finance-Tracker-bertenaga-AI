package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import com.smartfinance.tracker.data.local.entity.DebtEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

class FinancialAssistant(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

    suspend fun executeSmartJsonCommand(jsonStr: String): String {
        try {
            val json = JSONObject(jsonStr)
            val actionType = json.optString("action_type", "CHAT_ONLY")
            val feedback = json.optString("feedback", "")

            when (actionType) {
                "TRANSACTION" -> {
                    val amount = json.optDouble("amount", 0.0)
                    val catId = json.optLong("category_id", 15L)
                    val catName = json.optString("category_name", "Lain-lain / Umum")
                    val cleanNote = json.optString("clean_note", "Transaksi AI")
                    val type = json.optString("type", "EXPENSE")

                    if (amount > 0.0) {
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount, type = type, categoryId = catId, categoryName = catName,
                            note = cleanNote.uppercase(Locale.ROOT), timestamp = System.currentTimeMillis()
                        ))
                        return "📝 **TRANSAKSI BERHASIL DICATAT!**\n\n" +
                               "▪️ **Kategori**: $catName\n" +
                               "▪️ **Keterangan Bersih**: $cleanNote\n" +
                               "▪️ **Nominal**: ${formatRupiah.format(amount)}"
                    }
                }
                
                "DEBT_RECORD" -> {
                    val amount = json.optDouble("amount", 0.0)
                    val name = json.optString("contact_name", "Teman")
                    val debtType = json.optString("debt_type", "DEBT") // DEBT atau RECEIVABLE

                    if (amount > 0.0) {
                        // FIX: Masukkan data ke dalam database DAO Hutang Piutang secara nyata
                        db.debtDao().insertDebt(DebtEntity(
                            contactName = name, contactPhoneNumber = "0812", amount = amount,
                            remainingAmount = amount, type = debtType, note = "Dicatat otomatis via AI",
                            timestamp = System.currentTimeMillis(), isPaid = false
                        ))

                        // Hubungkan juga ke saldo dashboard utama agar ikut berkurang/bertambah
                        val isReceivable = debtType == "RECEIVABLE"
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount,
                            type = if (isReceivable) "EXPENSE" else "INCOME",
                            categoryId = if (isReceivable) 13L else 12L,
                            categoryName = if (isReceivable) "Piutang (Memberi Pinjaman)" else "Hutang (Saya Meminjam)",
                            note = if (isReceivable) "PINJAMAN KELUAR KE $name" else "PINJAMAN MASUK DARI $name",
                            timestamp = System.currentTimeMillis()
                        ))

                        return "🤝 **TRANSAKSI PINJAMAN BERHASIL DISIMPAN!**\n\n" +
                               "▪️ **Jenis**: ${if (debtType == "DEBT") "⚠️ Hutang Baru" else "💰 Piutang Baru"}\n" +
                               "▪️ **Kontak**: $name\n" +
                               "▪️ **Nominal**: ${formatRupiah.format(amount)}\n\n" +
                               "*Data sudah tersinkronisasi di Dashboard dan Menu History Hutang Piutang.*"
                    }
                }

                "CREATE_CATEGORY" -> {
                    val targetName = json.optString("target_name", "").trim()
                    val catType = json.optString("category_type", "EXPENSE")
                    
                    if (targetName.isNotEmpty()) {
                        db.categoryDao().insertCategory(CategoryEntity(name = targetName, type = catType, iconName = "ic_custom"))
                        return "🗂️ **KATEGORI BARU SUKSES DIBUAT!**\n\n▪️ **Nama**: \"$targetName\"\n▪️ **Tipe**: $catType"
                    }
                }
            }
            return feedback.ifEmpty { jsonStr }
        } catch (e: Exception) {
            // Jika output JSON gagal karena masalah formatting, kembalikan teks aslinya
            return jsonStr
        }
    }

    suspend fun processLocalFallback(input: String, debugError: String): String {
        val text = input.lowercase(Locale.ROOT).trim()

        if (text.contains("laporan") || text.contains("rekap")) {
            val transactions = db.transactionDao().getAllTransactions().first()
            var harian = 0.0
            var bulanan = 0.0
            val now = System.currentTimeMillis()
            val calTx = Calendar.getInstance()
            val calNow = Calendar.getInstance()

            for (tx in transactions) {
                calTx.timeInMillis = tx.timestamp
                val diffDays = (now - tx.timestamp) / (1000 * 60 * 60 * 24)
                if (tx.type == "EXPENSE" && diffDays <= 0) harian += tx.amount
                if (tx.type == "EXPENSE" && calTx.get(Calendar.MONTH) == calNow.get(Calendar.MONTH)) bulanan += tx.amount
            }
            return "📊 **LAPORAN DATABASE DARURAT (LOKAL)**\n\n" +
                   "▪️ Pengeluaran Hari Ini: ${formatRupiah.format(harian)}\n" +
                   "▪️ Pengeluaran Bulan Ini: ${formatRupiah.format(bulanan)}"
        }

        val numberPattern = Pattern.compile("\\d+")
        val numberMatcher = numberPattern.matcher(text)
        if (!numberMatcher.find()) return "Format tidak dikenali sistem lokal cadangan. Error: $debugError"

        val amount = numberMatcher.group().toDoubleOrNull() ?: 0.0
        db.transactionDao().insertTransaction(TransactionEntity(
            amount = amount, type = "EXPENSE", categoryId = 15L, categoryName = "Lain-lain / Umum",
            note = "TRANSAKSI LOKAL CADANGAN", timestamp = System.currentTimeMillis()
        ))

        return "📝 **BERHASIL DICATAT ENGINE CADANGAN LOKAL!**\n\n▪️ **Nominal**: ${formatRupiah.format(amount)}"
    }
}
