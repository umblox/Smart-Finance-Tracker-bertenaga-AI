package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.data.local.entity.DebtEntity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class FinancialAssistant(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    suspend fun parseAndExecuteRawAiResponse(rawText: String): String {
        val cleanJsonStr = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        try {
            val json = JSONObject(cleanJsonStr)
            val actionType = json.optString("action_type", "TRANSACTION").trim().uppercase(Locale.ROOT)
            val aiResponse = json.optString("ai_response", "Catatan transaksi diamankan.")

            val dateStr = json.optString("transaction_date", "").trim()
            val sdfParser = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID"))
            val targetTimestamp = if (dateStr.isNotEmpty()) {
                try { sdfParser.parse(dateStr)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
            } else {
                System.currentTimeMillis()
            }

            val amount = json.optDouble("amount", 0.0)
            val finalAmount = if (amount == 0.0) json.optString("amount", "0").toDoubleOrNull() ?: 0.0 else amount

            if (actionType == "TRANSACTION") {
                val cleanNote = json.optString("clean_note", "Transaksi AI").trim().uppercase(Locale.ROOT)
                var type = json.optString("type", "EXPENSE").trim().uppercase(Locale.ROOT)
                
                if (cleanNote.contains("GAJI") || cleanNote.contains("PAYDAY") || cleanNote.contains("PEMASUKAN") || aiResponse.uppercase(Locale.ROOT).contains("GAJIAN") || aiResponse.uppercase(Locale.ROOT).contains("PENDAPATAN")) {
                    type = "INCOME"
                }

                var catId = json.optLong("category_id", 15L)
                var catName = json.optString("category_name", "Lain-lain / Umum").trim()

                if (type == "INCOME") {
                    catId = 1L
                    catName = "Gaji & Pendapatan"
                }

                if (finalAmount > 0.0) {
                    db.transactionDao().insertTransaction(TransactionEntity(
                        amount = finalAmount, type = type, categoryId = catId, categoryName = catName, note = cleanNote, timestamp = targetTimestamp
                    ))
                }
            } else if (actionType == "DEBT_RECORD") {
                val name = json.optString("contact_name", "TEMAN").trim().uppercase(Locale.ROOT)
                val debtType = json.optString("debt_type", "DEBT").trim().uppercase(Locale.ROOT)

                if (finalAmount > 0.0) {
                    db.debtDao().insertDebt(DebtEntity(
                        contactName = name, contactPhoneNumber = "0812", amount = finalAmount, remainingAmount = finalAmount, type = debtType, note = "Otomatis via Chat AI", timestamp = targetTimestamp, isPaid = false
                    ))

                    val flowType = if (debtType == "DEBT") "INCOME" else "EXPENSE"
                    val catId = if (debtType == "DEBT") 12L else 13L
                    val catName = if (debtType == "DEBT") "Hutang (Saya Meminjam)" else "Piutang (Memberi Pinjaman)"

                    db.transactionDao().insertTransaction(TransactionEntity(
                        amount = finalAmount, type = flowType, categoryId = catId, categoryName = catName, note = if (debtType == "DEBT") "HUTANG MASUK DARI $name" else "PIUTANG KELUAR KE $name", timestamp = targetTimestamp
                    ))
                }
            }
            return aiResponse

        } catch (e: Exception) {
            // BENTENG CADANGAN: Jika JSON Melenceng, bongkar manual menggunakan deteksi String kasar agar transaksi tidak hilang!
            val upperText = cleanJsonStr.uppercase(Locale.ROOT)
            var detectedAmount = 0.0
            
            // Ekstraksi angka kasar dari string respons
            val numbers = Regex("\\d+").findAll(upperText).map { it.value.toDoubleOrNull() ?: 0.0 }.toList()
            if (numbers.isNotEmpty()) {
                detectedAmount = numbers.maxOrNull() ?: 0.0
            }

            if (detectedAmount > 0.0) {
                val isGajian = upperText.contains("GAJI") || upperText.contains("PEMASUKAN")
                db.transactionDao().insertTransaction(TransactionEntity(
                    amount = detectedAmount,
                    type = if (isGajian) "INCOME" else "EXPENSE",
                    categoryId = if (isGajian) 1L else 15L,
                    categoryName = if (isGajian) "Gaji & Pendapatan" else "Lain-lain / Umum",
                    note = if (isGajian) "GAJIAN (RECOVERY AI)" else "PENGELUARAN (RECOVERY AI)",
                    timestamp = System.currentTimeMillis()
                ))
                return "✅ [Sistem Pulih] Berhasil mencatat ${if (isGajian) "Pemasukan" else "Pengeluaran"} Rp ${detectedAmount} via Recovery Engine."
            }
            
            return "❌ Gagal total memproses input: Kalimat terlalu membingungkan sistem."
        }
    }
}
