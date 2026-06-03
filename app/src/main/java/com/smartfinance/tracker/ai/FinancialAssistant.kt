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
        try {
            val cleanJsonStr = rawText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(cleanJsonStr)
            val actionType = json.optString("action_type", "CHAT_ONLY").trim().uppercase(Locale.ROOT)
            val aiResponse = json.optString("ai_response", "Catatan berhasil diproses.")

            // PARSING TANGGAL DINAMIS DARI AI
            val dateStr = json.optString("transaction_date", "").trim()
            val sdfParser = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID"))
            val targetTimestamp = if (dateStr.isNotEmpty()) {
                try {
                    sdfParser.parse(dateStr)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
            } else {
                System.currentTimeMillis()
            }

            // EKSTRAKSI NOMINAL AMAN (MENDUKUNG NUMBER DAN STRING)
            val amount = json.optDouble("amount", 0.0)
            val finalAmount = if (amount == 0.0) {
                json.optString("amount", "0").toDoubleOrNull() ?: 0.0
            } else {
                amount
            }

            when (actionType) {
                "TRANSACTION" -> {
                    val cleanNote = json.optString("clean_note", "Transaksi AI").trim().uppercase(Locale.ROOT)
                    var type = json.optString("type", "EXPENSE").trim().uppercase(Locale.ROOT)
                    
                    // PROTEKSI GAJIAN: Cegah Llama salah deteksi jenis aliran kas
                    if (cleanNote.contains("GAJI") || cleanNote.contains("PAYDAY") || cleanNote.contains("PEMASUKAN")) {
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
                            amount = finalAmount,
                            type = type,
                            categoryId = catId,
                            categoryName = catName,
                            note = cleanNote,
                            timestamp = targetTimestamp // MASUK SESUAI TANGGAL REKOMENDASI AI
                        ))
                    }
                }
                
                "DEBT_RECORD" -> {
                    val name = json.optString("contact_name", "TEMAN").trim().uppercase(Locale.ROOT)
                    val debtType = json.optString("debt_type", "DEBT").trim().uppercase(Locale.ROOT)

                    if (finalAmount > 0.0) {
                        db.debtDao().insertDebt(DebtEntity(
                            contactName = name,
                            contactPhoneNumber = "0812",
                            amount = finalAmount,
                            remainingAmount = finalAmount,
                            type = debtType,
                            note = "Otomatis via Chat AI",
                            timestamp = targetTimestamp,
                            isPaid = false
                        ))

                        val flowType = if (debtType == "DEBT") "INCOME" else "EXPENSE"
                        val catId = if (debtType == "DEBT") 12L else 13L
                        val catName = if (debtType == "DEBT") "Hutang (Saya Meminjam)" else "Piutang (Memberi Pinjaman)"

                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = finalAmount,
                            type = flowType,
                            categoryId = catId,
                            categoryName = catName,
                            note = if (debtType == "DEBT") "HUTANG MASUK DARI $name" else "PIUTANG KELUAR KE $name",
                            timestamp = targetTimestamp
                        ))
                    }
                }
                
                "DEBT_PAYMENT" -> {
                    val targetId = json.optLong("debt_id", -1L)
                    val payAmount = json.optDouble("pay_amount", 0.0)
                    val finalPayAmount = if (payAmount == 0.0) {
                        json.optString("pay_amount", "0").toDoubleOrNull() ?: 0.0
                    } else {
                        payAmount
                    }

                    if (targetId != -1L && finalPayAmount > 0.0) {
                        val debts = db.debtDao().getAllDebts().first()
                        val matchDebt = debts.find { it.id == targetId }
                        
                        if (matchDebt != null) {
                            val nextRemaining = (matchDebt.remainingAmount - finalPayAmount).coerceAtLeast(0.0)
                            db.debtDao().insertDebt(matchDebt.copy(
                                remainingAmount = nextRemaining,
                                isPaid = nextRemaining <= 0.0
                            ))

                            val txType = if (matchDebt.type == "DEBT") "EXPENSE" else "INCOME"
                            db.transactionDao().insertTransaction(TransactionEntity(
                                amount = finalPayAmount,
                                type = txType,
                                categoryId = 11L,
                                categoryName = "Cicilan & Pinjaman",
                                note = "CICILAN OLEH ${matchDebt.contactName}",
                                timestamp = targetTimestamp
                            ))
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
