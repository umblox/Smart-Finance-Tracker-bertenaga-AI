package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.data.local.entity.DebtEntity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class FinancialAssistant(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    suspend fun parseAndExecuteRawAiResponse(rawText: String): String {
        val cleanJsonStr = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        try {
            val json = JSONObject(cleanJsonStr)
            val actionType = json.optString("action_type", "TRANSACTION").trim().uppercase(Locale.ROOT)
            var aiResponse = json.optString("ai_response", "").trim()

            // 1. PARSING WAKTU KALENDER DINAMIS
            val dateStr = json.optString("transaction_date", "").trim()
            val sdfParser = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID"))
            val targetTimestamp = if (dateStr.isNotEmpty()) {
                try { sdfParser.parse(dateStr)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
            } else {
                System.currentTimeMillis()
            }

            // 2. EKSTRAKSI NOMINAL AMAN
            val amount = json.optDouble("amount", 0.0)
            val finalAmount = if (amount == 0.0) json.optString("amount", "0").toDoubleOrNull() ?: 0.0 else amount

            when (actionType) {
                "VIEW_REPORT" -> {
                    // AMBIL DATA DARI DATABASE UNTUK MERESPONS LAPORAN SECARA NYATA
                    val allTx = db.transactionDao().getAllTransactions().first()
                    var incSum = 0.0
                    var expSum = 0.0
                    allTx.forEach { if (it.type == "INCOME") incSum += it.amount else expSum += it.amount }
                    
                    return "📊 **Ringkasan Laporan Keuangan Anda, Mam:**\n\n" +
                           "🟢 Total Pemasukan: ${formatRupiah.format(incSum)}\n" +
                           "🔴 Total Pengeluaran: ${formatRupiah.format(expSum)}\n" +
                           "💰 Saldo Bersih Dompet: ${formatRupiah.format(incSum - expSum)}\n\n" +
                           "Untuk visualisasi diagram lingkaran dan distribusi kategori terperinci, silakan klik langsung area grafik di halaman utama Dashboard ya!"
                }

                "TRANSACTION" -> {
                    val cleanNote = json.optString("clean_note", "Transaksi AI").trim().uppercase(Locale.ROOT)
                    var type = json.optString("type", "EXPENSE").trim().uppercase(Locale.ROOT)
                    
                    if (cleanNote.contains("GAJI") || cleanNote.contains("PAYDAY") || cleanNote.contains("PEMASUKAN")) {
                        type = "INCOME"
                    }

                    var catId = json.optLong("category_id", 15L)
                    var catName = json.optString("category_name", "Lain-lain / Umum").trim()

                    if (type == "INCOME") { catId = 1L; catName = "Gaji & Pendapatan" }

                    if (finalAmount > 0.0) {
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = finalAmount, type = type, categoryId = catId, categoryName = catName, note = cleanNote, timestamp = targetTimestamp
                        ))
                    }
                }
                
                "DEBT_RECORD" -> {
                    val name = json.optString("contact_name", "TEMAN").trim().uppercase(Locale.ROOT)
                    // NORMALISASI KAKU: Paksa string jenis utang menjadi uppercase agar lolos filter view
                    var debtType = json.optString("debt_type", "DEBT").trim().uppercase(Locale.ROOT)
                    
                    // Proteksi logika arah utang jika Llama mendadak linglung
                    val cleanNote = json.optString("clean_note", "").uppercase(Locale.ROOT)
                    if (cleanNote.contains("SAYA PINJAM") || cleanNote.contains("SAYA NGUTANG") || cleanNote.contains("SAYA BERHUTANG")) {
                        debtType = "DEBT"
                    } else if (cleanNote.contains("MEMINJAMKAN") || cleanNote.contains("DIPINJAM")) {
                        debtType = "RECEIVABLE"
                    }

                    if (finalAmount > 0.0) {
                        db.debtDao().insertDebt(DebtEntity(
                            contactName = name, contactPhoneNumber = "0812", amount = finalAmount, remainingAmount = finalAmount, 
                            type = debtType, note = "Otomatis via Chat AI", timestamp = targetTimestamp, isPaid = false
                        ))

                        val flowType = if (debtType == "DEBT") "INCOME" else "EXPENSE"
                        val catId = if (debtType == "DEBT") 12L else 13L
                        val catName = if (debtType == "DEBT") "Hutang (Saya Meminjam)" else "Piutang (Memberi Pinjaman)"

                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = finalAmount, type = flowType, categoryId = catId, categoryName = catName, 
                            note = if (debtType == "DEBT") "HUTANG MASUK DARI $name" else "PIUTANG KELUAR KE $name", timestamp = targetTimestamp
                        ))
                    }
                }
                
                "DEBT_PAYMENT" -> {
                    val targetId = json.optLong("debt_id", -1L)
                    val payAmount = json.optDouble("pay_amount", 0.0)
                    val finalPayAmount = if (payAmount == 0.0) json.optString("pay_amount", "0").toDoubleOrNull() ?: 0.0 else payAmount

                    if (targetId != -1L && finalPayAmount > 0.0) {
                        val debts = db.debtDao().getAllDebts().first()
                        val matchDebt = debts.find { it.id == targetId }
                        
                        if (matchDebt != null) {
                            val nextRemaining = (matchDebt.remainingAmount - finalPayAmount).coerceAtLeast(0.0)
                            db.debtDao().insertDebt(matchDebt.copy(
                                remainingAmount = nextRemaining, isPaid = nextRemaining <= 0.0
                            ))

                            val txType = if (matchDebt.type == "DEBT") "EXPENSE" else "INCOME"
                            db.transactionDao().insertTransaction(TransactionEntity(
                                amount = finalPayAmount, type = txType, categoryId = 11L, categoryName = "Cicilan & Pinjaman",
                                note = "CICILAN OLEH ${matchDebt.contactName}", timestamp = targetTimestamp
                            ))
                        }
                    }
                }
            }
            
            return aiResponse.ifEmpty { "Catatan keuangan berhasil diproses ke sistem, Mam!" }

        } catch (e: Exception) {
            // RECOVERY KAS KASAR (BENTENG PERTAHANAN AKHIR)
            val upperText = cleanJsonStr.uppercase(Locale.ROOT)
            var detectedAmount = 0.0
            val numbers = Regex("\\d+").findAll(upperText).map { it.value.toDoubleOrNull() ?: 0.0 }.toList()
            if (numbers.isNotEmpty()) detectedAmount = numbers.maxOrNull() ?: 0.0

            if (detectedAmount > 0.0) {
                val isGajian = upperText.contains("GAJI") || upperText.contains("PEMASUKAN")
                db.transactionDao().insertTransaction(TransactionEntity(
                    amount = detectedAmount, type = if (isGajian) "INCOME" else "EXPENSE",
                    categoryId = if (isGajian) 1L else 15L, categoryName = if (isGajian) "Gaji & Pendapatan" else "Lain-lain / Umum",
                    note = if (isGajian) "GAJIAN (RECOVERY)" else "PENGELUARAN (RECOVERY)", timestamp = System.currentTimeMillis()
                ))
                return "✅ [Recovery System] Berhasil mencatat transaksi sebesar ${formatRupiah.format(detectedAmount)}."
            }
            return "❌ Kalimat transaksi tidak dikenali oleh sistem."
        }
    }
}
