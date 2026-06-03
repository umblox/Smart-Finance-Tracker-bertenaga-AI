package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.data.local.entity.DebtEntity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.Locale

class FinancialAssistant(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    suspend fun parseAndExecuteRawAiResponse(rawText: String): String {
        try {
            // Bersihkan teks jika AI mengembalikan markdown block ```json ... ```
            val cleanJsonStr = rawText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(cleanJsonStr)
            val actionType = json.optString("action_type", "CHAT_ONLY").trim().uppercase(Locale.ROOT)
            val aiResponse = json.optString("ai_response", "Catatan berhasil diproses.")

            when (actionType) {
                "TRANSACTION" -> {
                    // 1. Ekstraksi Nominal Aman (String to Double)
                    val amountStr = json.optString("amount", "0").replace(",", "")
                    val amount = amountStr.toDoubleOrNull() ?: json.optDouble("amount", 0.0)
                    
                    val cleanNote = json.optString("clean_note", "Transaksi AI").trim().uppercase(Locale.ROOT)
                    var type = json.optString("type", "EXPENSE").trim().uppercase(Locale.ROOT)
                    
                    // 2. PROTEKSI TINGGI: Jika ada kata GAJI / INCOME / PEMASUKAN, paksa tipe menjadi INCOME
                    if (cleanNote.contains("GAJI") || cleanNote.contains("PAYDAY") || cleanNote.contains("PEMASUKAN") || type == "INCOME") {
                        type = "INCOME"
                    }

                    // 3. Normalisasi Category ID & Name agar tidak melanggar Foreign Key DB
                    var catId = json.optLong("category_id", 15L)
                    var catName = json.optString("category_name", "Lain-lain / Umum").trim()

                    if (type == "INCOME") {
                        // Kunci ke kategori pendapatan bawaan aplikasi jika terdeteksi gajian
                        catId = 1L 
                        catName = "Gaji & Pendapatan"
                    }

                    if (amount > 0.0) {
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount,
                            type = type,
                            categoryId = catId,
                            categoryName = catName,
                            note = cleanNote,
                            timestamp = System.currentTimeMillis()
                        ))
                    } else {
                        return "⚠️ AI mendeteksi nominal kosong atau 0, transaksi dibatalkan."
                    }
                }
                
                "DEBT_RECORD" -> {
                    val amountStr = json.optString("amount", "0").replace(",", "")
                    val amount = amountStr.toDoubleOrNull() ?: json.optDouble("amount", 0.0)
                    
                    val name = json.optString("contact_name", "TEMAN").trim().uppercase(Locale.ROOT)
                    val debtType = json.optString("debt_type", "DEBT").trim().uppercase(Locale.ROOT)

                    if (amount > 0.0) {
                        db.debtDao().insertDebt(DebtEntity(
                            contactName = name,
                            contactPhoneNumber = "0812",
                            amount = amount,
                            remainingAmount = amount,
                            type = debtType,
                            note = "Otomatis via Chat AI",
                            timestamp = System.currentTimeMillis(),
                            isPaid = false
                        ))

                        // Sinkronisasi otomatis ke tabel transaksi utama
                        val flowType = if (debtType == "DEBT") "INCOME" else "EXPENSE"
                        val catId = if (debtType == "DEBT") 12L else 13L
                        val catName = if (debtType == "DEBT") "Hutang (Saya Meminjam)" else "Piutang (Memberi Pinjaman)"

                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount,
                            type = flowType,
                            categoryId = catId,
                            categoryName = catName,
                            note = if (debtType == "DEBT") "HUTANG MASUK DARI $name" else "PIUTANG KELUAR KE $name",
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                }
                
                "DEBT_PAYMENT" -> {
                    val targetId = json.optLong("debt_id", -1L)
                    val payAmountStr = json.optString("pay_amount", "0").replace(",", "")
                    val payAmount = payAmountStr.toDoubleOrNull() ?: json.optDouble("pay_amount", 0.0)

                    if (targetId != -1L && payAmount > 0.0) {
                        val debts = db.debtDao().getAllDebts().first()
                        val matchDebt = debts.find { it.id == targetId }
                        
                        if (matchDebt != null) {
                            val nextRemaining = (matchDebt.remainingAmount - payAmount).coerceAtLeast(0.0)
                            db.debtDao().insertDebt(matchDebt.copy(
                                remainingAmount = nextRemaining,
                                isPaid = nextRemaining <= 0.0
                            ))

                            val txType = if (matchDebt.type == "DEBT") "EXPENSE" else "INCOME"
                            db.transactionDao().insertTransaction(TransactionEntity(
                                amount = payAmount,
                                type = txType,
                                categoryId = 11L,
                                categoryName = "Cicilan & Pinjaman",
                                note = "CICILAN OLEH ${matchDebt.contactName}",
                                timestamp = System.currentTimeMillis()
                            ))
                        } else {
                            return "⚠️ Gagal mencatat cicilan: Target ID pinjaman tidak valid."
                        }
                    }
                }
            }
            return aiResponse
        } catch (e: Exception) {
            return "❌ Crash Eksekusi JSON: ${e.localizedMessage}\nRespon Mentah: $rawText"
        }
    }
}
