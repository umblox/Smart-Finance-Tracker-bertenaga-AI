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
    // 🔥 INISIALISASI JALUR UTAMA: Hubungkan manajer sinkronisasi awan satu pintu
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
                    
                    // PARSING WAKTU KALENDER
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

                            // 🔥 FIX ARSITEKTUR 1: Tangkap ID unik hasil generate Room lokal SQLite
                            val generatedId = db.transactionDao().insertTransaction(transactionEntity)
                            val finalizedTransaction = transactionEntity.copy(id = generatedId)

                            // 🔥 REPLIKASI 1: Alirkan data final ke Firebase Firestore Cloud lewat jalur resmi
                            syncManager.syncSingleTransactionToCloud(finalizedTransaction)
                        }
                        
                        "DEBT_RECORD" -> {
                            val name = item.optString("contact_name", "TEMAN").trim().uppercase(Locale.ROOT)
                            var debtType = item.optString("debt_type", "DEBT").trim().uppercase(Locale.ROOT)
                            
                            val cleanNote = item.optString("clean_note", "").uppercase(Locale.ROOT)
                            if (cleanNote.contains("SAYA PINJAM") || cleanNote.contains("SAYA NGUTANG") || cleanNote.contains("SAYA BERHUTANG")) {
                                debtType = "DEBT"
                            } else if (cleanNote.contains("MEMINJAMKAN") || cleanNote.contains("DIPINJAM")) {
                                debtType = "RECEIVABLE"
                            }

                            db.debtDao().insertDebt(DebtEntity(
                                contactName = name, contactPhoneNumber = "0812", amount = finalAmount, remainingAmount = finalAmount, 
                                type = debtType, note = "Otomatis via Chat AI", timestamp = targetTimestamp, isPaid = false
                            ))

                            val flowType = if (debtType == "DEBT") "INCOME" else "EXPENSE"
                            val catId = if (debtType == "DEBT") 12L else 13L
                            val catName = if (debtType == "DEBT") "Hutang (Saya Meminjam)" else "Piutang (Memberi Pinjaman)"

                            val debtTransactionEntity = TransactionEntity(
                                amount = finalAmount, type = flowType, categoryId = catId, categoryName = catName, 
                                note = if (debtType == "DEBT") "HUTANG MASUK DARI $name" else "PIUTANG KELUAR KE $name", timestamp = targetTimestamp
                            )

                            // 🔥 FIX ARSITEKTUR 2: Ambil ID unik untuk pencatatan riwayat mutasi keuangan utang baru
                            val generatedId = db.transactionDao().insertTransaction(debtTransactionEntity)
                            val finalizedDebtTransaction = debtTransactionEntity.copy(id = generatedId)

                            // 🔥 REPLIKASI 2: Amankan salinan transaksi utang baru ke Firestore
                            syncManager.syncSingleTransactionToCloud(finalizedDebtTransaction)
                        }
                        
                        "DEBT_PAYMENT" -> {
                            val targetId = item.optLong("debt_id", -1L)
                            val payAmount = item.optDouble("pay_amount", 0.0)
                            val finalPayAmount = if (payAmount == 0.0) item.optString("pay_amount", "0").toDoubleOrNull() ?: 0.0 else payAmount

                            if (targetId != -1L && finalPayAmount > 0.0) {
                                val debts = db.debtDao().getAllDebts().first()
                                val matchDebt = debts.find { it.id == targetId }
                                
                                if (matchDebt != null) {
                                    val nextRemaining = (matchDebt.remainingAmount - finalPayAmount).coerceAtLeast(0.0)
                                    db.debtDao().insertDebt(matchDebt.copy(
                                        remainingAmount = nextRemaining, isPaid = nextRemaining <= 0.0
                                    ))

                                    val txType = if (matchDebt.type == "DEBT") "EXPENSE" else "INCOME"
                                    val payTransactionEntity = TransactionEntity(
                                        amount = finalPayAmount, type = txType, categoryId = 11L, categoryName = "Cicilan & Pinjaman",
                                        note = "CICILAN OLEH ${matchDebt.contactName}", timestamp = targetTimestamp
                                    )

                                    // 🔥 FIX ARSITEKTUR 3: Tangkap ID unik pendaftaran log cicilan/pelunasan pinjaman aktif
                                    val generatedId = db.transactionDao().insertTransaction(payTransactionEntity)
                                    val finalizedPayTransaction = payTransactionEntity.copy(id = generatedId)

                                    // 🔥 REPLIKASI 3: Amankan aliran data cicilan masuk/keluar ke server Cloud
                                    syncManager.syncSingleTransactionToCloud(finalizedPayTransaction)
                                }
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
