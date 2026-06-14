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
        cleanJsonStr = cleanJsonStr.replace(Regex("""^```json\s*"""), "")
        cleanJsonStr = cleanJsonStr.replace(Regex("""^
```\s*"""), "")
        cleanJsonStr = cleanJsonStr.replace(Regex("""\s*```$"""), "")
        cleanJsonStr = cleanJsonStr.trim()

        try {
            val json = JSONObject(cleanJsonStr)
            val actionType = json.optString("action_type", "CHAT_ONLY").trim().uppercase(Locale.ROOT)
            val aiResponse = json.optString("ai_response", "").trim()

            if (actionType == "CHAT_ONLY") {
                return aiResponse.ifEmpty { "Ada yang bisa dibantu lagi, Mam?" }
            }

            if (actionType == "VIEW_REPORT") {
                return compileAiReport(cleanJsonStr)
            }

            val txArray = json.optJSONArray("transactions")
            if (txArray != null && txArray.length() > 0) {
                for (i in 0 until txArray.length()) {
                    val item = txArray.getJSONObject(i)
                    
                    val customDateStr = item.optString("transaction_date", "").trim()
                    val targetTimestamp = parseTransactionDateTime(customDateStr)
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
                            val isReceivableFlow = item.optString("debt_type", "DEBT").uppercase(Locale.ROOT) == "RECEIVABLE"
                            executeDirectDebtRecord(contactNameRaw, finalAmount, isReceivableFlow, targetTimestamp)
                        }
                        "DEBT_PAYMENT" -> {
                            executeDirectDebtPayment(contactNameRaw, finalAmount, aiResponse, targetTimestamp)
                        }
                    }
                }
            }
            return aiResponse.ifEmpty { "Sip Mam, mutasi sudah berhasil diamankan ke Cloud!" }
        } catch (e: Exception) {
            return "❌ Maaf Mam, sistem gagal membaca maksud instruksi ini."
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
            "note" to "Dicatat Otomatis oleh AI",
            "timestamp" to timestampValue,
            "isPaid" to false
        )
        firestore.collection("debts").document(debtId).set(debtMap).await()

        val flowType = if (selectedType == "RECEIVABLE") "EXPENSE" else "INCOME"
        val catId = if (selectedType == "RECEIVABLE") 104L else 101L
        val catName = if (selectedType == "RECEIVABLE") "Piutang" else "Hutang"
        val txId = "tx_${System.currentTimeMillis()}"

        // 🔥 FIX CATATAN SERAGAM: Catatan lebih enak dibaca dan logis
        val standardizedNote = if (selectedType == "RECEIVABLE") "MEMBERIKAN PINJAMAN KEPADA $sanitizedName" else "MENERIMA PINJAMAN DARI $sanitizedName"

        val txMap = hashMapOf(
            "id" to txId,
            "amount" to amountValue,
            "type" to flowType,
            "categoryId" to catId,
            "categoryName" to catName,
            "note" to standardizedNote,
            "timestamp" to timestampValue,
            "debtId" to debtId
        )
        firestore.collection("transactions").document(txId).set(txMap).await()
    }

    private suspend fun executeDirectDebtPayment(contactNameRaw: String, finalAmount: Double, originalAiResponse: String, targetTimestamp: Long) {
        val snapshot = firestore.collection("debts").get().await()
        var matchDocId: String? = null
        var matchAmount = 0.0
        var matchType = "DEBT"
        var matchContactName = contactNameRaw.ifEmpty { "TEMAN" }.uppercase(Locale.ROOT)

        val inputTokens = contactNameRaw.uppercase(Locale.ROOT).split(" ").filter { it.length > 2 }
        val aiResponseUpper = originalAiResponse.uppercase(Locale.ROOT)

        for (doc in snapshot.documents) {
            val isPaid = doc.getBoolean("isPaid") ?: false
            if (!isPaid) {
                val dbName = (doc.getString("contactName") ?: "").uppercase(Locale.ROOT).trim()
                val remainingAmount = doc.getDouble("remainingAmount") ?: 0.0
                
                var isTokenMatch = false
                for (token in inputTokens) {
                    if (dbName.contains(token)) {
                        isTokenMatch = true
                        break
                    }
                }

                if (isTokenMatch || dbName.contains(contactNameRaw.uppercase(Locale.ROOT)) || contactNameRaw.uppercase(Locale.ROOT).contains(dbName)) {
                    matchDocId = doc.id
                    matchAmount = remainingAmount
                    matchType = doc.getString("type") ?: "DEBT"
                    matchContactName = dbName
                    break
                }
            }
        }

        if (matchDocId != null) {
            val isPelunasan = aiResponseUpper.contains("MELUNASI") || aiResponseUpper.contains("LUNAS") || finalAmount >= matchAmount
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

            // 🔥 FIX CATATAN SERAGAM
            val standardizedNote = if (matchType == "DEBT") "MEMBAYAR CICILAN UTANG KE $matchContactName" else "MENERIMA CICILAN PIUTANG DARI $matchContactName"

            val payTxMap = hashMapOf(
                "id" to txId,
                "amount" to targetPayAmount,
                "type" to txType,
                "categoryId" to catId,
                "categoryName" to catName,
                "note" to standardizedNote,
                "timestamp" to targetTimestamp,
                "debtId" to matchDocId
            )
            firestore.collection("transactions").document(txId).set(payTxMap).await()
        }
    }

    private suspend fun executePureTransaction(item: JSONObject, finalAmount: Double, targetTimestamp: Long) {
        val cleanNote = item.optString("clean_note", "Transaksi AI").trim().uppercase(Locale.ROOT)
        val type = item.optString("type", "EXPENSE").trim().uppercase(Locale.ROOT)

        var catName = item.optString("category_name", "Lain-lain / Umum").trim()
        var catId = item.optLong("category_id", 15L)

        if (catName.isEmpty() || catName == "Lain-lain / Umum") {
            if (type == "INCOME") {
                catId = 1L
                catName = "Gaji & Pendapatan"
            } else {
                catId = 15L
                catName = "Lain-lain / Umum"
            }
        }

        val isNewCategory = item.optBoolean("is_new_category", false)
        if (isNewCategory && catId > 200L) {
            // 🔥 PARSING PARENT CATEGORY: Membaca ID Induk jika ini adalah sub-kategori baru
            val pIdStr = item.optString("parent_category_id", "")
            val parsedParentId: Long? = if (pIdStr.isEmpty() || pIdStr == "null") null else pIdStr.toLongOrNull()

            val newCatMap = hashMapOf(
                "id" to catId,
                "name" to catName,
                "type" to type,
                "iconName" to "ic_custom",
                "parentCategoryId" to parsedParentId,
                "isLocked" to false
            )
            firestore.collection("categories").document("cat_$catId").set(newCatMap).await()
        }

        val txId = "tx_${System.currentTimeMillis()}"
        val finalNoteStr = cleanNote.ifEmpty { "TRANSAKSI $catName" }.uppercase(Locale.ROOT)

        val txMap = hashMapOf(
            "id" to txId,
            "amount" to finalAmount,
            "type" to type,
            "categoryId" to catId,
            "categoryName" to catName,
            "note" to finalNoteStr,
            "timestamp" to targetTimestamp
        )
        firestore.collection("transactions").document(txId).set(txMap).await()
    }

    private suspend fun compileAiReport(cleanJsonStr: String): String {
        val snapshot = firestore.collection("transactions").get().await()
        val json = JSONObject(cleanJsonStr)
        val filterObj = json.optJSONObject("report_filter")
        
        val timeRange = filterObj?.optString("time_range", "MONTHLY") ?: "MONTHLY"
        val targetDateStr = filterObj?.optString("target_date", "") ?: ""
        val targetCategory = filterObj?.optString("target_category", "")?.uppercase(Locale.ROOT)?.trim() ?: ""
        val targetKeyword = filterObj?.optString("target_keyword", "")?.uppercase(Locale.ROOT)?.trim() ?: ""

        var incSum = 0.0
        var expSum = 0.0
        val calToday = Calendar.getInstance()
        val sdfDate = SimpleDateFormat("dd-MM-yyyy", Locale("id", "ID"))

        for (doc in snapshot.documents) {
            val amt = doc.getDouble("amount") ?: 0.0
            val type = doc.getString("type") ?: "EXPENSE"
            val currentCategoryName = (doc.getString("categoryName") ?: "Umum").uppercase(Locale.ROOT).trim()
            val note = (doc.getString("note") ?: "").uppercase(Locale.ROOT).trim()
            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

            val txCal = Calendar.getInstance().apply { timeInMillis = timestamp }
            var isTimeMatch = false

            when (timeRange) {
                "TODAY" -> isTimeMatch = txCal.get(Calendar.DAY_OF_YEAR) == calToday.get(Calendar.DAY_OF_YEAR) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                "WEEKLY" -> isTimeMatch = txCal.get(Calendar.WEEK_OF_YEAR) == calToday.get(Calendar.WEEK_OF_YEAR) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                "MONTHLY" -> isTimeMatch = txCal.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                "YEARLY" -> isTimeMatch = txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                "CUSTOM_DATE" -> if (targetDateStr.isNotEmpty()) isTimeMatch = sdfDate.format(Date(timestamp)) == targetDateStr
                "ALL" -> isTimeMatch = true
            }

            if (isTimeMatch) {
                // 🔥 LOGIKA PENCARIAN SUPER LEBAR: Anti bug kategori 0
                var matchCat = true
                var matchKey = true
                
                if (targetCategory.isNotEmpty()) {
                    matchCat = currentCategoryName.contains(targetCategory) || note.contains(targetCategory)
                }
                if (targetKeyword.isNotEmpty()) {
                    matchKey = note.contains(targetKeyword) || currentCategoryName.contains(targetKeyword)
                }

                if (matchCat && matchKey) {
                    val tUpper = type.trim().uppercase(Locale.ROOT)
                    if (tUpper == "INCOME" || tUpper == "DEBT") incSum += amt
                    if (tUpper == "EXPENSE" || tUpper == "RECEIVABLE") expSum += amt
                }
            }
        }

        val rentangLabel = when (timeRange) {
            "TODAY" -> "Hari Ini"
            "WEEKLY" -> "Minggu Ini"
            "YEARLY" -> "Tahun Ini"
            "CUSTOM_DATE" -> "Tanggal $targetDateStr"
            else -> "Bulan Ini"
        }
        
        var lingkupLabel = ""
        if (targetCategory.isNotEmpty()) lingkupLabel += "\n📂 Kategori: $targetCategory"
        if (targetKeyword.isNotEmpty()) lingkupLabel += "\n🔍 Pencarian: $targetKeyword"

        // 🔥 TEMPLATE LAPORAN MEWAH & LUWES
        return "📊 **Laporan Kas Mam ($rentangLabel)**\n" +
               "==========================" +
               "$lingkupLabel\n\n" +
               "🟢 Pemasukan: ${formatRupiah.format(incSum)}\n" +
               "🔴 Pengeluaran: ${formatRupiah.format(expSum)}\n" +
               "==========================\n" +
               "💰 **Sisa Bersih: ${formatRupiah.format(incSum - expSum)}**\n\n" +
               "_(Data akurat ditarik dari Cloud)_"
    }

    private fun parseTransactionDateTime(dateStr: String): Long {
        if (dateStr.trim().isEmpty()) return System.currentTimeMillis()
        return try { 
            if (dateStr.contains(":")) {
                SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID")).parse(dateStr.trim())?.time ?: System.currentTimeMillis() 
            } else {
                SimpleDateFormat("dd-MM-yyyy", Locale("id", "ID")).parse(dateStr.trim())?.time ?: System.currentTimeMillis() 
            }
        } catch (e: Exception) { 
            System.currentTimeMillis() 
        }
    }

    private fun parseAmount(item: JSONObject): Double {
        val amount = item.optDouble("amount", 0.0)
        return if (amount == 0.0) item.optString("amount", "0").toDoubleOrNull() ?: 0.0 else amount
    }

    private fun dynamicContactNameExtractor(text: String, userMessageKeyword: String): String {
        val databasePopulerNames = listOf("JOKO", "ARNETA", "ADIT", "DANI", "ARIANTO", "BUDI", "ARI", "BAYU", "AJI", "LILIK", "DIKAH")
        val textUpper = text.uppercase(Locale.ROOT)
        val msgUpper = userMessageKeyword.uppercase(Locale.ROOT)
        for (name in databasePopulerNames) { if (textUpper.contains(name) || msgUpper.contains(name)) return name }
        return "TEMAN"
    }
}
