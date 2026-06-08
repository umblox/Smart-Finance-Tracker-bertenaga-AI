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

                    val cleanAiResponseUpper = aiResponse.uppercase(Locale.ROOT)
                    
                    // 🔥 PERBAIKAN 1: Parser Nama Kontak Dinamis (Bisa mendeteksi JOKO, ARNETA, DANI, dll. tanpa hardcode)
                    var contactNameRaw = item.optString("contact_name", "").trim().uppercase(Locale.ROOT)
                    if (contactNameRaw.isEmpty() || contactNameRaw == "TEMAN") {
                        contactNameRaw = dynamicContactNameExtractor(cleanAiResponseUpper)
                    }

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
                            
                            val isReceivable = cleanAiResponseUpper.contains("PIUTANG") || cleanJsonStr.uppercase(Locale.ROOT).contains("RECEIVABLE")
                            val finalizedDebtType = if (isReceivable) "RECEIVABLE" else "DEBT"

                            // 1. Masuk ke tabel navigasi bawah (debts)
                            val newDebt = DebtEntity(
                                contactName = contactNameRaw, contactPhoneNumber = "0812", amount = finalAmount,
                                remainingAmount = finalAmount, type = finalizedDebtType, note = "Otomatis via Chat AI",
                                timestamp = targetTimestamp, isPaid = false
                            )
                            val generatedDebtId = db.debtDao().insertDebt(newDebt)
                            syncManager.syncSingleDebtToCloud(newDebt.copy(id = generatedDebtId))

                            // 2. 🔥 REPLIKASI MANUAL PATTERN: Paksa tipe menjadi INCOME/EXPENSE agar dibaca Dashboard
                            val flowType = if (finalizedDebtType == "RECEIVABLE") "EXPENSE" else "INCOME"
                            val catId = if (finalizedDebtType == "RECEIVABLE") 104L else 101L
                            val catName = if (finalizedDebtType == "RECEIVABLE") "Piutang" else "Hutang"
                            
                            val newTransaction = TransactionEntity(
                                amount = finalAmount, 
                                type = flowType, // Diwajibkan berjenis INCOME / EXPENSE murni
                                categoryId = catId, 
                                categoryName = catName,
                                note = "[$catName] INPUT AI PINJAMAN - $contactNameRaw".uppercase(Locale.ROOT), 
                                timestamp = targetTimestamp
                            )
                            val generatedTxId = db.transactionDao().insertTransaction(newTransaction)
                            syncManager.syncSingleTransactionToCloud(newTransaction.copy(id = generatedTxId))
                        }
                        
                        "DEBT_PAYMENT" -> {
                            val debts = db.debtDao().getAllDebts().first()
                            
                            // 🔥 PERBAIKAN 2: Cari data berdasarkan nama kontak secara dinamis di list utang aktif
                            val matchDebt = debts.find { debtItem ->
                                val dbName = debtItem.contactName.uppercase(Locale.ROOT)
                                !debtItem.isPaid && (dbName == contactNameRaw || cleanAiResponseUpper.contains(dbName))
                            }
                            
                            if (matchDebt != null) {
                                val isPelunasan = cleanAiResponseUpper.contains("MELUNASI") || cleanAiResponseUpper.contains("LUNAS")
                                val targetPayAmount = if (isPelunasan) matchDebt.remainingAmount else finalAmount
                                
                                val nextRemaining = (matchDebt.remainingAmount - targetPayAmount).coerceAtLeast(0.0)
                                val updatedDebt = matchDebt.copy(remainingAmount = nextRemaining, isPaid = nextRemaining <= 0.0)
                                
                                // 1. Update Tabel Navigasi Bawah
                                db.debtDao().insertDebt(updatedDebt)
                                syncManager.syncSingleDebtToCloud(updatedDebt)

                                // 2. 🔥 REPLIKASI MANUAL PATTERN: Masukkan data cicilan berjenis murni INCOME/EXPENSE ke Dashboard
                                val txType = if (matchDebt.type == "DEBT") "EXPENSE" else "INCOME"
                                val catId = if (matchDebt.type == "DEBT") 102L else 103L
                                val catName = if (matchDebt.type == "DEBT") "Pembayaran kembali" else "Penagihan Utang"

                                val payTransactionEntity = TransactionEntity(
                                    amount = targetPayAmount, 
                                    type = txType, // Diwajibkan berjenis INCOME / EXPENSE murni
                                    categoryId = catId, 
                                    categoryName = catName,
                                    note = "[$catName] $contactNameRaw - CICILAN AI".uppercase(Locale.ROOT), 
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
     * 🔥 Ekstraktor Nama Kontak Fleksibel Dinamis
     */
    private fun dynamicContactNameExtractor(text: String): String {
        val keywords = listOf("KEPADA", "DARI", "DENGAN", "MEMBERI", "MEMINJAMKAN", "OLEH")
        val words = text.split(" ")
        for (keyword in keywords) {
            val index = words.indexOf(keyword)
            if (index != -1 && index + 1 < words.size) {
                // Ambil kata setelah kata kunci (nama orang biasanya tepat berada setelah kata kunci ini)
                val possibleName = words[index + 1].replace(Regex("[^A-Z]"), "")
                if (possibleName.length > 2) return possibleName
            }
        }
        return ""
    }
}
