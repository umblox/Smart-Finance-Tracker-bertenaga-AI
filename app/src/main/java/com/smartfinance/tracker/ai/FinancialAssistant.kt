package com.smartfinance.tracker.ai

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class FinancialAssistant(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    suspend fun parseAndExecuteRawAiResponse(rawText: String): String {
        var cleanJsonStr = rawText.trim()
        if (cleanJsonStr.startsWith("```json")) {
            cleanJsonStr = cleanJsonStr.removePrefix("```json")
        } else if (cleanJsonStr.startsWith("```")) {
            cleanJsonStr = cleanJsonStr.removePrefix("```")
        }
        if (cleanJsonStr.endsWith("```")) {
            cleanJsonStr = cleanJsonStr.removeSuffix("```")
        }
        cleanJsonStr = cleanJsonStr.trim()

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
                    if (contactNameRaw.isEmpty() || contactNameRaw == "TEMAN" || contactNameRaw == "BERI" || contactNameRaw == "TOLONG") {
                        contactNameRaw = dynamicContactNameExtractor(cleanAiResponseUpper, userMessageKeyword = cleanJsonStr)
                    }

                    when (actionType) {
                        "TRANSACTION" -> {
                            executePureTransaction(item, finalAmount, targetTimestamp)
                        }
                        "DEBT_RECORD" -> {
                            val isReceivableFlow = cleanAiResponseUpper.contains("PIUTANG") || 
                                                   cleanAiResponseUpper.contains("MEMINJAMKAN") || 
                                                   cleanAiResponseUpper.contains("KEPADA ANDA")
                            
                            executeDirectDebtRecord(contactNameRaw, finalAmount, isReceivableFlow, targetTimestamp)
                        }
                        "DEBT_PAYMENT" -> {
                            executeDirectDebtPayment(contactNameRaw, finalAmount, cleanAiResponseUpper, targetTimestamp)
                        }
                    }
                }
            }
            return aiResponse.ifEmpty { "Catatan keuangan berhasil diproses ke Firebase Cloud, Mam!" }
        } catch (e: Exception) {
            return "❌ Kalimat transaksi tidak dikenali oleh sistem awan."
        }
    }

    suspend fun executeDirectDebtRecord(name: String, amountValue: Double, isReceivable: Boolean, timestampValue: Long) {
        val selectedType = if (isReceivable) "RECEIVABLE" else "DEBT"
        val debtId = "debt_${System.currentTimeMillis()}"
        val sanitizedName = name.ifEmpty { "TEMAN" }.uppercase(Locale.ROOT)

        val debtMap = hashMapOf(
            "id" to debtId,
            "contactName" to sanitizedName,
            "contactPhoneNumber" to "0812",
            "amount" to amountValue,
            "remainingAmount" to amountValue,
            "type" to selectedType,
            "note" to "Proses Otomatis via AI Cloud Premium",
            "timestamp" to timestampValue,
            "isPaid" to false
        )
        firestore.collection("debts").document(debtId).set(debtMap).await()

        val flowType = if (selectedType == "RECEIVABLE") "EXPENSE" else "INCOME"
        val catId = if (selectedType == "RECEIVABLE") 104L else 101L
        val catName = if (selectedType == "RECEIVABLE") "Piutang" else "Hutang"
        val txId = "tx_${System.currentTimeMillis()}"

        val txMap = hashMapOf(
            "id" to txId,
            "amount" to amountValue,
            "type" to flowType,
            "categoryId" to catId,
            "categoryName" to catName,
            "note" to "[$selectedType] $sanitizedName - INPUT AUTOMATIC AI",
            "timestamp" to timestampValue
        )
        firestore.collection("transactions").document(txId).set(txMap).await()
    }

    private suspend fun executeDirectDebtPayment(contactNameRaw: String, finalAmount: Double, aiResponseUpper: String, targetTimestamp: Long) {
        val snapshot = firestore.collection("debts").get().await()
        
        var matchDocId: String? = null
        var matchAmount = 0.0
        var matchType = "DEBT"
        var matchContactName = contactNameRaw.ifEmpty { "TEMAN" }

        for (doc in snapshot.documents) {
            val isPaid = doc.getBoolean("isPaid") ?: false
            if (!isPaid) {
                val dbName = (doc.getString("contactName") ?: "").uppercase(Locale.ROOT)
                val remainingAmount = doc.getDouble("remainingAmount") ?: 0.0
                
                if (dbName == contactNameRaw.uppercase(Locale.ROOT) || aiResponseUpper.contains(dbName) || finalAmount == remainingAmount) {
                    matchDocId = doc.id
                    matchAmount = remainingAmount
                    matchType = doc.getString("type") ?: "DEBT"
                    matchContactName = dbName
                    break
                }
            }
        }

        if (matchDocId != null) {
            val isPelunasan = aiResponseUpper.contains("MELUNASI") || aiResponseUpper.contains("LUNAS")
            val targetPayAmount = if (isPelunasan) matchAmount else finalAmount
            val nextRemaining = (matchAmount - targetPayAmount).coerceAtLeast(0.0)

            firestore.collection("debts").document(matchDocId).update(
                "remainingAmount", nextRemaining,
                "isPaid", nextRemaining <= 0.0
            ).await()

            val txType = if (matchType == "DEBT") "EXPENSE" else "INCOME"
            val catId = if (matchType == "DEBT") 102L else 103L
            val catName = if (matchType == "DEBT") "Pembayaran kembali" else "Penagihan Utang"
            val txId = "tx_${System.currentTimeMillis()}"

            val payTxMap = hashMapOf(
                "id" to txId,
                "amount" to targetPayAmount,
                "type" to txType,
                "categoryId" to catId,
                "categoryName" to catName,
                "note" to "[$catName] $matchContactName - CICILAN AI CLOUD",
                "timestamp" to targetTimestamp
            )
            firestore.collection("transactions").document(txId).set(payTxMap).await()
        }
    }

    private suspend fun executePureTransaction(item: JSONObject, finalAmount: Double, targetTimestamp: Long) {
        val cleanNote = item.optString("clean_note", "Transaksi AI").trim().uppercase(Locale.ROOT)
        var type = item.optString("type", "EXPENSE").trim().uppercase(Locale.ROOT)
        if (cleanNote.contains("GAJI") || cleanNote.contains("PAYDAY") || cleanNote.contains("PEMASUKAN")) { type = "INCOME" }
        var catId = item.optLong("category_id", 15L)
        var catName = item.optString("category_name", "Lain-lain / Umum").trim()
        if (type == "INCOME") { catId = 1L; catName = "Gaji & Pendapatan" }

        val txId = "tx_${System.currentTimeMillis()}"
        val txMap = hashMapOf(
            "id" to txId,
            "amount" to finalAmount,
            "type" to type,
            "categoryId" to catId,
            "categoryName" to catName,
            "note" to cleanNote,
            "timestamp" to targetTimestamp
        )
        firestore.collection("transactions").document(txId).set(txMap).await()
    }

    private suspend fun compileAiReport(cleanJsonStr: String, aiResponse: String): String {
        val snapshot = firestore.collection("transactions").get().await()
        val json = JSONObject(cleanJsonStr)
        val filterObj = json.optJSONObject("report_filter")
        
        val timeRange = filterObj?.optString("time_range", "MONTHLY") ?: "MONTHLY"
        val targetDateStr = filterObj?.optString("target_date", "") ?: ""
        val targetCategory = filterObj?.optString("target_category", "")?.uppercase(Locale.ROOT) ?: ""

        var incSum = 0.0
        var expSum = 0.0
        val calToday = Calendar.getInstance()
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID"))

        for (doc in snapshot.documents) {
            val amt = doc.getDouble("amount") ?: 0.0
            val type = doc.getString("type") ?: "EXPENSE"
            val catName = doc.getString("categoryName") ?: "Umum"
            val note = doc.getString("note") ?: ""
            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

            val txCal = Calendar.getInstance().apply { timeInMillis = timestamp }
            var isTimeMatch = false

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
                        val txDateStr = sdfDate.format(Date(timestamp))
                        isTimeMatch = txDateStr == targetDateStr
                    }
                }
                "ALL" -> isTimeMatch = true
            }

            if (isTimeMatch && targetCategory.isNotEmpty()) {
                val currentTxCat = catName.uppercase(Locale.ROOT)
                val currentTxNote = note.uppercase(Locale.ROOT)
                if (!currentTxCat.contains(targetCategory) && !currentTxNote.contains(targetCategory)) {
                    isTimeMatch = false
                }
            }

            if (isTimeMatch) {
                val tUpper = type.trim().uppercase(Locale.ROOT)
                if (tUpper == "INCOME" || tUpper == "DEBT") incSum += amt
                if (tUpper == "EXPENSE" || tUpper == "RECEIVABLE") expSum += amt
            }
        }

        val rentangLabel = when (timeRange) {
            "TODAY" -> "Hari Ini"
            "WEEKLY" -> "Minggu Ini"
            "YEARLY" -> "Tahun Ini"
            "CUSTOM_DATE" -> "Pada Tanggal $targetDateStr"
            else -> "Bulan Ini"
        }
        
        val kategoriLabel = if (targetCategory.isNotEmpty()) " untuk Kategori [$targetCategory]" else ""

        return "📊 **Laporan Finansial Cloud $rentangLabel$kategoriLabel Anda, Mam:**\n\n" +
               "🟢 Total Arus Masuk: ${formatRupiah.format(incSum)}\n" +
               "🔴 Total Arus Keluar: ${formatRupiah.format(expSum)}\n" +
               "💰 Sisa Kas Bersih: ${formatRupiah.format(incSum - expSum)}\n\n" +
               "Data diproses secara aman dan real-time dari Firebase Firestore Cloud."
    }

    private fun parseTransactionDate(dateStr: String): Long {
        if (dateStr.trim().isEmpty()) return System.currentTimeMillis()
        return try { SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")).parse(dateStr.trim())?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
    }

    private fun parseAmount(item: JSONObject): Double {
        val amount = item.optDouble("amount", 0.0)
        return if (amount == 0.0) item.optString("amount", "0").toDoubleOrNull() ?: 0.0 else amount
    }

    private fun dynamicContactNameExtractor(text: String, userMessageKeyword: String): String {
        val databasePopulerNames = listOf("JOKO", "ARNETA", "ADIT", "DANI", "ARIANTO", "BUDI", "ARI", "BAYU", "AJI")
        val textUpper = text.uppercase(Locale.ROOT)
        val msgUpper = userMessageKeyword.uppercase(Locale.ROOT)
        
        for (name in databasePopulerNames) { 
            if (textUpper.contains(name) || msgUpper.contains(name)) return name 
        }
        return "TEMAN"
    }
}
