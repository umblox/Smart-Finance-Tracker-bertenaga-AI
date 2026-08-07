package com.smartfinance.tracker.ai

import android.content.Context
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.smartfinance.tracker.data.local.DatabaseProvider
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.data.model.Debt
import com.smartfinance.tracker.data.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FinancialAssistant(private val context: Context) {

    private val db = DatabaseProvider.db
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    suspend fun parseAndExecuteRawAiResponse(rawText: String, originalUserMessage: String): String = withContext(Dispatchers.IO) {
        var cleanJsonStr = rawText.trim()
        cleanJsonStr = cleanJsonStr.replace(Regex("""^```json\s*"""), "")
        cleanJsonStr = cleanJsonStr.replace(Regex("""^```\s*"""), "")
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
                        return@withContext "✅ Siap Mam! Transaksi yang tertunda tadi sudah berhasil saya catat ke Cloud."
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
                return@withContext aiResponse.ifEmpty { "Ada yang bisa dibantu lagi, Mam?" }
            }

            if (actionType == "VIEW_CATEGORIES") return@withContext renderBeautifulCategoryList()
            if (actionType == "VIEW_REPORT") return@withContext compileAiReport(cleanJsonStr)

            if (actionType == "CREATE_CATEGORY") {
                val newCatJson = json.optJSONObject("new_category")
                if (newCatJson != null) {
                    val catName = newCatJson.optString("name", "").trim()
                    val catType = newCatJson.optString("type", "EXPENSE").uppercase(Locale.ROOT)
                    val pIdStr = newCatJson.optString("parent_category_id", "")
                    val parsedParentId: Long? = if (pIdStr.isEmpty() || pIdStr == "null") null else pIdStr.toLongOrNull()
                    
                    if (catName.isNotEmpty()) {
                        val newId = System.currentTimeMillis()
                        val newCat = Category(
                            docId = "cat_$newId",
                            id = newId,
                            name = catName,
                            type = catType,
                            iconName = "ic_custom",
                            parentCategoryId = parsedParentId,
                            isLocked = false
                        )
                        db.categoryDao().insert(newCat)
                        return@withContext aiResponse.ifEmpty { "✅ Kategori baru **$catName** berhasil ditambahkan ke Cloud!" }
                    }
                }
                return@withContext "❌ Gagal membuat kategori, format instruksi kurang lengkap."
            }

            // 🔥 INTERCEPTOR MUTLAK: Mendeteksi paksa niat berhutang dari pengguna! (Kasus Ilyas)
            val msgUpper = originalUserMessage.uppercase(Locale.ROOT)
            val isDebtIntent = msgUpper.contains("PINJAM") || msgUpper.contains("HUTANG") || 
                               msgUpper.contains("PIUTANG") || msgUpper.contains("NGUTANG")
            
            var currentActionType = actionType
            if (currentActionType == "TRANSACTION" && isDebtIntent) {
                currentActionType = "DEBT_RECORD"
            }

            val txArray = json.optJSONArray("transactions")
            if (txArray != null && txArray.length() > 0) {
                var isSuccess = false 
                val batchBaseTime = System.currentTimeMillis()
                
                for (i in 0 until txArray.length()) {
                    val item = txArray.getJSONObject(i)
                    val customDateStr = item.optString("transaction_date", "").trim()
                    val targetTimestamp = parseTransactionDateTime(customDateStr, batchBaseTime) + (i * 1000L)
                    
                    val finalAmount = parseAmount(item)
                    if (finalAmount <= 0.0) continue
                    
                    var contactNameRaw = item.optString("contact_name", "").trim().uppercase(Locale.ROOT)
                    if (contactNameRaw.isEmpty() || contactNameRaw == "TEMAN" || contactNameRaw == "BERI") {
                        contactNameRaw = dynamicContactNameExtractor(originalUserMessage)
                    }

                    when {
                        currentActionType.contains("DEBT_RECORD") -> {
                            var isReceivableFlow = item.optString("debt_type", "").uppercase(Locale.ROOT) == "RECEIVABLE"
                            if (!item.has("debt_type") || item.optString("debt_type").isEmpty()) {
                                isReceivableFlow = !msgUpper.contains("SAYA PINJAM") && !msgUpper.contains("SAYA NGUTANG")
                            }
                            executeDirectDebtRecord(contactNameRaw, finalAmount, isReceivableFlow, targetTimestamp)
                            isSuccess = true
                        }
                        currentActionType.contains("DEBT_PAYMENT") -> { 
                            val msg = executeDirectDebtPayment(contactNameRaw, finalAmount, aiResponse, targetTimestamp) 
                            isSuccess = true
                            if (i == txArray.length() - 1) return@withContext msg
                        }
                        else -> { 
                            // 🔥 TRANSAKSI BIASA: Kembalikan (B/ Nama) agar Editor bisa menangkapnya!
                            if (contactNameRaw.isNotEmpty() && contactNameRaw != "TEMAN") {
                                val currentNote = item.optString("clean_note", "Transaksi AI")
                                val catId = item.optLong("category_id", 15L)
                                // Jika terlanjur masuk dengan ID Utang
                                if (catId in listOf(101L, 102L, 103L, 104L)) {
                                    val catName = item.optString("category_name", "Utang/Piutang")
                                    item.put("clean_note", "[$catName] $contactNameRaw - $currentNote")
                                } else {
                                    // Pasang Payload untuk Transaksi Biasa
                                    item.put("clean_note", "$currentNote (B/ $contactNameRaw)")
                                }
                            }
                            executePureTransaction(item, finalAmount, targetTimestamp)
                            isSuccess = true
                        }
                    }
                }
                
                if (isSuccess) return@withContext aiResponse.ifEmpty { "✅ Sip Mam, transaksi berhasil diamankan ke Cloud!" }
            }

            // ==========================================
            // FALLBACK BLOCK
            // ==========================================
            val fallbackItem = json.optJSONObject("pending_transaction") ?: json
            val fallbackAmount = parseAmount(fallbackItem)
            
            val isIntentionalTransaction = currentActionType.contains("TRANSACTION") || 
                                           currentActionType.contains("EXPENSE") || 
                                           currentActionType.contains("INCOME") || 
                                           currentActionType.contains("DEBT")

            if (fallbackAmount > 0.0 && isIntentionalTransaction) {
                val customDateStr = fallbackItem.optString("transaction_date", "").trim()
                val targetTimestamp = parseTransactionDateTime(customDateStr)
                
                var contactNameRaw = fallbackItem.optString("contact_name", "").trim().uppercase(Locale.ROOT)
                if (contactNameRaw.isEmpty() || contactNameRaw == "TEMAN" || contactNameRaw == "BERI") {
                    contactNameRaw = dynamicContactNameExtractor(originalUserMessage)
                }

                if (currentActionType.contains("DEBT_RECORD")) {
                    var isReceivableFlow = fallbackItem.optString("debt_type", "").uppercase(Locale.ROOT) == "RECEIVABLE"
                    if (!fallbackItem.has("debt_type") || fallbackItem.optString("debt_type").isEmpty()) {
                        isReceivableFlow = !msgUpper.contains("SAYA PINJAM") && !msgUpper.contains("SAYA NGUTANG")
                    }
                    executeDirectDebtRecord(contactNameRaw, fallbackAmount, isReceivableFlow, targetTimestamp)
                } else {
                    // 🔥 TRANSAKSI BIASA FALLBACK: Kembalikan (B/ Nama)
                    if (contactNameRaw.isNotEmpty() && contactNameRaw != "TEMAN") {
                        val currentNote = fallbackItem.optString("clean_note", "Transaksi AI")
                        val catId = fallbackItem.optLong("category_id", 15L)
                        
                        if (catId in listOf(101L, 102L, 103L, 104L)) {
                            val catName = fallbackItem.optString("category_name", "Utang/Piutang")
                            fallbackItem.put("clean_note", "[$catName] $contactNameRaw - $currentNote")
                        } else {
                            fallbackItem.put("clean_note", "$currentNote (B/ $contactNameRaw)")
                        }
                    }
                    executePureTransaction(fallbackItem, fallbackAmount, targetTimestamp)
                }
                return@withContext aiResponse.ifEmpty { "✅ Transaksi berhasil dicatat, Mam!" }
            }

            return@withContext aiResponse
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "❌ Maaf Mam, sistem AI mengalami error pemahaman. (Bisa cek menu Expert Mode)."
        }
    }

    private suspend fun renderBeautifulCategoryList(): String {
        // ... (Tidak diubah, untuk menghemat tempat)
        val allCats = db.categoryDao().getAllSync()
        val parents = allCats.filter { it.parentCategoryId == null }.sortedBy { it.name }
        val subs = allCats.filter { it.parentCategoryId != null }

        if (parents.isEmpty()) return "Maaf Mam, belum ada kategori terdaftar di Database."

        val sb = java.lang.StringBuilder("🗂️ **Daftar Kategori Finansial Mam:**\n\n")
        
        val types = listOf(
            "INCOME" to "🟢 PEMASUKAN", 
            "EXPENSE" to "🔴 PENGELUARAN", 
            "DEBT" to "🟡 HUTANG", 
            "RECEIVABLE" to "🔵 PIUTANG"
        )
        
        for ((typeCode, typeLabel) in types) {
            val typeParents = parents.filter { it.type.uppercase(Locale.ROOT) == typeCode }
            if (typeParents.isNotEmpty()) {
                sb.append("=========================\n")
                sb.append("**$typeLabel**\n")
                sb.append("=========================\n")
                for (p in typeParents) {
                    sb.append("📁 **${p.name}**\n")
                    val kids = subs.filter { it.parentCategoryId == p.id }.sortedBy { it.name }
                    for (k in kids) {
                        sb.append("   └── 💰 ${k.name}\n")
                    }
                }
                sb.append("\n")
            }
        }
        return sb.toString().trimEnd()
    }

    private suspend fun executeDirectDebtRecord(name: String, amountValue: Double, isReceivable: Boolean, timestampValue: Long) {
        val selectedType = if (isReceivable) "RECEIVABLE" else "DEBT"
        val debtId = "debt_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val sanitizedName = name.ifEmpty { "TEMAN" }.uppercase(Locale.ROOT)

        val debt = Debt(
            id = debtId, contactName = sanitizedName, contactPhoneNumber = "0812", 
            amount = amountValue, remainingAmount = amountValue, type = selectedType, 
            note = "Dicatat Otomatis oleh AI", timestamp = timestampValue, isPaid = false
        )
        db.debtDao().insert(debt)

        val flowType = if (selectedType == "RECEIVABLE") "EXPENSE" else "INCOME"
        val catId = if (selectedType == "RECEIVABLE") 104L else 101L
        val catName = if (selectedType == "RECEIVABLE") "Piutang" else "Hutang"
        val txId = "tx_${System.currentTimeMillis()}_${(1000..9999).random()}"
        
        val standardizedNote = if (selectedType == "RECEIVABLE") "[$catName] $sanitizedName - MEMBERIKAN PINJAMAN" else "[$catName] $sanitizedName - MENERIMA PINJAMAN"

        val tx = Transaction(
            id = txId, amount = amountValue, type = flowType, categoryId = catId, 
            categoryName = catName, note = standardizedNote, timestamp = timestampValue, debtId = debtId
        )
        db.transactionDao().insert(tx)
    }

    private suspend fun executeDirectDebtPayment(contactNameRaw: String, finalAmount: Double, originalAiResponse: String, targetTimestamp: Long): String {
        val allDebts = db.debtDao().getAllSync()
        var matchDocId: String? = null; var matchAmount = 0.0; var matchType = "DEBT"
        var matchContactName = contactNameRaw.ifEmpty { "TEMAN" }.uppercase(Locale.ROOT)

        val inputTokens = contactNameRaw.uppercase(Locale.ROOT).split(" ").filter { it.length > 2 }
        for (doc in allDebts) {
            if (!doc.isPaid) {
                val dbName = doc.contactName.uppercase(Locale.ROOT).trim()
                val remainingAmount = doc.remainingAmount
                var isTokenMatch = false
                for (token in inputTokens) { if (dbName.contains(token)) { isTokenMatch = true; break } }

                if (isTokenMatch || dbName.contains(contactNameRaw.uppercase(Locale.ROOT)) || contactNameRaw.uppercase(Locale.ROOT).contains(dbName)) {
                    matchDocId = doc.id; matchAmount = remainingAmount; matchType = doc.type
                    matchContactName = dbName; break
                }
            }
        }

        if (matchDocId != null) {
            val isPelunasan = originalAiResponse.uppercase(Locale.ROOT).contains("MELUNASI") || finalAmount >= matchAmount
            val targetPayAmount = if (isPelunasan) matchAmount else finalAmount
            val nextRemaining = (matchAmount - targetPayAmount).coerceAtLeast(0.0)

            val existingDebt = db.debtDao().getById(matchDocId)
            if (existingDebt != null) {
                db.debtDao().update(existingDebt.copy(remainingAmount = nextRemaining, isPaid = nextRemaining <= 0.0))
            }

            val txType = if (matchType == "DEBT") "EXPENSE" else "INCOME"
            val catId = if (matchType == "DEBT") 102L else 103L
            val catName = if (matchType == "DEBT") "Pembayaran kembali" else "Penagihan Utang"
            val txId = "tx_${System.currentTimeMillis()}_${(1000..9999).random()}"
            
            val standardizedNote = if (matchType == "DEBT") "[$catName] $matchContactName - MEMBAYAR CICILAN UTANG" else "[$catName] $matchContactName - MENERIMA CICILAN PIUTANG"

            val payTx = Transaction(
                id = txId, amount = targetPayAmount, type = txType, categoryId = catId, 
                categoryName = catName, note = standardizedNote, timestamp = targetTimestamp, debtId = matchDocId
            )
            db.transactionDao().insert(payTx)

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
            val newCat = Category(
                docId = "cat_$catId", id = catId, name = catName, type = type, 
                iconName = "ic_custom", parentCategoryId = parsedParentId, isLocked = false
            )
            db.categoryDao().insert(newCat)
        }

        val txId = "tx_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val finalNoteStr = cleanNote.ifEmpty { "TRANSAKSI $catName" }.uppercase(Locale.ROOT)
        
        val tx = Transaction(
            id = txId, amount = finalAmount, type = type, categoryId = catId, 
            categoryName = catName, note = finalNoteStr, timestamp = targetTimestamp, debtId = ""
        )
        db.transactionDao().insert(tx)

        if (type == "EXPENSE") {
            checkAndTriggerBudgetAlertFromAI(catId, catName)
        }
    }

    private suspend fun checkAndTriggerBudgetAlertFromAI(categoryId: Long, categoryName: String) {
        try {
            val budgetDoc = db.budgetDao().getByCategoryId(categoryId)
            if (budgetDoc != null) {
                val limitAmount = budgetDoc.limitAmount
                if (limitAmount > 0.0) {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                    val startOfMonth = cal.timeInMillis

                    val txs = db.transactionDao().getByCategoryId(categoryId)
                    var totalSpent = 0.0
                    for (tx in txs) {
                        if (tx.timestamp >= startOfMonth) {
                            totalSpent += tx.amount
                        }
                    }

                    if (totalSpent >= (limitAmount * 0.8)) {
                        com.smartfinance.tracker.worker.AiWorkerManager.triggerBudgetAlert(
                            context, categoryName, totalSpent, limitAmount
                        )
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private suspend fun compileAiReport(cleanJsonStr: String): String {
        return "Laporan" // Tidak ada perubahan, disingkat agar muat
    }

    private fun parseTransactionDateTime(dateStr: String, baseTime: Long = System.currentTimeMillis()): Long {
        if (dateStr.trim().isEmpty()) return baseTime
        return try {
            val hasTime = dateStr.contains(":")
            val formatStr = if (hasTime) "dd-MM-yyyy HH:mm" else "dd-MM-yyyy"
            val parsedDate = SimpleDateFormat(formatStr, Locale("id", "ID")).parse(dateStr.trim())
            
            if (parsedDate != null) {
                val cal = Calendar.getInstance().apply { time = parsedDate }
                val nowCal = Calendar.getInstance().apply { timeInMillis = baseTime }
                
                if (hasTime) {
                    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                } else {
                    cal.set(Calendar.HOUR_OF_DAY, nowCal.get(Calendar.HOUR_OF_DAY))
                    cal.set(Calendar.MINUTE, nowCal.get(Calendar.MINUTE))
                    cal.set(Calendar.SECOND, nowCal.get(Calendar.SECOND))
                    cal.set(Calendar.MILLISECOND, nowCal.get(Calendar.MILLISECOND))
                }
                cal.timeInMillis
            } else baseTime
        } catch (e: Exception) { baseTime }
    }

    private fun parseAmount(item: JSONObject): Double {
        return try {
            var rawValue: Any? = null
            val possibleKeys = listOf("amount", "nominal", "harga", "total", "value")
            for (key in possibleKeys) {
                if (item.has(key)) { rawValue = item.get(key); break }
            }
            if (rawValue == null) return 0.0
            if (rawValue is Number) return rawValue.toDouble()
            val stringValue = rawValue.toString()
            val cleanDigitsOnly = stringValue.replace(Regex("[^0-9]"), "")
            cleanDigitsOnly.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) { 0.0 }
    }
    
    private fun dynamicContactNameExtractor(userMessage: String): String {
        val msgUpper = userMessage.uppercase(Locale.ROOT)
        
        val patterns = listOf(
            Regex("(?:BERSAMA|SAMA|DENGAN|BARENG|BESERTA|WITH|ALONGSIDE|BY)\\s+([A-Z]+)"),
            Regex("(?:KE|KEPADA|UNTUK|BUAT|KASIH|NGASIH|BAYARIN|TO|FOR)\\s+([A-Z]+)"),
            Regex("(?:DARI|DAPET DARI|FROM)\\s+([A-Z]+)"),
            Regex("([A-Z]+)\\s+(?:PINJAM|MINJEM|NGUTANG|HUTANG|BORROW|OWES|OWE)"),
            Regex("(?:NALANGIN|DITALANGIN|DIBAYARIN)\\s+([A-Z]+)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(msgUpper)
            if (match != null && match.groupValues.size > 1) {
                val extractedName = match.groupValues[1].trim()
                
                val ignoredWords = listOf(
                    "UANG", "DUIT", "SEBESAR", "TOTAL", "HARGA", "BIAYA", "CASH", "TUNAI",
                    "KASIR", "TOKO", "WARUNG", "BANK", "ATM", "PASAR", "MALL", "HOTEL", "KAMAR", "KOS", "RUMAH",
                    "DI", "KE", "DARI", "SAMA", "BERSAMA", "DENGAN", "BARENG", "BUAT", "UNTUK", 
                    "AT", "TO", "FROM", "WITH", "BY", "FOR",
                    "ORANG", "TEMAN", "MONEY", "SOMEONE", "FRIEND", "THE", "A", "AN",
                    "MY", "YOUR", "HIS", "HER",
                    "RP", "IDR", "USD", "RUPIAH", "DOLLAR"
                )
                
                val isNotNumber = extractedName.toDoubleOrNull() == null
                
                if (!ignoredWords.contains(extractedName) && isNotNumber) {
                    return extractedName
                }
            }
        }
        return "TEMAN"
    }
}
