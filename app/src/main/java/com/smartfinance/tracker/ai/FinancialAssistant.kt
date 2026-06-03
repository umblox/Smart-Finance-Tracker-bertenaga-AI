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
            val json = JSONObject(rawText)
            val actionType = json.optString("action_type", "CHAT_ONLY")
            val aiResponse = json.optString("ai_response", "Catatan berhasil diamankan.")

            when (actionType) {
                "TRANSACTION" -> {
                    val amount = json.optDouble("amount", 0.0)
                    val catId = json.optLong("category_id", 15L)
                    val catName = json.optString("category_name", "Lain-lain / Umum")
                    val cleanNote = json.optString("clean_note", "Transaksi AI").uppercase(Locale.ROOT)
                    val type = json.optString("type", "EXPENSE").uppercase(Locale.ROOT)

                    if (amount > 0.0) {
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount, type = type, categoryId = catId, categoryName = catName,
                            note = cleanNote, timestamp = System.currentTimeMillis()
                        ))
                    }
                }
                
                "DEBT_RECORD" -> {
                    val amount = json.optDouble("amount", 0.0)
                    val name = json.optString("contact_name", "TEMAN").uppercase(Locale.ROOT)
                    val debtType = json.optString("debt_type", "DEBT").uppercase(Locale.ROOT)

                    if (amount > 0.0) {
                        // 1. Catat transaksi hutang piutang master
                        db.debtDao().insertDebt(DebtEntity(
                            contactName = name, contactPhoneNumber = "0812", amount = amount,
                            remainingAmount = amount, type = debtType, note = "Otomatis via Chat AI",
                            timestamp = System.currentTimeMillis(), isPaid = false
                        ))

                        // 2. SINKRONISASI SALDO UTAMA SECARA NYATA
                        val flowType = if (debtType == "DEBT") "INCOME" else "EXPENSE"
                        val catId = if (debtType == "DEBT") 12L else 13L
                        val catName = if (debtType == "DEBT") "Hutang (Saya Meminjam)" else "Piutang (Memberi Pinjaman)"

                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount, type = flowType, categoryId = catId, categoryName = catName,
                            note = if (debtType == "DEBT") "HUTANG MASUK DARI $name" else "PIUTANG KELUAR KE $name",
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                }
                
                "DEBT_PAYMENT" -> {
                    val targetId = json.optLong("debt_id", -1L)
                    val payAmount = json.optDouble("pay_amount", 0.0)

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
                                amount = payAmount, type = txType, categoryId = 11L, categoryName = "Cicilan & Pinjaman",
                                note = "CICILAN OLEH ${matchDebt.contactName}", timestamp = System.currentTimeMillis()
                            ))
                        } else {
                            return "⚠️ Gagal mencatat cicilan: ID transaksi pinjaman tidak ditemukan dalam database lokal."
                        }
                    }
                }
            }
            return aiResponse
        } catch (e: Exception) {
            // Bongkar pesan kesalahan asli SQLite/Parser ke layar chat agar transparan
            return "❌ Gagal Eksekusi Database Lokal: ${e.localizedMessage ?: "Format JSON Melenceng"}\nRespon Mentah: $rawText"
        }
    }
}
