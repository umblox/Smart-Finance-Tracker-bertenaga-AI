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
import java.util.*

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

            // 🔥 FIX BESAR: LOGIKA AGREGASI LAPORAN DETAIL (BISA HARIAN, MINGGUAN, BULANAN, TAHUNAN, KATEGORI)
            if (actionType == "VIEW_REPORT") {
                val allTx = db.transactionDao().getAllTransactions().first()
                val upperRawText = cleanJsonStr.uppercase(Locale.ROOT) + " " + aiResponse.uppercase(Locale.ROOT)
                
                var incSum = 0.0
                var expSum = 0.0
                val calToday = Calendar.getInstance()

                // Filter penentu rentang waktu cerdas berbasis interseptor obrolan kalimat
                val isHarian = upperRawText.contains("HARI INI") || upperRawText.contains("HARIAN")
                val isMingguan = upperRawText.contains("MINGGU")
                val isTahunan = upperRawText.contains("TAHUN")

                allTx.forEach { tx ->
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    var isTimeMatch = true // Default bulanan jika tidak ditentukan

                    if (isHarian) {
                        isTimeMatch = txCal.get(Calendar.DAY_OF_YEAR) == calToday.get(Calendar.DAY_OF_YEAR) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    } else if (isMingguan) {
                        isTimeMatch = txCal.get(Calendar.WEEK_OF_YEAR) == calToday.get(Calendar.WEEK_OF_YEAR) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    } else if (isTahunan) {
                        isTimeMatch = txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    } else {
                        // Default Filter: Bulanan
                        isTimeMatch = txCal.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    }

                    // Pemfilteran berdasarkan spesifik kata kunci nama Kategori jika diminta user
                    if (upperRawText.contains("KATEGORI") || upperRawText.contains("JENIS")) {
                        val matchCategory = tx.categoryName.uppercase(Locale.ROOT)
                        if (!upperRawText.contains(matchCategory)) {
                            isTimeMatch = false
                        }
                    }

                    if (isTimeMatch) {
                        val tUpper = tx.type.trim().uppercase(Locale.ROOT)
                        if (tUpper == "INCOME" || tUpper == "DEBT") {
                            incSum += tx.amount
                        } else if (tUpper == "EXPENSE" || tUpper == "RECEIVABLE") {
                            expSum += tx.amount
                        }
                    }
                }

                val rentangLabel = when {
                    isHarian -> "Hari Ini"
                    isMingguan -> "Minggu Ini"
                    isTahunan -> "Tahun Ini"
                    else -> "Bulan Ini"
                }

                return "📊 **Laporan Finansial Riil $rentangLabel Anda, Mam:**\n\n" +
                       "🟢 Total Arus Masuk: ${formatRupiah.format(incSum)}\n" +
                       "🔴 Total Arus Keluar: ${formatRupiah.format(expSum)}\n" +
                       "💰 Sisa Kas Bersih: ${formatRupiah.format(incSum - expSum)}\n\n" +
                       "Catatan: Seluruh data disinkronkan otomatis dari rekap data master menu internal."
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

                            val newDebt = DebtEntity(
                                contactName = contactNameRaw, contactPhoneNumber = "0812", amount = finalAmount,
                                remainingAmount = finalAmount, type = finalizedDebtType, note = "Otomatis via Chat AI",
                                timestamp = targetTimestamp, isPaid = false
                            )
                            val generatedDebtId = db.debtDao().insertDebt(newDebt)
                            syncManager.syncSingleDebtToCloud(newDebt.copy(id = generatedDebtId))

                            val flowType = if (finalizedDebtType == "RECEIVABLE") "EXPENSE" else "INCOME"
                            val catId = if (finalizedDebtType == "RECEIVABLE") 104L else 101L
                            val catName = if (finalizedDebtType == "RECEIVABLE") "Piutang" else "Hutang"
                            
                            val newTransaction = TransactionEntity(
                                amount = finalAmount, type = flowType, categoryId = catId, categoryName = catName,
                                note = "[$catName] AI PINJAMAN - $contactNameRaw".uppercase(Locale.ROOT), timestamp = targetTimestamp
                            )
                            val generatedTxId = db.transactionDao().insertTransaction(newTransaction)
                            syncManager.syncSingleTransactionToCloud(newTransaction.copy(id = generatedTxId))
                        }
                        
                        "DEBT_PAYMENT" -> {
                            val debts = db.debtDao().getAllDebts().first()
                            
                            val matchDebt = debts.find { debtItem ->
                                val dbName = debtItem.contactName.uppercase(Locale.ROOT)
                                !debtItem.isPaid && (dbName == contactNameRaw || cleanAiResponseUpper.contains(dbName))
                            }
                            
                            if (matchDebt != null) {
                                val isPelunasan = cleanAiResponseUpper.contains("MELUNASI") || cleanAiResponseUpper.contains("LUNAS")
                                val targetPayAmount = if (isPelunasan) matchDebt.remainingAmount else finalAmount
                                
                                val nextRemaining = (matchDebt.remainingAmount - targetPayAmount).coerceAtLeast(0.0)
                                val updatedDebt = matchDebt.copy(remainingAmount = nextRemaining, isPaid = nextRemaining <= 0.0)
                                
                                db.debtDao().insertDebt(updatedDebt)
                                syncManager.syncSingleDebtToCloud(updatedDebt)

                                val txType = if (matchDebt.type == "DEBT") "EXPENSE" else "INCOME"
                                val catId = if (matchDebt.type == "DEBT") 102L else 103L
                                val catName = if (matchDebt.type == "DEBT") "Pembayaran kembali" else "Penagihan Utang"

                                val payTransactionEntity = TransactionEntity(
                                    amount = targetPayAmount, type = txType, categoryId = catId, categoryName = catName,
                                    note = "[$catName] $contactNameRaw - CICILAN AI".uppercase(Locale.ROOT), timestamp = targetTimestamp
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

    private fun dynamicContactNameExtractor(text: String): String {
        // Daftar nama populer yang sering kamu pakai biar aman terkunci akurat
        val databasePopulerNames = listOf("JOKO", "ARNETA", "DANI", "ARIANTO", "BUDI")
        for (name in databasePopulerNames) {
            if (text.contains(name)) return name
        }
        
        val keywords = listOf("KEPADA", "DARI", "DENGAN", "MEMBERI", "MEMINJAMKAN", "OLEH", "UNTUK")
        val words = text.split(" ")
        for (keyword in keywords) {
            val index = words.indexOf(keyword)
            if (index != -1 && index + 1 < words.size) {
                val possibleName = words[index + 1].replace(Regex("[^A-Z]"), "")
                if (possibleName.length > 2) return possibleName
            }
        }
        return ""
    }
}
