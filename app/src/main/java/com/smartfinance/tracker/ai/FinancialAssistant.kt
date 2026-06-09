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

            if (actionType == "VIEW_REPORT") {
                return compileAiReport(cleanJsonStr, aiResponse)
            }

            val txArray = json.optJSONArray("transactions")
            if (txArray != null && txArray.length() > 0) {
                for (i in 0 until txArray.length()) {
                    val item = txArray.getJSONObject(i)
                    val targetTimestamp = parseTransactionDate(item.optString("transaction_date", ""))
                    val finalAmount = parseAmount(item)

                    if (finalAmount <= 0.0) continue
                    val cleanAiResponseUpper = aiResponse.uppercase(Locale.ROOT)
                    
                    var contactNameRaw = item.optString("contact_name", "").trim().uppercase(Locale.ROOT)
                    if (contactNameRaw.isEmpty() || contactNameRaw == "TEMAN") {
                        contactNameRaw = dynamicContactNameExtractor(cleanAiResponseUpper)
                    }

                    when (actionType) {
                        "TRANSACTION" -> {
                            executePureTransaction(item, finalAmount, targetTimestamp)
                        }
                        "DEBT_RECORD" -> {
                            // 🔥 AMAN: Jalur otomatis dimatikan karena sudah diambil alih oleh Interseptor ChatFragment 
                            // Ini mencegah duplikasi data ganda di database SQLite.
                        }
                        "DEBT_PAYMENT" -> {
                            executeDirectDebtPayment(contactNameRaw, finalAmount, cleanAiResponseUpper, targetTimestamp)
                        }
                    }
                }
            }
            return aiResponse.ifEmpty { "Catatan keuangan berhasil diproses ke sistem, Mam!" }
        } catch (e: Exception) {
            return "❌ Kalimat transaksi tidak dikenali oleh sistem."
        }
    }

    // 🔥 JALUR SAKTI UTANG BARU: Dipanggil langsung oleh ChatFragment (Identik dengan nalar kaku UI manual)
    suspend fun executeDirectDebtRecord(name: String, amountValue: Double, isReceivable: Boolean, timestampValue: Long) {
        val selectedType = if (isReceivable) "RECEIVABLE" else "DEBT"
        
        // 1. Amankan ke tabel modul navigasi bawah
        val newDebt = DebtEntity(
            contactName = name.uppercase(Locale.ROOT), contactPhoneNumber = "0812", amount = amountValue,
            remainingAmount = amountValue, type = selectedType, note = "Proses via AI Premium",
            timestamp = timestampValue, isPaid = false
        )
        val generatedDebtId = db.debtDao().insertDebt(newDebt)
        syncManager.syncSingleDebtToCloud(newDebt.copy(id = generatedDebtId))

        // 2. Suntikkan tipe murni INCOME/EXPENSE agar Dashboard reaktif seketika
        val flowType = if (selectedType == "RECEIVABLE") "EXPENSE" else "INCOME"
        val catId = if (selectedType == "RECEIVABLE") 104L else 101L
        val catName = if (selectedType == "RECEIVABLE") "Piutang" else "Hutang"

        val newTransaction = TransactionEntity(
            amount = amountValue, type = flowType, categoryId = catId, categoryName = catName,
            note = "[$selectedType] $name - INPUT AI PINJAMAN", timestamp = timestampValue
        )
        val generatedTxId = db.transactionDao().insertTransaction(newTransaction)
        syncManager.syncSingleTransactionToCloud(newTransaction.copy(id = generatedTxId))
    }

    private suspend fun executeDirectDebtPayment(contactNameRaw: String, finalAmount: Double, aiResponseUpper: String, targetTimestamp: Long) {
        val debts = db.debtDao().getAllDebts().first()
        
        // Cari kecocokan data aktif secara berlapis di SQLite lokal HP
        val matchDebt = debts.find { debtItem ->
            val dbName = debtItem.contactName.uppercase(Locale.ROOT)
            !debtItem.isPaid && (dbName == contactNameRaw || aiResponseUpper.contains(dbName))
        } ?: debts.find { !it.isPaid && finalAmount == it.remainingAmount } 

        if (matchDebt != null) {
            val isPelunasan = aiResponseUpper.contains("MELUNASI") || aiResponseUpper.contains("LUNAS")
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
                note = "[$catName] ${matchDebt.contactName} - CICILAN AI", timestamp = targetTimestamp
            )
            val generatedId = db.transactionDao().insertTransaction(payTransactionEntity)
            syncManager.syncSingleTransactionToCloud(payTransactionEntity.copy(id = generatedId))
        }
    }

    private suspend fun executePureTransaction(item: JSONObject, finalAmount: Double, targetTimestamp: Long) {
        val cleanNote = item.optString("clean_note", "Transaksi AI").trim().uppercase(Locale.ROOT)
        var type = item.optString("type", "EXPENSE").trim().uppercase(Locale.ROOT)
        if (cleanNote.contains("GAJI") || cleanNote.contains("PAYDAY") || cleanNote.contains("PEMASUKAN")) { type = "INCOME" }
        var catId = item.optLong("category_id", 15L)
        var catName = item.optString("category_name", "Lain-lain / Umum").trim()
        if (type == "INCOME") { catId = 1L; catName = "Gaji & Pendapatan" }

        val transactionEntity = TransactionEntity(
            amount = finalAmount, type = type, categoryId = catId, categoryName = catName, note = cleanNote, timestamp = targetTimestamp
        )
        val generatedId = db.transactionDao().insertTransaction(transactionEntity)
        syncManager.syncSingleTransactionToCloud(transactionEntity.copy(id = generatedId))
    }

    private suspend fun compileAiReport(cleanJsonStr: String, aiResponse: String): String {
        val allTx = db.transactionDao().getAllTransactions().first()
        val json = JSONObject(cleanJsonStr)
        val filterObj = json.optJSONObject("report_filter")
        
        val timeRange = filterObj?.optString("time_range", "MONTHLY") ?: "MONTHLY"
        val targetDateStr = filterObj?.optString("target_date", "") ?: ""
        val targetCategory = filterObj?.optString("target_category", "")?.uppercase(Locale.ROOT) ?: ""

        var incSum = 0.0
        var expSum = 0.0
        val calToday = Calendar.getInstance()
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID"))

        allTx.forEach { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            var isTimeMatch = false

            // 1. EVALUASI RENTANG WAKTU SECARA AKURAT
            when (timeRange) {
                "TODAY" -> {
                    isTimeMatch = txCal.get(Calendar.DAY_OF_YEAR) == calToday.get(Calendar.DAY_OF_YEAR) && 
                                  txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                }
                "WEEKLY" -> {
                    isTimeMatch = txCal.get(Calendar.WEEK_OF_YEAR) == calToday.get(Calendar.WEEK_OF_YEAR) && 
                                  txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                }
                "MONTHLY" -> {
                    isTimeMatch = txCal.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && 
                                  txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                }
                "YEARLY" -> {
                    isTimeMatch = txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                }
                "CUSTOM_DATE" -> {
                    if (targetDateStr.isNotEmpty()) {
                        val txDateStr = sdfDate.format(Date(tx.timestamp))
                        isTimeMatch = txDateStr == targetDateStr
                    }
                }
                "ALL" -> isTimeMatch = true
            }

            // 2. EVALUASI PENYARINGAN KATEGORI JIKA USER MEMINTA
            if (isTimeMatch && targetCategory.isNotEmpty()) {
                val currentTxCat = tx.categoryName.uppercase(Locale.ROOT)
                val currentTxNote = tx.note.uppercase(Locale.ROOT)
                // Filter ketat: jika nama kategori tidak cocok dan nota tidak mengandung nama kategori tersebut, lewati!
                if (!currentTxCat.contains(targetCategory) && !currentTxNote.contains(targetCategory)) {
                    isTimeMatch = false
                }
            }

            // Akumulasikan jika lolos sensor waktu dan kategori[span_1](start_span)[span_1](end_span)
            if (isTimeMatch) {
                val tUpper = tx.type.trim().uppercase(Locale.ROOT)
                if (tUpper == "INCOME" || tUpper == "DEBT") incSum += tx.amount
                if (tUpper == "EXPENSE" || tUpper == "RECEIVABLE") expSum += tx.amount
            }
        }

        // Susun teks balasan laporan yang rapi untuk ditampilkan di chat
        val rentangLabel = when (timeRange) {
            "TODAY" -> "Hari Ini"
            "WEEKLY" -> "Minggu Ini"
            "YEARLY" -> "Tahun Ini"
            "CUSTOM_DATE" -> "Pada Tanggal $targetDateStr"
            else -> "Bulan Ini"
        }
        
        val kategoriLabel = if (targetCategory.isNotEmpty()) " untuk Kategori [$targetCategory]" else ""

        return "📊 **Laporan Finansial Riil $rentangLabel$kategoriLabel Anda, Mam:**\n\n" +
               "🟢 Total Arus Masuk: ${formatRupiah.format(incSum)}\n" +
               "🔴 Total Arus Keluar: ${formatRupiah.format(expSum)}\n" +
               "💰 Sisa Kas Bersih: ${formatRupiah.format(incSum - expSum)}\n\n" +
               "Data diproses secara akurat berdasarkan catatan database internal aplikasi."
    }
