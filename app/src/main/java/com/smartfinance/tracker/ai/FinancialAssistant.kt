package com.smartfinance.tracker.ai

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.smartfinance.tracker.utils.FirebaseManager

class FinancialAssistant(private val context: Context) {

    private val firestore = FirebaseManager.getFirestore()
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
            val cleanAiResponseUpper = aiResponse.uppercase(Locale.ROOT)
            val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)

            if (cleanAiResponseUpper.contains("YA") || cleanAiResponseUpper.contains("LANJUT") || cleanAiResponseUpper.contains("BENAR") || cleanAiResponseUpper.contains("CATAT")) {
                val savedTxStr = prefs.getString("pending_tx", null)
                if (savedTxStr != null) {
                    val savedItem = JSONObject(savedTxStr)
                    val amount = parseAmount(savedItem)
                    if (amount > 0.0) {
                        executePureTransaction(savedItem, amount, System.currentTimeMillis())
                        prefs.edit().remove("pending_tx").apply()
                        return "✅ Siap Mam! Transaksi yang tertunda tadi sudah berhasil saya catat ke Cloud."
                    }
                }
            }

            if (actionType == "CHAT_ONLY") {
                val pendingJson = json.optJSONObject("pending_transaction")
                if (pendingJson != null && pendingJson.length() > 0) {
                    val pAmt = parseAmount(pendingJson)
                    if (pAmt > 0.0) {
                        prefs.edit().putString("pending_tx", pendingJson.toString()).apply()
                    }
                }
                return aiResponse.ifEmpty { "Ada yang bisa dibantu lagi, Mam?" }
            }

            if (actionType == "VIEW_CATEGORIES") return renderBeautifulCategoryList()
            if (actionType == "VIEW_REPORT") return compileAiReport(cleanJsonStr)

            if (actionType == "CREATE_CATEGORY") {
                val newCat = json.optJSONObject("new_category")
                if (newCat != null) {
                    val catName = newCat.optString("name", "").trim()
                    val catType = newCat.optString("type", "EXPENSE").uppercase(Locale.ROOT)
                    val pIdStr = newCat.optString("parent_category_id", "")
                    val parsedParentId: Long? = if (pIdStr.isEmpty() || pIdStr == "null") null else pIdStr.toLongOrNull()
                    
                    if (catName.isNotEmpty()) {
                        val newId = System.currentTimeMillis()
                        val newCatMap = hashMapOf("id" to newId, "name" to catName, "type" to catType, "iconName" to "ic_custom", "parentCategoryId" to parsedParentId, "isLocked" to false)
                        firestore.collection("categories").document("cat_$newId").set(newCatMap).await()
                        return aiResponse.ifEmpty { "✅ Kategori baru **$catName** berhasil ditambahkan ke Cloud!" }
                    }
                }
                return "❌ Gagal membuat kategori, format instruksi kurang lengkap."
            }

                val txArray = json.optJSONArray("transactions")
            if (txArray != null && txArray.length() > 0) {
                var isSuccess = false // Jaring pengaman untuk mendeteksi apakah data benar-benar tersimpan
                
                for (i in 0 until txArray.length()) {
                    val item = txArray.getJSONObject(i)
                    val customDateStr = item.optString("transaction_date", "").trim()
                    val targetTimestamp = parseTransactionDateTime(customDateStr)
                    
                    // Tarik nominal dengan fungsi baru yang lebih kebal
                    val finalAmount = parseAmount(item)
                    if (finalAmount <= 0.0) continue
                    
                    var contactNameRaw = item.optString("contact_name", "").trim().uppercase(Locale.ROOT)
                    if (contactNameRaw.isEmpty() || contactNameRaw == "TEMAN" || contactNameRaw == "BERI") {
                        contactNameRaw = dynamicContactNameExtractor(cleanAiResponseUpper, userMessageKeyword = cleanJsonStr)
                    }

                    // 🔥 FIX: Evaluasi super longgar. Apapun actionType-nya, kalau bukan hutang, PAKSA CATAT!
                    when {
                        actionType.contains("DEBT_RECORD") -> {
                            val isReceivableFlow = item.optString("debt_type", "DEBT").uppercase(Locale.ROOT) == "RECEIVABLE"
                            executeDirectDebtRecord(contactNameRaw, finalAmount, isReceivableFlow, targetTimestamp)
                            isSuccess = true
                        }
                        actionType.contains("DEBT_PAYMENT") -> { 
                            val msg = executeDirectDebtPayment(contactNameRaw, finalAmount, aiResponse, targetTimestamp) 
                            isSuccess = true
                            if (i == txArray.length() - 1) return msg
                        }
                        else -> { 
                            // 🚀 JARING PENGAMAN: Walau AI halusinasi ngasih actionType "EXPENSE", "ADD_TX", dll
                            executePureTransaction(item, finalAmount, targetTimestamp)
                            isSuccess = true
                        }
                    }
                }
                
                return if (isSuccess) {
                    aiResponse.ifEmpty { "✅ Sip Mam, transaksi berhasil diamankan ke Cloud!" }
                } else {
                    "⚠️ Peringatan: Transaksi gagal dicatat karena AI mengembalikan nominal yang tidak valid (Rp 0)."
                }
            }

    private suspend fun renderBeautifulCategoryList(): String {
        val snapshot = firestore.collection("categories").get().await()
        val allCats = snapshot.documents.mapNotNull { it.data }
        val parents = allCats.filter { it["parentCategoryId"] == null }.sortedBy { it["name"] as? String ?: "" }
        val subs = allCats.filter { it["parentCategoryId"] != null }

        if (parents.isEmpty()) return "Maaf Mam, belum ada kategori terdaftar di Cloud."

        val sb = java.lang.StringBuilder("🗂️ **Daftar Kategori Finansial Mam:**\n\n")
        
        val types = listOf(
            "INCOME" to "🟢 PEMASUKAN", 
            "EXPENSE" to "🔴 PENGELUARAN", 
            "DEBT" to "🟡 HUTANG", 
            "RECEIVABLE" to "🔵 PIUTANG"
        )
        
        for ((typeCode, typeLabel) in types) {
            val typeParents = parents.filter { (it["type"] as? String)?.uppercase(Locale.ROOT) == typeCode }
            if (typeParents.isNotEmpty()) {
                sb.append("=========================\n")
                sb.append("**$typeLabel**\n")
                sb.append("=========================\n")
                for (p in typeParents) {
                    val pId = p["id"] as? Long ?: 0L
                    val pName = p["name"] as? String ?: "Tanpa Nama"
                    sb.append("📁 **$pName**\n")
                    val kids = subs.filter { (it["parentCategoryId"] as? Number)?.toLong() == pId }.sortedBy { it["name"] as? String ?: "" }
                    for (k in kids) {
                        val kName = k["name"] as? String ?: "Tanpa Nama"
                        sb.append("   └── 💰 $kName\n")
                    }
                }
                sb.append("\n")
            }
        }
        return sb.toString().trimEnd()
    }

    suspend fun executeDirectDebtRecord(name: String, amountValue: Double, isReceivable: Boolean, timestampValue: Long) {
        val selectedType = if (isReceivable) "RECEIVABLE" else "DEBT"
        val debtId = "debt_${System.currentTimeMillis()}"
        val sanitizedName = name.ifEmpty { "TEMAN" }.uppercase(Locale.ROOT)

        val debtMap = hashMapOf("id" to debtId, "contactName" to sanitizedName, "contactPhoneNumber" to "0812", "amount" to amountValue, "remainingAmount" to amountValue, "type" to selectedType, "note" to "Dicatat Otomatis oleh AI", "timestamp" to timestampValue, "isPaid" to false)
        firestore.collection("debts").document(debtId).set(debtMap).await()

        val flowType = if (selectedType == "RECEIVABLE") "EXPENSE" else "INCOME"
        val catId = if (selectedType == "RECEIVABLE") 104L else 101L
        val catName = if (selectedType == "RECEIVABLE") "Piutang" else "Hutang"
        val txId = "tx_${System.currentTimeMillis()}"
        val standardizedNote = if (selectedType == "RECEIVABLE") "MEMBERIKAN PINJAMAN KEPADA $sanitizedName" else "MENERIMA PINJAMAN DARI $sanitizedName"

        val txMap = hashMapOf("id" to txId, "amount" to amountValue, "type" to flowType, "categoryId" to catId, "categoryName" to catName, "note" to standardizedNote, "timestamp" to timestampValue, "debtId" to debtId)
        firestore.collection("transactions").document(txId).set(txMap).await()
    }

    private suspend fun executeDirectDebtPayment(contactNameRaw: String, finalAmount: Double, originalAiResponse: String, targetTimestamp: Long): String {
        val snapshot = firestore.collection("debts").get().await()
        var matchDocId: String? = null; var matchAmount = 0.0; var matchType = "DEBT"
        var matchContactName = contactNameRaw.ifEmpty { "TEMAN" }.uppercase(Locale.ROOT)

        val inputTokens = contactNameRaw.uppercase(Locale.ROOT).split(" ").filter { it.length > 2 }
        for (doc in snapshot.documents) {
            val isPaid = doc.getBoolean("isPaid") ?: false
            if (!isPaid) {
                val dbName = (doc.getString("contactName") ?: "").uppercase(Locale.ROOT).trim()
                val remainingAmount = doc.getDouble("remainingAmount") ?: 0.0
                var isTokenMatch = false
                for (token in inputTokens) { if (dbName.contains(token)) { isTokenMatch = true; break } }

                if (isTokenMatch || dbName.contains(contactNameRaw.uppercase(Locale.ROOT)) || contactNameRaw.uppercase(Locale.ROOT).contains(dbName)) {
                    matchDocId = doc.id; matchAmount = remainingAmount; matchType = doc.getString("type") ?: "DEBT"
                    matchContactName = dbName; break
                }
            }
        }

        if (matchDocId != null) {
            val isPelunasan = originalAiResponse.uppercase(Locale.ROOT).contains("MELUNASI") || finalAmount >= matchAmount
            val targetPayAmount = if (isPelunasan) matchAmount else finalAmount
            val nextRemaining = (matchAmount - targetPayAmount).coerceAtLeast(0.0)

            firestore.collection("debts").document(matchDocId).update("remainingAmount", nextRemaining, "isPaid", nextRemaining <= 0.0).await()

            val txType = if (matchType == "DEBT") "EXPENSE" else "INCOME"
            val catId = if (matchType == "DEBT") 102L else 103L
            val catName = if (matchType == "DEBT") "Pembayaran kembali" else "Penagihan Utang"
            val txId = "tx_${System.currentTimeMillis()}"
            val standardizedNote = if (matchType == "DEBT") "MEMBAYAR CICILAN UTANG KE $matchContactName" else "MENERIMA CICILAN PIUTANG DARI $matchContactName"

            val payTxMap = hashMapOf("id" to txId, "amount" to targetPayAmount, "type" to txType, "categoryId" to catId, "categoryName" to catName, "note" to standardizedNote, "timestamp" to targetTimestamp, "debtId" to matchDocId)
            firestore.collection("transactions").document(txId).set(payTxMap).await()

            val statusLunasText = if (nextRemaining <= 0.0) "LUNAS SEPENUHNYA ✅" else formatRupiah.format(nextRemaining)
            return "✅ **Sip Mam, Pembayaran Berhasil Dicatat!**\n\n👤 Kontak: $matchContactName\n💵 Nominal: ${formatRupiah.format(targetPayAmount)}\n📊 Sisa: $statusLunasText\n\n$originalAiResponse"
        }
        return originalAiResponse.ifEmpty { "✅ Pencatatan diproses." }
    }

    private suspend fun executePureTransaction(item: JSONObject, finalAmount: Double, targetTimestamp: Long) {
        val cleanNote = item.optString("clean_note", "Transaksi AI").trim().uppercase(Locale.ROOT)
        val type = item.optString("type", "EXPENSE").trim().uppercase(Locale.ROOT)

        var catName = item.optString("category_name", "Lain-lain / Umum").trim()
        var catId = item.optLong("category_id", 15L)

        if (catName.isEmpty() || catName == "Lain-lain / Umum") {
            if (type == "INCOME") { catId = 1L; catName = "Gaji & Pendapatan" } else { catId = 15L; catName = "Lain-lain / Umum" }
        }

        val isNewCategory = item.optBoolean("is_new_category", false)
        if (isNewCategory && catId > 200L) {
            val pIdStr = item.optString("parent_category_id", "")
            val parsedParentId: Long? = if (pIdStr.isEmpty() || pIdStr == "null") null else pIdStr.toLongOrNull()
            val newCatMap = hashMapOf("id" to catId, "name" to catName, "type" to type, "iconName" to "ic_custom", "parentCategoryId" to parsedParentId, "isLocked" to false)
            firestore.collection("categories").document("cat_$catId").set(newCatMap).await()
        }

        val txId = "tx_${System.currentTimeMillis()}"
        val finalNoteStr = cleanNote.ifEmpty { "TRANSAKSI $catName" }.uppercase(Locale.ROOT)
        val txMap = hashMapOf("id" to txId, "amount" to finalAmount, "type" to type, "categoryId" to catId, "categoryName" to catName, "note" to finalNoteStr, "timestamp" to targetTimestamp)
        firestore.collection("transactions").document(txId).set(txMap).await()
    }

    private suspend fun compileAiReport(cleanJsonStr: String): String {
        val snapshot = firestore.collection("transactions").get().await()
        val json = JSONObject(cleanJsonStr)
        val filterObj = json.optJSONObject("report_filter")
        
        val reportType = filterObj?.optString("report_type", "SUMMARY") ?: "SUMMARY"
        val timeRange = filterObj?.optString("time_range", "MONTHLY")?.uppercase(Locale.ROOT) ?: "MONTHLY"
        val targetCategory = filterObj?.optString("target_category", "")?.uppercase(Locale.ROOT)?.trim() ?: ""
        val targetKeyword = filterObj?.optString("target_keyword", "")?.uppercase(Locale.ROOT)?.trim() ?: ""

        val startDateStr = filterObj?.optString("start_date", "") ?: ""
        val endDateStr = filterObj?.optString("end_date", "") ?: ""

        val sdfDate = SimpleDateFormat("dd-MM-yyyy", Locale("id", "ID"))
        var startTs = 0L; var endTs = Long.MAX_VALUE

        if (timeRange == "CUSTOM_RANGE") {
            try {
                if (startDateStr.isNotEmpty()) {
                    val cal = Calendar.getInstance().apply { time = sdfDate.parse(startDateStr)!! }
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    startTs = cal.timeInMillis
                }
                if (endDateStr.isNotEmpty()) {
                    val cal = Calendar.getInstance().apply { time = sdfDate.parse(endDateStr)!! }
                    cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                    endTs = cal.timeInMillis
                } else if (startDateStr.isNotEmpty()) {
                    val cal = Calendar.getInstance().apply { time = sdfDate.parse(startDateStr)!! }
                    cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                    endTs = cal.timeInMillis
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        var incSum = 0.0; var expSum = 0.0
        val matchedTransactions = mutableListOf<Map<String, Any>>()
        
        val calToday = Calendar.getInstance()
        val calLastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }

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
                "LAST_MONTH" -> isTimeMatch = txCal.get(Calendar.MONTH) == calLastMonth.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calLastMonth.get(Calendar.YEAR)
                "YEARLY" -> isTimeMatch = txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                "CUSTOM_RANGE" -> isTimeMatch = timestamp in startTs..endTs
                "ALL" -> isTimeMatch = true
                else -> isTimeMatch = true
            }

            if (isTimeMatch) {
                var matchCat = true; var matchKey = true
                if (targetCategory.isNotEmpty()) {
                    matchCat = currentCategoryName.contains(targetCategory) || targetCategory.contains(currentCategoryName)
                }
                if (targetKeyword.isNotEmpty()) {
                    matchKey = note.contains(targetKeyword) || targetKeyword.contains(note) || currentCategoryName.contains(targetKeyword)
                }

                if (matchCat && matchKey) {
                    val tUpper = type.trim().uppercase(Locale.ROOT)
                    if (tUpper == "INCOME" || tUpper == "DEBT") incSum += amt
                    if (tUpper == "EXPENSE" || tUpper == "RECEIVABLE") expSum += amt
                    
                    matchedTransactions.add(mapOf("note" to note, "category" to currentCategoryName, "amount" to amt, "date" to timestamp, "flow" to tUpper))
                }
            }
        }

        val rentangLabel = when (timeRange) {
            "TODAY" -> "Hari Ini"; "WEEKLY" -> "Minggu Ini"; "MONTHLY" -> "Bulan Ini"; "LAST_MONTH" -> "Bulan Lalu"; "YEARLY" -> "Tahun Ini"
            "CUSTOM_RANGE" -> if (startDateStr == endDateStr) "Tanggal $startDateStr" else "Periode $startDateStr s/d $endDateStr"
            else -> "Semua Waktu"
        }
        var lingkupLabel = ""
        if (targetCategory.isNotEmpty()) lingkupLabel += "\n📂 Kategori: $targetCategory"
        if (targetKeyword.isNotEmpty()) lingkupLabel += "\n🔍 Pencarian: $targetKeyword"

        val expenseList = matchedTransactions.filter { it["flow"] == "EXPENSE" || it["flow"] == "RECEIVABLE" }

        if (reportType == "ITEM_DETAILS") {
            if (matchedTransactions.isEmpty()) return "📊 **Rincian Transaksi Mam ($rentangLabel)**\nBelum ada data untuk pencarian ini."
            
            val sb = java.lang.StringBuilder("📝 **Rincian Transaksi Mam ($rentangLabel)**$lingkupLabel\n\n")
            val sortedList = matchedTransactions.sortedByDescending { it["date"] as Long }
            val sdfItem = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale("id", "ID"))
            
            sortedList.forEachIndexed { index, map -> 
                val dateStr = sdfItem.format(Date(map["date"] as Long))
                val flow = map["flow"] as String
                val icon = if (flow == "INCOME" || flow == "DEBT") "🟢" else "🔴"
                sb.append("${index + 1}. **${map["note"]}**\n   └ $dateStr | ${map["category"]} | $icon ${formatRupiah.format(map["amount"])}\n\n")
            }
            sb.append("==========================\n")
            if (incSum > 0) sb.append("🟢 Total Pemasukan: ${formatRupiah.format(incSum)}\n")
            if (expSum > 0) sb.append("🔴 Total Pengeluaran: ${formatRupiah.format(expSum)}\n")
            return sb.toString().trimEnd()
        }

        if (reportType == "TOP_EXPENSE") {
            val topItems = expenseList.sortedByDescending { it["amount"] as Double }.take(5)
            if (topItems.isEmpty()) return "📊 **Laporan Pengeluaran Tertinggi Mam ($rentangLabel)**\nBelum ada data pengeluaran."
            val sb = java.lang.StringBuilder("🔥 **Laporan Top 5 Pengeluaran Tertinggi Mam ($rentangLabel)**$lingkupLabel\n\n")
            topItems.forEachIndexed { index, map -> 
                val dateStr = SimpleDateFormat("dd MMM", Locale("id", "ID")).format(Date(map["date"] as Long))
                sb.append("${index + 1}. **${map["note"]}**\n   └ $dateStr | Kategori: ${map["category"]} | ${formatRupiah.format(map["amount"])}\n\n")
            }
            return sb.toString().trimEnd()
        }

        if (reportType == "CATEGORY_BREAKDOWN") {
            val grouped = expenseList.groupBy { it["category"] as String }
            val summed = grouped.mapValues { it.value.sumOf { item -> item["amount"] as Double } }
            val sortedGroups = summed.toList().sortedByDescending { it.second }
            if (sortedGroups.isEmpty()) return "📊 **Rincian Kategori Mam ($rentangLabel)**\nBelum ada data."
            val sb = java.lang.StringBuilder("📑 **Rincian Pengeluaran Per Kategori Mam ($rentangLabel)**$lingkupLabel\n\n")
            sortedGroups.forEach { (cat, total) -> sb.append("📁 **$cat** : ${formatRupiah.format(total)}\n") }
            sb.append("\n🔴 **Total Keseluruhan:** ${formatRupiah.format(expSum)}")
            return sb.toString().trimEnd()
        }

        return "📊 **Ringkasan Finansial Mam ($rentangLabel)**\n" +
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
            if (dateStr.contains(":")) { SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID")).parse(dateStr.trim())?.time ?: System.currentTimeMillis() } 
            else { SimpleDateFormat("dd-MM-yyyy", Locale("id", "ID")).parse(dateStr.trim())?.time ?: System.currentTimeMillis() }
        } catch (e: Exception) { System.currentTimeMillis() }
    }

    private fun parseAmount(item: JSONObject): Double {
        return try {
            var rawValue: Any? = null
            // Cari berbagai kemungkinan kunci yang sering dihalusinasikan AI
            val possibleKeys = listOf("amount", "nominal", "harga", "total", "value")
            for (key in possibleKeys) {
                if (item.has(key)) {
                    rawValue = item.get(key)
                    break
                }
            }

            if (rawValue == null) return 0.0

            // Jika AI sudah memberikannya dalam bentuk angka murni
            if (rawValue is Number) return rawValue.toDouble()

            // Jika AI memberikan String ("Rp 20.000", "20.000", "20 ribu")
            val stringValue = rawValue.toString()
            val cleanDigitsOnly = stringValue.replace(Regex("[^0-9]"), "")
            cleanDigitsOnly.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }
    
    private fun dynamicContactNameExtractor(text: String, userMessageKeyword: String): String {
        val databasePopulerNames = listOf("JOKO", "ARNETA", "ADIT", "DANI", "ARIANTO", "BUDI", "ARI", "BAYU", "AJI", "LILIK", "DIKAH")
        val textUpper = text.uppercase(Locale.ROOT)
        val msgUpper = userMessageKeyword.uppercase(Locale.ROOT)
        for (name in databasePopulerNames) { if (textUpper.contains(name) || msgUpper.contains(name)) return name }
        return "TEMAN"
    }
}
