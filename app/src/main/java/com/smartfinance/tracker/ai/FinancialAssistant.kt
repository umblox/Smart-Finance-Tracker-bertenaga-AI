package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.data.local.entity.DebtEntity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

class FinancialAssistant(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    suspend fun parseAndExecuteRawAiResponse(rawText: String): String {
        try {
            val jsonPattern = Pattern.compile("\\{[^\\{]*\\}$")
            val matcher = jsonPattern.matcher(rawText.trim())

            if (matcher.find()) {
                val jsonString = matcher.group()
                val cleanNarration = rawText.replace(jsonString, "").trim()

                // Jalankan perintah biner ke SQLite sambil mengirimkan teks asli user untuk proteksi kata kunci
                executeSilentJsonCommand(jsonString, cleanNarration.lowercase())
                
                return cleanNarration
            }
        } catch (e: Exception) {
            return rawText
        }
        return rawText
    }

    private suspend fun executeSilentJsonCommand(jsonStr: String, cleanNarrationLower: String) {
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
                    var debtType = json.optString("debt_type", "DEBT").uppercase(Locale.ROOT)

                    // 🛡️ INTERCEPTOR PROTECTION MUTLAK: Deteksi kalimat terbalik
                    // Jika teks mengandung indikasi kuat user yang memberikan pinjaman uang
                    if (cleanNarrationLower.contains("meminjam uang saya") || 
                        cleanNarrationLower.contains("meminjamkan") || 
                        cleanNarrationLower.contains("piutang")) {
                        debtType = "RECEIVABLE"
                    }

                    if (amount > 0.0) {
                        // 1. Simpan ke tabel master Hutang-Piutang (DebtEntity)
                        db.debtDao().insertDebt(DebtEntity(
                            contactName = name, 
                            contactPhoneNumber = "0812", 
                            amount = amount,
                            remainingAmount = amount, 
                            type = debtType, // Tersimpan akurat sebagai RECEIVABLE
                            note = "Dicatat otomatis via Chat AI",
                            timestamp = System.currentTimeMillis(), 
                            isPaid = false
                        ))

                        // 2. Sinkronisasikan efek saldo ke dashboard utama (TransactionEntity)
                        // Piutang (RECEIVABLE) = Uang kita keluar dipinjam orang (EXPENSE)
                        // Hutang (DEBT) = Uang masuk ke dompet kita dari orang (INCOME)
                        val isReceivable = debtType == "RECEIVABLE"
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount,
                            type = if (isReceivable) "EXPENSE" else "INCOME",
                            categoryId = if (isReceivable) 13L else 12L,
                            categoryName = if (isReceivable) "Piutang (Memberi Pinjaman)" else "Hutang (Saya Meminjam)",
                            note = if (isReceivable) "PINJAMAN KELUAR KE $name" else "PINJAMAN MASUK DARI $name",
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
                                note = "CICILAN PINJAMAN OLEH ${matchDebt.contactName}", timestamp = System.currentTimeMillis()
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
            // Gagal parsing senyap, abaikan agar tidak crash
        }
    }
}
