package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.data.local.entity.DebtEntity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.Locale

class FinancialAssistant(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    suspend fun parseAndExecuteRawAiResponse(rawText: String): String {
        try {
            // Pemotong string berbasis Tag Delimiter <EXEC> dan </EXEC>
            if (rawText.contains("<EXEC>") && rawText.contains("</EXEC>")) {
                val parts = rawText.split("<EXEC>")
                val cleanNarration = parts[0].trim() // Ini teks ramah manusia untuk chat
                
                val codePart = parts[1].split("</EXEC>")[0].trim() // Ini isi JSON rahasia
                
                // Eksekusi ke SQLite Room
                executeSilentJsonCommand(codePart)
                
                return cleanNarration
            }
        } catch (e: Exception) {
            return rawText
        }
        return rawText
    }

    private suspend fun executeSilentJsonCommand(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val actionType = json.optString("action_type", "CHAT_ONLY")

            when (actionType) {
                "TRANSACTION" -> {
                    val amount = json.optDouble("amount", 0.0)
                    var catId = json.optLong("category_id", 15L)
                    var catName = json.optString("category_name", "Lain-lain / Umum")
                    val cleanNote = json.optString("clean_note", "Transaksi AI").uppercase(Locale.ROOT)
                    var type = json.optString("type", "EXPENSE").uppercase(Locale.ROOT)

                    if (cleanNote.contains("GAJI") || cleanNote.contains("TIPS") || cleanNote.contains("BONUS") || cleanNote.contains("UPAH")) {
                        type = "INCOME"
                        if (catId == 15L) { catId = 1L; catName = "Gaji & Pendapatan" }
                    }

                    if (amount > 0.0) {
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount, type = type, categoryId = catId, categoryName = catName,
                            note = cleanNote, timestamp = System.currentTimeMillis()
                        ))
                    }
                }
                
                "DEBT_RECORD" -> {
                    val amount = json.optDouble("amount", 0.0)
                    val name = json.optString("contact_name", "Teman").uppercase(Locale.ROOT)
                    val debtType = json.optString("debt_type", "DEBT").uppercase(Locale.ROOT)

                    if (amount > 0.0) {
                        db.debtDao().insertDebt(DebtEntity(
                            contactName = name, contactPhoneNumber = "0812", amount = amount,
                            remainingAmount = amount, type = debtType, note = "Otomatis via Chat AI",
                            timestamp = System.currentTimeMillis(), isPaid = false
                        ))

                        val isReceivable = debtType == "RECEIVABLE"
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount,
                            type = if (isReceivable) "EXPENSE" else "INCOME",
                            categoryId = if (isReceivable) 13L else 12L,
                            categoryName = if (isReceivable) "Piutang (Memberi Pinjaman)" else "Hutang (Saya Meminjam)",
                            note = "PINJAMAN: $name",
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
                        }
                    }
                }

                "CREATE_CATEGORY" -> {
                    val targetName = json.optString("target_name", "").trim()
                    var catType = json.optString("category_type", "EXPENSE").uppercase(Locale.ROOT)
                    
                    if (targetName.lowercase().contains("tips") || targetName.lowercase().contains("gaji")) {
                        catType = "INCOME"
                    }

                    if (targetName.isNotEmpty()) {
                        db.categoryDao().insertCategory(CategoryEntity(name = targetName, type = catType, iconName = "ic_custom"))
                    }
                }
            }
        } catch (e: Exception) {
            // Gagal parsing senyap
        }
    }

    suspend fun processLocalFallback(input: String, debugError: String): String {
        return "🤖 Sistem Jaringan Lambat."
    }
}
