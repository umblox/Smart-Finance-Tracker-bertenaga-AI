package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.data.local.entity.DebtEntity
import com.smartfinance.tracker.data.remote.FirebaseSyncManager
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class FinancialAssistant(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val syncManager = FirebaseSyncManager(context)

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

            if (actionType == "CHAT_ONLY") {
                return aiResponse.ifEmpty { "Ada yang bisa saya bantu lagi, Mam?" }
            }

            val txArray = json.optJSONArray("transactions")
            if (txArray != null && txArray.length() > 0) {
                for (i in 0 until txArray.length()) {
                    val item = txArray.getJSONObject(i)
                    
                    val dateStr = item.optString("transaction_date", "").trim()
                    val sdfParser = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID"))
                    val targetTimestamp = if (dateStr.isNotEmpty()) {
                        try { sdfParser.parse(dateStr)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
                    } else {
                        System.currentTimeMillis()
                    }

                    val amount = item.optDouble("amount", 0.0)
                    val finalAmount = if (amount == 0.0) item.optString("amount", "0").toDoubleOrNull() ?: 0.0 else amount
                    if (finalAmount <= 0.0) continue

                    val contactNameRaw = item.optString("contact_name", "").trim().uppercase(Locale.ROOT)

                    when (actionType) {
                        "TRANSACTION" -> {
                            val cleanNote = item.optString("clean_note", "Transaksi AI").trim().uppercase(Locale.ROOT)
                            var type = item.optString("type", "EXPENSE").trim().uppercase(Locale.ROOT)
                            
                            if (cleanNote.contains("GAJI") || cleanNote.contains("PAYDAY") || cleanNote.contains("PEMASUKAN")) {
                                type = "INCOME"
                            }

                            var catId = item.optLong("category_id", 15L)
                            var catName = item.optString("category_name", "Lain-lain / Umum").trim()
                            if (type == "INCOME") { catId = 1L; catName = "Gaji & Pendapatan" }

                            val transactionEntity = TransactionEntity(
                                amount = finalAmount, type = type, categoryId = catId, categoryName = catName, note = cleanNote, timestamp = targetTimestamp
                            )

                            val generatedId = db.transactionDao().insertTransaction(transactionEntity)
                            syncManager.syncSingleTransactionToCloud(transactionEntity.copy(id = generatedId))
                        }
                        
                        "DEBT_RECORD" -> {
                            if (contactNameRaw.isEmpty()) continue
                            
                            // 🔥 KUNCI LOGIKA 1: Validasi arah utang-piutang berdasarkan teks respon riil biar gak bisa dibohongi JSON Groq
                            val isReceivableAction = aiResponse.uppercase(Locale.ROOT).contains("PIUTANG") || 
                                                     cleanJsonStr.uppercase(Locale.ROOT).contains("RECEIVABLE")
                            
                            val finalizedDebtType = if (isReceivableAction) "RECEIVABLE" else "DEBT"

                            // 1. Tulis ke Menu Navigasi Bawah (Tabel Debts)
                            val newDebt = DebtEntity(
                                contactName = contactNameRaw, contactPhoneNumber = "0812", amount = finalAmount,
                                remainingAmount = finalAmount, type = finalizedDebtType, note = "Otomatis via Chat AI",
                                timestamp = targetTimestamp, isPaid = false
                            )
                            val generatedDebtId = db.debtDao().insertDebt(newDebt)
                            syncManager.syncSingleDebtToCloud(newDebt.copy(id = generatedDebtId))

                            // 2. Tulis ke Dashboard via Mutasi Kas (Tabel Transactions)
                            // RECEIVABLE (Kita pinjamkan uang ke orang) -> Arus Kas Keluar (EXPENSE), ID Kategori: 104
                            // DEBT (Kita ngutang dapet uang dari orang) -> Arus Kas Masuk (INCOME), ID Kategori: 101
                            val flowType = if (finalizedDebtType == "RECEIVABLE") "EXPENSE" else "INCOME"
                            val catId = if (finalizedDebtType == "RECEIVABLE") 104L else 101L
                            val catName = if (finalizedDebtType == "RECEIVABLE") "Piutang" else "Hutang"
                            val prefixNote = if (finalizedDebtType == "RECEIVABLE") "PIUTANG KELUAR KE" else "HUTANG MASUK DARI"
                            
                            val newTransaction = TransactionEntity(
                                amount = finalAmount, type = flowType, categoryId = catId, categoryName = catName,
                                note = "[$catName] $prefixNote $contactNameRaw".uppercase(Locale.ROOT), timestamp = targetTimestamp
                            )
                            val generatedTxId = db.transactionDao().insertTransaction(newTransaction)
                            syncManager.syncSingleTransactionToCloud(newTransaction.copy(id = generatedTxId))
                        }
                        
                        "DEBT_PAYMENT" -> {
                            if (contactNameRaw.isEmpty()) continue
                            
                            val debts = db.debtDao().getAllDebts().first()
                            // Cari pinjaman aktif orang tersebut yang belum lunas
                            val matchDebt = debts.find { it.contactName.uppercase(Locale.ROOT) == contactNameRaw && !it.isPaid }
                            
                            if (matchDebt != null) {
                                val isPelunasan = aiResponse.uppercase(Locale.ROOT).contains("MELUNASI") || aiResponse.uppercase(Locale.ROOT).contains("LUNAS")
                                val targetPayAmount = if (isPelunasan) matchDebt.remainingAmount else finalAmount
                                
                                val nextRemaining = (matchDebt.remainingAmount - targetPayAmount).coerceAtLeast(0.0)
                                val updatedDebt = matchDebt.copy(remainingAmount = nextRemaining, isPaid = nextRemaining <= 0.0)
                                
                                // 1. Update status di Menu Navigasi Bawah (Tabel Debts)
                                db.debtDao().insertDebt(updatedDebt)
                                syncManager.syncSingleDebtToCloud(updatedDebt)

                                // 2. Catat mutasi kas ke Dashboard (Tabel Transactions)
                                // Jika kita bayar cicilan utang kita (DEBT) -> EXPENSE, ID Kategori: 102
                                // Jika orang bayar cicilan utang mereka ke kita (RECEIVABLE) -> INCOME, ID Kategori: 103
                                val txType = if (matchDebt.type == "DEBT") "EXPENSE" else "INCOME"
                                val catId = if (matchDebt.type == "DEBT") 102L else 103L
                                val catName = if (matchDebt.type == "DEBT") "Pembayaran kembali" else "Penagihan Utang"

                                val payTransactionEntity = TransactionEntity(
                                    amount = targetPayAmount, type = txType, categoryId = catId, categoryName = catName,
                                    note = "[$catName] CICILAN OLEH $contactNameRaw".uppercase(Locale.ROOT), timestamp = targetTimestamp
                                )

                                val generatedId = db.transactionDao().insertTransaction(payTransactionEntity)
                                syncManager.syncSingleTransactionToCloud(payTransactionEntity.copy(id = generatedId))
                            }
                        }
                    }
                }
            }

            return aiResponse.ifEmpty { "Catatan keuangan berhasil diproses ke sistem, Mam!" }

        } catch (e: Exception) {
            return "❌ Kalimat transaksi tidak dikenali oleh sistem."
        }
    }
}
