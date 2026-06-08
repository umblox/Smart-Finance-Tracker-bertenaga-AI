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

            if (actionType == "VIEW_REPORT") {
                val allTx = db.transactionDao().getAllTransactions().first()
                var incSum = 0.0
                var expSum = 0.0
                allTx.forEach { 
                    if (!it.note.contains("CICILAN") && !it.note.contains("HUTANG MASUK")) {
                        if (it.type == "INCOME") incSum += it.amount else expSum += it.amount
                    }
                }
                return "📊 **Ringkasan Laporan Keuangan Riil Anda, Mam:**\n\n" +
                       "🟢 Pemasukan Murni: ${formatRupiah.format(incSum)}\n" +
                       "🔴 Pengeluaran Murni: ${formatRupiah.format(expSum)}\n" +
                       "💰 Saldo Dompet: ${formatRupiah.format(incSum - expSum)}\n\n" +
                       "Catatan: Struktur cicilan dan utang aktif dapat dipantau langsung via grafik utama halaman Dashboard!"
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
                            val finalizedTransaction = transactionEntity.copy(id = generatedId)
                            syncManager.syncSingleTransactionToCloud(finalizedTransaction)
                        }
                        
                        "DEBT_RECORD" -> {
                            if (contactNameRaw.isEmpty()) continue
                            val debtType = item.optString("debt_type", "DEBT").trim().uppercase(Locale.ROOT)

                            db.debtDao().insertDebt(DebtEntity(
                                contactName = contactNameRaw, contactPhoneNumber = "0812", amount = finalAmount, remainingAmount = finalAmount, 
                                type = debtType, note = "Otomatis via Chat AI", timestamp = targetTimestamp, isPaid = false
                            ))

                            val flowType = if (debtType == "DEBT") "INCOME" else "EXPENSE"
                            val catId = if (debtType == "DEBT") 101L else 104L
                            val catName = if (debtType == "DEBT") "Hutang" else "Piutang"
                            val prefixNote = if (debtType == "DEBT") "HUTANG MASUK DARI" else "PIUTANG KELUAR KE"

                            val debtTransactionEntity = TransactionEntity(
                                amount = finalAmount, 
                                type = flowType, 
                                categoryId = catId, 
                                categoryName = catName, 
                                note = "[$catName] $prefixNote $contactNameRaw".uppercase(Locale.ROOT), 
                                timestamp = targetTimestamp
                            )

                            val generatedId = db.transactionDao().insertTransaction(debtTransactionEntity)
                            val finalizedDebtTransaction = debtTransactionEntity.copy(id = generatedId)
                            syncManager.syncSingleTransactionToCloud(finalizedDebtTransaction)
                        }
                        
                        "DEBT_PAYMENT" -> {
                            if (contactNameRaw.isEmpty()) continue
                            
                            // 🔥 FIX LOGIKA UTAMA: Cari pinjaman aktif berdasarkan pencocokan nama teks secara fleksibel!
                            val debts = db.debtDao().getAllDebts().first()
                            val matchDebt = debts.find { it.contactName.uppercase(Locale.ROOT) == contactNameRaw && !it.isPaid }
                            
                            if (matchDebt != null) {
                                val isPelunasan = aiResponse.uppercase(Locale.ROOT).contains("MELUNASI") || aiResponse.uppercase(Locale.ROOT).contains("LUNAS")
                                val targetPayAmount = if (isPelunasan) matchDebt.remainingAmount else finalAmount
                                
                                val nextRemaining = (matchDebt.remainingAmount - targetPayAmount).coerceAtLeast(0.0)
                                db.debtDao().insertDebt(matchDebt.copy(
                                    remainingAmount = nextRemaining, isPaid = nextRemaining <= 0.0
                                ))

                                val txType = if (matchDebt.type == "DEBT") "EXPENSE" else "INCOME"
                                val catId = if (matchDebt.type == "DEBT") 102L else 103L
                                val catName = if (matchDebt.type == "DEBT") "Pembayaran kembali" else "Penagihan Utang"

                                val payTransactionEntity = TransactionEntity(
                                    amount = targetPayAmount, 
                                    type = txType, 
                                    categoryId = catId, 
                                    categoryName = catName,
                                    note = "[$catName] CICILAN OLEH $contactNameRaw".uppercase(Locale.ROOT), 
                                    timestamp = targetTimestamp
                                )

                                val generatedId = db.transactionDao().insertTransaction(payTransactionEntity)
                                val finalizedPayTransaction = payTransactionEntity.copy(id = generatedId)
                                syncManager.syncSingleTransactionToCloud(finalizedPayTransaction)
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
