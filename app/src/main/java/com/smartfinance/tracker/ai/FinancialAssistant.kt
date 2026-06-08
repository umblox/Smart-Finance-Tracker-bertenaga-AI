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
            val aiResponse = json.optString("ai_response", "").trim()

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

                    // Ambil nama kontak dari JSON dan normalkan teks respons untuk pelacak cadangan
                    var contactNameRaw = item.optString("contact_name", "").trim().uppercase(Locale.ROOT)
                    val cleanAiResponseUpper = aiResponse.uppercase(Locale.ROOT)

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
                            // Pelapis 1: Jika Groq lupa mengekstrak nama kontak, bongkar paksa dari kalimat respon asisten
                            if (contactNameRaw.isEmpty()) {
                                contactNameRaw = debtsBackupContactParser(cleanAiResponseUpper)
                            }
                            if (contactNameRaw.isEmpty()) continue
                            
                            val isReceivableAction = cleanAiResponseUpper.contains("PIUTANG") || cleanJsonStr.uppercase(Locale.ROOT).contains("RECEIVABLE")
                            val finalizedDebtType = if (isReceivableAction) "RECEIVABLE" else "DEBT"

                            // 1. Amankan ke tabel modul navigasi bawah (debts)
                            val newDebt = DebtEntity(
                                contactName = contactNameRaw, contactPhoneNumber = "0812", amount = finalAmount,
                                remainingAmount = finalAmount, type = finalizedDebtType, note = "Otomatis via Chat AI",
                                timestamp = targetTimestamp, isPaid = false
                            )
                            val generatedDebtId = db.debtDao().insertDebt(newDebt)
                            syncManager.syncSingleDebtToCloud(newDebt.copy(id = generatedDebtId))

                            // 2. Amankan riwayat mutasi ganda ke dashboard (transactions)
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
                            val debts = db.debtDao().getAllDebts().first()
                            
                            // Pelapis 2: Jika nama dari JSON luput, cari string nama yang tertera di dalam kalimat AI response
                            if (contactNameRaw.isEmpty() || contactNameRaw == "TEMAN") {
                                contactNameRaw = debtsBackupContactParser(cleanAiResponseUpper)
                            }

                            // 🔥 SAKTI INTERSEPTOR: Cari kecocokan nama kontak dari database yang tertera di percakapan
                            val matchDebt = debts.find { debtItem ->
                                val dbName = debtItem.contactName.uppercase(Locale.ROOT)
                                !debtItem.isPaid && (dbName == contactNameRaw || cleanAiResponseUpper.contains(dbName))
                            }
                            
                            if (matchDebt != null) {
                                val isPelunasan = cleanAiResponseUpper.contains("MELUNASI") || cleanAiResponseUpper.contains("LUNAS")
                                val targetPayAmount = if (isPelunasan) matchDebt.remainingAmount else finalAmount
                                
                                val nextRemaining = (matchDebt.remainingAmount - targetPayAmount).coerceAtLeast(0.0)
                                val updatedDebt = matchDebt.copy(remainingAmount = nextRemaining, isPaid = nextRemaining <= 0.0)
                                
                                // 1. Sinkronisasi Modul Navigasi Bawah
                                db.debtDao().insertDebt(updatedDebt)
                                syncManager.syncSingleDebtToCloud(updatedDebt)

                                // 2. Sinkronisasi Mutasi Arus Kas Dashboard Utama
                                val txType = if (matchDebt.type == "DEBT") "EXPENSE" else "INCOME"
                                val catId = if (matchDebt.type == "DEBT") 102L else 103L
                                val catName = if (matchDebt.type == "DEBT") "Pembayaran kembali" else "Penagihan Utang"

                                val payTransactionEntity = TransactionEntity(
                                    amount = targetPayAmount, 
                                    type = txType, 
                                    categoryId = catId, 
                                    categoryName = catName,
                                    note = "[$catName] CICILAN OLEH ${matchDebt.contactName}".uppercase(Locale.ROOT), 
                                    timestamp = targetTimestamp
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

    /**
     * Pembongkar string nama kontak cadangan berbasis deteksi substring kalimat respon asisten
     */
    private fun debtsBackupContactParser(aiText: String): String {
        return when {
            aiText.contains("ARNETA") -> "ARNETA"
            aiText.contains("DANI") -> "DANI"
            aiText.contains("ARIANTO") -> "ARIANTO"
            else -> ""
        }
    }
}
